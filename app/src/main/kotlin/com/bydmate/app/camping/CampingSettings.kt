package com.bydmate.app.camping

import android.content.Context

/** Fresh air or recirculation while camping; KEEP leaves the car's current choice alone. */
enum class CampingAir { KEEP, OUTSIDE, INSIDE }

/** Blow direction while camping; KEEP leaves the car's current choice alone. Values = ac_wind_mode. */
enum class CampingAirflow { KEEP, FACE, FACE_FEET, FEET }

/**
 * What camping mode does, chosen before «Start camping». Every part is optional: a switched-off
 * part is never written to the car, on start or on stop.
 */
data class CampingSettings(
    val screenOff: Boolean = true,
    val clusterOff: Boolean = false,
    val interiorLightsOff: Boolean = true,
    val exteriorLightsOff: Boolean = true,
    val lockDoors: Boolean = true,
    val unlockOnStop: Boolean = false,
    val climate: Boolean = true,
    val temperature: Int = 21,
    /** True = the car's AUTO climate (fan picks itself), false = manual with [fanLevel]. */
    val climateAuto: Boolean = true,
    val fanLevel: Int = 2,
    val air: CampingAir = CampingAir.OUTSIDE,
    val airflow: CampingAirflow = CampingAirflow.KEEP,
    val climateOffOnStop: Boolean = false,
    /** Stop camping (and turn the climate off) below this state of charge, %. 0 = never. */
    val minSoc: Int = 20,
) {
    /** Nothing to do at all: the start button stays disabled. */
    val isEmpty: Boolean
        get() = !screenOff && !clusterOff && !interiorLightsOff && !exteriorLightsOff && !lockDoors && !climate

    companion object {
        const val TEMP_MIN = 16
        const val TEMP_MAX = 30
        const val FAN_MIN = 1
        const val FAN_MAX = 7
        const val MIN_SOC_MAX = 50
    }
}

/** Plain SharedPreferences, like the cluster projection settings: read once per start, tiny. */
class CampingSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CampingSettings {
        val d = CampingSettings()
        return CampingSettings(
            screenOff = prefs.getBoolean("screen_off", d.screenOff),
            clusterOff = prefs.getBoolean("cluster_off", d.clusterOff),
            interiorLightsOff = prefs.getBoolean("interior_lights_off", d.interiorLightsOff),
            exteriorLightsOff = prefs.getBoolean("exterior_lights_off", d.exteriorLightsOff),
            lockDoors = prefs.getBoolean("lock_doors", d.lockDoors),
            unlockOnStop = prefs.getBoolean("unlock_on_stop", d.unlockOnStop),
            climate = prefs.getBoolean("climate", d.climate),
            temperature = prefs.getInt("temperature", d.temperature)
                .coerceIn(CampingSettings.TEMP_MIN, CampingSettings.TEMP_MAX),
            climateAuto = prefs.getBoolean("climate_auto", d.climateAuto),
            fanLevel = prefs.getInt("fan_level", d.fanLevel)
                .coerceIn(CampingSettings.FAN_MIN, CampingSettings.FAN_MAX),
            air = enumOr(prefs.getString("air", null), d.air),
            airflow = enumOr(prefs.getString("airflow", null), d.airflow),
            climateOffOnStop = prefs.getBoolean("climate_off_on_stop", d.climateOffOnStop),
            minSoc = prefs.getInt("min_soc", d.minSoc).coerceIn(0, CampingSettings.MIN_SOC_MAX),
        )
    }

    fun save(s: CampingSettings) {
        prefs.edit()
            .putBoolean("screen_off", s.screenOff)
            .putBoolean("cluster_off", s.clusterOff)
            .putBoolean("interior_lights_off", s.interiorLightsOff)
            .putBoolean("exterior_lights_off", s.exteriorLightsOff)
            .putBoolean("lock_doors", s.lockDoors)
            .putBoolean("unlock_on_stop", s.unlockOnStop)
            .putBoolean("climate", s.climate)
            .putInt("temperature", s.temperature)
            .putBoolean("climate_auto", s.climateAuto)
            .putInt("fan_level", s.fanLevel)
            .putString("air", s.air.name)
            .putString("airflow", s.airflow.name)
            .putBoolean("climate_off_on_stop", s.climateOffOnStop)
            .putInt("min_soc", s.minSoc)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val PREFS_NAME = "camping_mode"
    }
}
