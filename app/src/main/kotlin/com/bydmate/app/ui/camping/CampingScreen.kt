package com.bydmate.app.ui.camping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.camping.CampingAir
import com.bydmate.app.camping.CampingAirflow
import com.bydmate.app.camping.CampingRefusal
import com.bydmate.app.camping.CampingSettings
import com.bydmate.app.camping.CampingState
import com.bydmate.app.camping.CampingStopReason
import com.bydmate.app.ui.settings.SettingChipRow
import com.bydmate.app.ui.settings.SettingDivider
import com.bydmate.app.ui.settings.SettingHint
import com.bydmate.app.ui.settings.SettingSliderRow
import com.bydmate.app.ui.settings.SettingSubhead
import com.bydmate.app.ui.settings.SettingToggleRow
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.NavyDeep
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextSecondary
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Camping mode setup: every part is a switch, chosen before «Start camping». While camping runs
 * the switches are locked and the big button becomes Stop.
 */
@Composable
fun CampingScreen(
    onBack: () -> Unit,
    viewModel: CampingViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editable = !state.active && !state.busy
    val edit = viewModel::edit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Header(onBack)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section {
                    SettingSubhead(stringResource(R.string.camping_section_screens))
                    SettingToggleRow(
                        title = stringResource(R.string.camping_screen_off),
                        description = stringResource(R.string.camping_screen_off_desc),
                        checked = s.screenOff, enabled = editable,
                        onCheckedChange = { v -> edit { copy(screenOff = v) } }, traceId = "camping_screen_off",
                    )
                    SettingDivider()
                    SettingToggleRow(
                        title = stringResource(R.string.camping_cluster_off),
                        description = stringResource(R.string.camping_cluster_off_desc),
                        checked = s.clusterOff, enabled = editable,
                        onCheckedChange = { v -> edit { copy(clusterOff = v) } }, traceId = "camping_cluster_off",
                    )
                    SettingDivider()
                    SettingToggleRow(
                        title = stringResource(R.string.camping_interior_lights),
                        description = stringResource(R.string.camping_interior_lights_desc),
                        checked = s.interiorLightsOff, enabled = editable,
                        onCheckedChange = { v -> edit { copy(interiorLightsOff = v) } }, traceId = "camping_interior",
                    )
                    SettingDivider()
                    SettingToggleRow(
                        title = stringResource(R.string.camping_exterior_lights),
                        description = stringResource(R.string.camping_exterior_lights_desc),
                        checked = s.exteriorLightsOff, enabled = editable,
                        onCheckedChange = { v -> edit { copy(exteriorLightsOff = v) } }, traceId = "camping_exterior",
                    )
                }
                Section {
                    SettingSubhead(stringResource(R.string.camping_section_doors))
                    SettingToggleRow(
                        title = stringResource(R.string.camping_lock),
                        checked = s.lockDoors, enabled = editable,
                        onCheckedChange = { v -> edit { copy(lockDoors = v) } }, traceId = "camping_lock",
                    )
                    SettingDivider()
                    SettingToggleRow(
                        title = stringResource(R.string.camping_unlock_on_stop),
                        checked = s.unlockOnStop, enabled = editable && s.lockDoors,
                        onCheckedChange = { v -> edit { copy(unlockOnStop = v) } }, traceId = "camping_unlock_stop",
                    )
                }
                Section {
                    SettingSubhead(stringResource(R.string.camping_section_safety))
                    SettingSliderRow(
                        title = stringResource(R.string.camping_min_soc),
                        description = stringResource(R.string.camping_min_soc_desc),
                        value = s.minSoc.toFloat(),
                        onValueChange = { v -> edit { copy(minSoc = (v / 5f).roundToInt() * 5) } },
                        valueRange = 0f..CampingSettings.MIN_SOC_MAX.toFloat(),
                        steps = CampingSettings.MIN_SOC_MAX / 5 - 1,
                        valueLabel = if (s.minSoc == 0) stringResource(R.string.camping_min_soc_off) else "${s.minSoc}%",
                        enabled = editable,
                    )
                    SettingHint(stringResource(R.string.camping_hint))
                }
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClimateSection(s, editable, edit)
                LogSection(state)
            }
        }
        Footer(s, state, viewModel)
    }
}

