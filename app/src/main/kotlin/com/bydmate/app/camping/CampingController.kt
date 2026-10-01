package com.bydmate.app.camping

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.bydmate.app.R
import com.bydmate.app.cluster.ClusterMode
import com.bydmate.app.cluster.ClusterProjectionManager
import com.bydmate.app.data.vehicle.HelperBootstrap
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.data.vehicle.VehicleApi
import com.bydmate.app.diagnostics.Trace
import com.bydmate.app.diagnostics.TraceArea
import com.bydmate.app.util.appLocalizedContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One car command of a camping session and how it went, for the screen's log. */
data class CampingLogLine(val command: String, val ok: Boolean, val detail: String? = null)

data class CampingState(
    val active: Boolean = false,
    val busy: Boolean = false,
    val startedAt: Long? = null,
    val log: List<CampingLogLine> = emptyList(),
    /** Why the last session ended, when it was not the driver's own stop. */
    val stopReason: CampingStopReason? = null,
    /** Start refused before anything was sent. */
    val refusal: CampingRefusal? = null,
)

enum class CampingStopReason { LOW_BATTERY, MOVING }
enum class CampingRefusal { MOVING, NO_OVERLAY_PERMISSION }

/**
 * Tesla-style camp mode. Applies [CampingSettings] once on start, then every minute re-sends the
 * climate setup if the car switched the climate off, and stops on its own when the battery falls
 * under the chosen limit or the car starts moving.
 *
 * Process-wide singleton living in the app process, which [com.bydmate.app.service.TrackingService]
 * keeps in the foreground. A process death ends camping without the stop commands: the car stays
 * as it was (locked, climate on) and the overlays go with the process.
 */
