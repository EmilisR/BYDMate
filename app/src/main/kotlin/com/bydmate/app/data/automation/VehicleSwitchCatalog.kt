package com.bydmate.app.data.automation

import org.json.JSONObject

/**
 * ADAS / driver-assist / CPD switches the `vehicle_switch` automation action can write.
 *
 * Source: the BYDMate FID dump of a BYD Seal U (DiLink, build QKQ1.210910.001, 2026-09-29).
 * Every fid below is the `*_SET` write fid of that dump; [statusFid] is the matching status
 * fid when the dump has one, read before and after the write so the journal shows what the
 * car answered (`status 1→2`). None of these are live-validated yet: the on/off values follow
 * BYD's common switch encoding (1 = on, 2 = off), and every entry lets the rule override the
 * raw value in case a trim uses another encoding (0/1 is the usual alternative).
 *
 * Deliberately NOT here: calibration and reset fids (BSD/LDW static calibration, drive/safety/
 * LKS reset), which trigger one-shot procedures on the ECU rather than a setting.
 *
 * The catalog is the allowlist of this action: the dispatcher refuses any id not listed, and
 * only the ADAS (1038) and Setting (1023) devices are ever written.
 */
object VehicleSwitchCatalog {

    const val KIND = "vehicle_switch"

    const val DEV_ADAS = 1038
    const val DEV_SETTING = 1023

    /** BYD's usual switch encoding for `*_SET` fids. */
    const val DEFAULT_ON = 1
    const val DEFAULT_OFF = 2

    enum class Type { SWITCH, LEVEL }

    data class Entry(
        val id: String,
        val label: String,
        val dev: Int,
        val writeFid: Int,
        val statusFid: Int?,
        val type: Type = Type.SWITCH,
        /** Disabling it (or setting any level) runs only in P and always asks first. */
        val safetyCritical: Boolean = false,
        val onValue: Int = DEFAULT_ON,
        val offValue: Int = DEFAULT_OFF,
        /** LEVEL entries: the raw values the rule editor offers. */
        val levels: List<Int> = emptyList(),
        /** Section of the rule editor's dropdown; set by [group]. */
        val group: String = "",
    )

    private fun adas(id: String, label: String, fid: Int, status: Int?, critical: Boolean = false) =
        Entry(id, label, DEV_ADAS, fid, status, safetyCritical = critical)

    private fun adasLevel(id: String, label: String, fid: Int, status: Int?, levels: List<Int>, critical: Boolean = false) =
        Entry(id, label, DEV_ADAS, fid, status, Type.LEVEL, critical, levels = levels)

    private fun setting(id: String, label: String, fid: Int, status: Int?) =
        Entry(id, label, DEV_SETTING, fid, status)