@Composable
private fun ClimateSection(
    s: CampingSettings,
    editable: Boolean,
    edit: (CampingSettings.() -> CampingSettings) -> Unit,
) {
    val climateEditable = editable && s.climate
    Section {
        SettingSubhead(stringResource(R.string.camping_section_climate))
        SettingToggleRow(
            title = stringResource(R.string.camping_climate),
            description = stringResource(R.string.camping_climate_desc),
            checked = s.climate, enabled = editable,
            onCheckedChange = { v -> edit { copy(climate = v) } }, traceId = "camping_climate",
        )
        SettingDivider()
        SettingSliderRow(
            title = stringResource(R.string.camping_temperature),
            value = s.temperature.toFloat(),
            onValueChange = { v -> edit { copy(temperature = v.roundToInt()) } },
            valueRange = CampingSettings.TEMP_MIN.toFloat()..CampingSettings.TEMP_MAX.toFloat(),
            steps = CampingSettings.TEMP_MAX - CampingSettings.TEMP_MIN - 1,
            valueLabel = "${s.temperature}°",
            enabled = climateEditable,
        )
        SettingDivider()
        SettingChipRow(
            title = stringResource(R.string.camping_climate_mode),
            options = listOf(stringResource(R.string.camping_mode_auto), stringResource(R.string.camping_mode_manual)),
            selectedIndex = if (s.climateAuto) 0 else 1,
            onSelect = { i -> edit { copy(climateAuto = i == 0) } },
            enabled = climateEditable,
        )
        SettingSliderRow(
            title = stringResource(R.string.camping_fan),
            value = s.fanLevel.toFloat(),
            onValueChange = { v -> edit { copy(fanLevel = v.roundToInt()) } },
            valueRange = CampingSettings.FAN_MIN.toFloat()..CampingSettings.FAN_MAX.toFloat(),
            steps = CampingSettings.FAN_MAX - CampingSettings.FAN_MIN - 1,
            valueLabel = s.fanLevel.toString(),
            enabled = climateEditable && !s.climateAuto,
        )
        SettingDivider()
        SettingChipRow(
            title = stringResource(R.string.camping_air),
            options = listOf(
                stringResource(R.string.camping_keep),
                stringResource(R.string.camping_air_outside),
                stringResource(R.string.camping_air_inside),
            ),
            selectedIndex = s.air.ordinal,
            onSelect = { i -> edit { copy(air = CampingAir.entries[i]) } },
            enabled = climateEditable,
        )
        SettingChipRow(
            title = stringResource(R.string.camping_airflow),
            options = listOf(
                stringResource(R.string.camping_keep),
                stringResource(R.string.camping_airflow_face),
                stringResource(R.string.camping_airflow_face_feet),
                stringResource(R.string.camping_airflow_feet),
            ),
            selectedIndex = s.airflow.ordinal,
            onSelect = { i -> edit { copy(airflow = CampingAirflow.entries[i]) } },
            enabled = climateEditable,
        )
        SettingDivider()
        SettingToggleRow(
            title = stringResource(R.string.camping_climate_off_on_stop),
            checked = s.climateOffOnStop, enabled = climateEditable,
            onCheckedChange = { v -> edit { copy(climateOffOnStop = v) } }, traceId = "camping_ac_off_stop",
        )
    }
}

@Composable
private fun LogSection(state: CampingState) {
    if (state.log.isEmpty()) return
    Section {
        SettingSubhead(stringResource(R.string.camping_log_title))
        state.log.forEach { line ->
            Text(
                text = (if (line.ok) "✓ " else "✗ ") + line.command + (line.detail?.let { "  ($it)" } ?: ""),
                color = if (line.ok) TextSecondary else AccentOrange,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Footer(s: CampingSettings, state: CampingState, viewModel: CampingViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        val message = when {
            state.refusal == CampingRefusal.MOVING -> stringResource(R.string.camping_refusal_moving)
            state.refusal == CampingRefusal.NO_OVERLAY_PERMISSION -> stringResource(R.string.camping_refusal_overlay)
            state.stopReason == CampingStopReason.LOW_BATTERY -> stringResource(R.string.camping_stopped_low_battery)
            state.stopReason == CampingStopReason.MOVING -> stringResource(R.string.camping_stopped_moving)
            state.active && state.startedAt != null -> stringResource(
                R.string.camping_active, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.startedAt)),
            )
            s.isEmpty -> stringResource(R.string.camping_nothing_selected)
            else -> null
        }
        if (message != null) {
            Text(
                message,
                color = if (state.active) AccentGreen else AccentOrange,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp).clickable { viewModel.dismissMessage() },
            )
        }
        Button(
            onClick = { if (state.active) viewModel.stop() else viewModel.start() },
            enabled = !state.busy && (state.active || !s.isEmpty),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.active) SocRed else AccentGreen,
                contentColor = if (state.active) Color.White else NavyDark,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                stringResource(if (state.active) R.string.camping_stop else R.string.camping_start),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(1.5.dp, TextMuted, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = TextSecondary, fontSize = 16.sp)
        }
        Text(
            stringResource(R.string.camping_title),
            color = AccentGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