@Singleton
class CampingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleApi: VehicleApi,
    private val helper: HelperClient,
    private val bootstrap: HelperBootstrap,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CampingState())
    val state: StateFlow<CampingState> = _state.asStateFlow()

    val settingsStore = CampingSettingsStore(context)

    private var settings: CampingSettings = CampingSettings()
    private var overlay: CampingOverlay? = null
    private var keepAlive: Job? = null
    private var clusterPowered = false

    fun start(s: CampingSettings) {
        scope.launch {
            mutex.withLock {
                if (_state.value.active) return@withLock
                _state.update { CampingState(busy = true) }
                settingsStore.save(s)
                settings = s

                val speed = vehicleApi.readSpeed()
                if (speed != null && speed > MAX_START_SPEED_KMH) {
                    refuse(CampingRefusal.MOVING)
                    return@withLock
                }
                // Always needed: even with the screen left on, the Stop button is an overlay.
                if (!ensureOverlayPermission()) {
                    refuse(CampingRefusal.NO_OVERLAY_PERMISSION)
                    return@withLock
                }
                Trace.event(TraceArea.USER, "camping_start", "screen" to s.screenOff, "cluster" to s.clusterOff,
                    "climate" to s.climate, "lock" to s.lockDoors)

                // Screens first: the driver sees at once that camping has begun.
                val o = CampingOverlay(context) { stop() }
                withContext(Dispatchers.Main) { o.showMain(s.screenOff) }
                overlay = o
                if (s.clusterOff) blackOutCluster(o)

                for (cmd in CampingCommands.start(s)) send(cmd)

                _state.update { it.copy(active = true, busy = false, startedAt = System.currentTimeMillis()) }
                keepAlive = scope.launch { keepAliveLoop() }
            }
        }
    }

    /** The driver's stop: overlay button, the camping screen. */
    fun stop() = stopWith(null)

    private fun stopWith(reason: CampingStopReason?) {
        scope.launch {
            mutex.withLock {
                if (!_state.value.active) return@withLock
                _state.update { it.copy(busy = true) }
                keepAlive?.cancel()
                keepAlive = null
                Trace.event(TraceArea.USER, "camping_stop", "reason" to (reason?.name ?: "driver"))
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) { overlay?.hideAll() }
                    overlay = null
                    restoreCluster()
                    for (cmd in CampingCommands.stop(settings, reason == CampingStopReason.LOW_BATTERY)) send(cmd)
                }
                _state.update { it.copy(active = false, busy = false, startedAt = null, stopReason = reason) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(stopReason = null, refusal = null) }

    private suspend fun keepAliveLoop() {
        while (scope.isActive) {
            delay(KEEP_ALIVE_MS)
            val speed = vehicleApi.readSpeed()
            if (speed != null && speed > MAX_START_SPEED_KMH) {
                stopWith(CampingStopReason.MOVING)
                return
            }
            val soc = vehicleApi.readSoc()
            if (CampingCommands.lowBattery(settings, soc)) {
                stopWith(CampingStopReason.LOW_BATTERY)
                return
            }
            // Climate: re-send the whole setup only when the car reports it off — writing an
            // unchanged setpoint every minute would beep / flash the climate popup for nothing.
            if (settings.climate && vehicleApi.readAcStatus() == 0) {
                Log.i(TAG, "climate found off, re-applying")
                for (cmd in CampingCommands.climate(settings)) send(cmd)
            }
            updateOverlayStatus(soc)
        }
    }

    private suspend fun updateOverlayStatus(soc: Float?) {
        val inside = vehicleApi.readInsideTemp()
        val outside = vehicleApi.readExteriorTemp()
        val lc = context.appLocalizedContext()
        val line = lc.getString(
            R.string.camping_overlay_status,
            inside?.toString() ?: "—",
            outside?.toString() ?: "—",
            soc?.let { "%.0f".format(it) } ?: "—",
        )
        withContext(Dispatchers.Main) { overlay?.updateStatus(line) }
    }

    private suspend fun send(cmd: String) {
        val r = vehicleApi.dispatch(cmd)
        val line = CampingLogLine(cmd, r.isSuccess, r.exceptionOrNull()?.message)
        if (r.isFailure) Log.w(TAG, "camping write failed: $cmd ${line.detail}")
        _state.update { it.copy(log = (it.log + line).takeLast(MAX_LOG)) }
        // Same spacing the composite writes use: back-to-back climate writes get dropped.
        delay(WRITE_STAGGER_MS)
    }

    private fun refuse(r: CampingRefusal) {
        _state.update { CampingState(refusal = r) }
    }

    private suspend fun ensureOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        if (bootstrap.ensureRunning()) runCatching { helper.grantOverlayPermission() }
        return Settings.canDrawOverlays(context)
    }

    /**
     * Cluster: our navigation projection off if it is up, then the cluster compositor on so the
     * full-cluster projection surface is shown, and a black window on that surface. Uses the same
     * write-ahead marker as [ClusterProjectionManager], so a crash mid-camping is healed by its
     * boot recovery. Experimental: cars without that surface keep their cluster as it is.
     */
    private suspend fun blackOutCluster(o: CampingOverlay) {
        if (ClusterProjectionManager.currentMode != ClusterMode.OFF) {
            ClusterProjectionManager.setMode(context, ClusterMode.OFF, helper, bootstrap, reason = "camping")
            delay(CLUSTER_SETTLE_MS)
        }
        val display = CampingOverlay.findClusterDisplay(context)
        if (display == null || !bootstrap.ensureRunning()) {
            _state.update { it.copy(log = it.log + CampingLogLine("cluster", false, "no cluster display")) }
            return
        }
        @Suppress("ApplySharedPref")
        val marked = withContext(Dispatchers.IO) {
            context.getSharedPreferences(ClusterProjectionManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(ClusterProjectionManager.KEY_COMPOSITOR_POWERED, true).commit()
        }
        val shown = withContext(Dispatchers.Main) { o.showCluster(display) }
        clusterPowered = marked && shown && runCatching { helper.setClusterContainerMode(true) }.getOrDefault(false)
        _state.update { it.copy(log = it.log + CampingLogLine("cluster", clusterPowered)) }
    }

    private suspend fun restoreCluster() {
        if (!clusterPowered) return
        clusterPowered = false
        // A projection the driver started during camping owns the compositor now.
        if (ClusterProjectionManager.currentMode != ClusterMode.OFF) return
        val off = runCatching { helper.setClusterContainerMode(false) }.getOrDefault(false)
        if (off) {
            context.getSharedPreferences(ClusterProjectionManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(ClusterProjectionManager.KEY_COMPOSITOR_POWERED, false).apply()
        }
    }

    companion object {
        private const val TAG = "Camping"
        private const val KEEP_ALIVE_MS = 60_000L
        private const val WRITE_STAGGER_MS = 400L
        private const val CLUSTER_SETTLE_MS = 1_500L
        private const val MAX_START_SPEED_KMH = 3f
        private const val MAX_LOG = 40
    }
}