    val ENTRIES: List<Entry> = buildList {
        group("Child presence detection",
            setting("cpd", "Child presence detection (CPD)", 1324617778, 376438818),
        )
        group("Driver monitoring",
            setting("driver_fatigue_monitor", "Driver fatigue monitor", 784334899, 784334859),
            setting("driver_presence_monitor", "Driver presence monitor", 784334885, null),
            setting("fatigue_relief", "Fatigue relief", 784334892, 545259570),
            setting("speed_limit_change", "Speed limit change prompt", 1324572734, 725614643),
            adas("adas_fatigue_monitor", "ADAS fatigue monitor", 503316504, null),
            adas("ims", "IMS (in-cabin monitoring)", 850530371, 535863322),
        )
        group("Warnings and sounds",
            adas("assist_prompt_sound", "Driving assist prompt sound", 850571333, 922750994),
            adas("slw", "Speed limit warning (SLW)", 850452531, 535834664),
            adas("sla", "Speed limit assist (SLA)", 944767010, 828375077),
            adas("isla", "Intelligent speed limit assist (ISLA)", 944767044, 725614616),
            adas("islc", "Intelligent speed limit control (ISLC)", 1324560408, 854589481),
            adas("slr", "Speed limit recognition (SLR)", 944767040, 725614620),
            adas("front_car_start", "Front car start reminder", 850452543, 535842834),
            adas("tla", "Traffic light assist (TLA)", 1324560410, 725614632),
        )
        group("Lane",
            adas("tja_ica", "Traffic jam / cruise assist (TJA/ICA)", 944766996, 828375084),
            adas("ilca", "Intelligent lane change (ILCA)", 1324560404, 700448776),
            adas("elka", "Emergency lane keep (ELKA)", 944767046, 854589482, critical = true),
            adasLevel("lks_mode", "Lane keep mode (LKS)", 944767016, 828375054, listOf(0, 1, 2, 3)),
            adasLevel("lks_sensitivity", "Lane keep sensitivity", 944767012, 828375056, listOf(1, 2, 3)),
            adasLevel("ldw_type", "Lane departure warning type", 944767022, 828375096, listOf(1, 2, 3)),
            adas("avoid_slow_lane", "Avoid slow lane overtaking", 850571319, 594542632),
            adas("cornering_speed", "Cornering speed reduction assist", 850571317, 594542630),
        )
        group("Collision",
            adas("aeb", "Automatic emergency braking (AEB)", 944767020, 852492332, critical = true),
            adasLevel("fcw_level", "Forward collision warning level", 1324560420, 852492333, listOf(0, 1, 2, 3), critical = true),
            adas("pcw", "Pre-collision warning (PCW)", 944767014, 852492314, critical = true),
            adas("fcta", "Front cross traffic alert (FCTA)", 1324560400, 748683276),
            adas("fctb", "Front cross traffic brake (FCTB)", 1324560402, 748683278),
            adas("rcta", "Rear cross traffic alert (RCTA)", 944766990, null),
            adas("rcw", "Rear collision warning (RCW)", 944766992, 1098907676),
            adas("bsd", "Blind spot detection (BSD)", 944767024, 1098907656),
            adas("bsis", "Blind spot info system (BSIS)", 1324560440, 580911126),
            adas("mois", "Moving object info system (MOIS)", 1324560442, 568328213),
            adas("dow", "Door open warning (DOW)", 944766994, 1098907678),
        )
        group("Chassis / braking",
            adas("esp", "Electronic stability (ESP)", 944766984, 305135676, critical = true),
            adas("hdc", "Hill descent control (HDC)", 944766998, 305135665, critical = true),
            adas("avh", "Auto hold (AVH)", 944766986, 304087110),
            adas("cst", "Comfort stop (CST)", 944767000, 223346712),
            adas("ectb", "ECTB", 944767006, null),
            adasLevel("brake_feel", "Brake pedal feel (iBooster)", 944766988, 305135629, listOf(1, 2, 3), critical = true),
        )
        group("Lights / other",
            adas("hma", "Auto high beam (HMA)", 944767008, 828375048),
            adas("mvac", "MVAC", 1324560414, 700448791),
            adas("quick_speed_setting", "Quick speed setting", 850571325, 516947994),
            adas("wheel_speed_adjust", "Steering wheel speed adjustment", 850571331, 535851066),
            adas("e2e", "End-to-end driving (E2E)", 1324576798, 481296416),
            adas("hnp", "Highway navigation pilot (HNP)", 1324560406, 700448824),
            adas("unp", "Urban navigation pilot (UNP)", 1324560412, 700448826),
            adas("apa", "Auto parking assist (APA)", 944767002, 487587878),
            adas("ivi_state", "ADAS IVI state", 503316547, null),
        )
    }

    private fun MutableList<Entry>.group(name: String, vararg entries: Entry) {
        entries.forEach { add(it.copy(group = name)) }
    }

    private val BY_ID: Map<String, Entry> = ENTRIES.associateBy { it.id }

    fun find(id: String?): Entry? = id?.let { BY_ID[it] }

    /** What a `vehicle_switch` action asks for. [raw] overrides the on/off/level value when set. */
    data class Request(val id: String, val on: Boolean, val raw: Int?)

    fun parse(payload: String?): Request? = try {
        val json = JSONObject(payload ?: return null)
        val id = json.optString("id")
        if (id.isBlank()) null
        else Request(
            id = id,
            on = json.optBoolean("on", true),
            raw = if (json.has("raw") && !json.isNull("raw")) json.optInt("raw") else null,
        )
    } catch (_: Exception) {
        null
    }

    fun payload(id: String, on: Boolean, raw: Int? = null): String =
        JSONObject().put("id", id).put("on", on).apply { if (raw != null) put("raw", raw) }.toString()

    /** The raw value to write for [request] on [entry]. LEVEL entries always carry [Request.raw]. */
    fun valueFor(entry: Entry, request: Request): Int? = when {
        request.raw != null -> request.raw
        entry.type == Type.LEVEL -> null
        request.on -> entry.onValue
        else -> entry.offValue
    }

    /**
     * True when [request] needs the car in P and an on-screen confirmation: turning a
     * safety-critical switch off, or setting a safety-critical level at all.
     */
    fun needsParkAndConfirm(request: Request): Boolean {
        val entry = find(request.id) ?: return false
        if (!entry.safetyCritical) return false
        return entry.type == Type.LEVEL || !request.on || request.raw != null
    }

    fun displayName(request: Request): String {
        val entry = find(request.id) ?: return request.id
        val state = when {
            entry.type == Type.LEVEL -> "level ${request.raw ?: "?"}"
            request.raw != null -> "raw ${request.raw}"
            request.on -> "on"
            else -> "off"
        }
        return "${entry.label}: $state"
    }
}
