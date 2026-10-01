package com.bydmate.app.camping

/**
 * Turns [CampingSettings] into the car commands camping mode sends. The strings are the app's
 * own command vocabulary ([com.bydmate.app.data.vehicle.CommandTranslator]), all of them on
 * live-validated or already-shipped write channels — camping mode adds no new car writes.
 *
 * Pure: no Android, unit-tested.
 */
object CampingCommands {

    const val AC_ON = "自动空调"
    const val AC_OFF = "关闭空调"
    const val AC_AUTO = "空调自动"
    const val AC_MANUAL = "空调手动"
    const val AIR_OUTSIDE = "外循环"
    const val AIR_INSIDE = "内循环"
    const val INTERIOR_LIGHT_OFF = "关闭车内灯"
    const val AMBIENT_LIGHT_OFF = "氛围灯关闭"
    const val DRL_OFF = "关闭日行灯"
    const val LOCK = "车门上锁"
    const val UNLOCK = "车门解锁"

    fun temperature(celsius: Int): String =
        "设置温度${celsius.coerceIn(CampingSettings.TEMP_MIN, CampingSettings.TEMP_MAX)}"

    fun fan(level: Int): String = "风量${level.coerceIn(CampingSettings.FAN_MIN, CampingSettings.FAN_MAX)}"

    private fun airflow(a: CampingAirflow): String? = when (a) {
        CampingAirflow.KEEP -> null
        CampingAirflow.FACE -> "吹面"
        CampingAirflow.FACE_FEET -> "吹面吹脚"
        CampingAirflow.FEET -> "吹脚"
    }

    /**
     * Climate on with the chosen setup. Power first: the car ignores mode / setpoint writes
     * while the climate is off. A fan level only in manual — on BYD a fan write drops AUTO.
     */
    fun climate(s: CampingSettings): List<String> {
        if (!s.climate) return emptyList()
        return buildList {
            add(AC_ON)
            add(if (s.climateAuto) AC_AUTO else AC_MANUAL)
            add(temperature(s.temperature))
            if (!s.climateAuto) add(fan(s.fanLevel))
            when (s.air) {
                CampingAir.KEEP -> Unit
                CampingAir.OUTSIDE -> add(AIR_OUTSIDE)
                CampingAir.INSIDE -> add(AIR_INSIDE)
            }
            airflow(s.airflow)?.let(::add)
        }
    }

    /** Everything sent on start: climate, then lights, the lock last (a failed lock is the loudest). */
    fun start(s: CampingSettings): List<String> = buildList {
        addAll(climate(s))
        if (s.interiorLightsOff) {
            add(INTERIOR_LIGHT_OFF)
            add(AMBIENT_LIGHT_OFF)
        }
        if (s.exteriorLightsOff) add(DRL_OFF)
        if (s.lockDoors) add(LOCK)
    }

    /**
     * Sent on stop. [lowBattery]: the stop came from the state-of-charge guard, so the climate
     * goes off whatever the setting says — that is the guard's whole point.
     */
    fun stop(s: CampingSettings, lowBattery: Boolean): List<String> = buildList {
        if (s.climate && (s.climateOffOnStop || lowBattery)) add(AC_OFF)
        if (s.lockDoors && s.unlockOnStop) add(UNLOCK)
    }

    /** True when the state-of-charge guard must end camping. Unknown SoC never stops it. */
    fun lowBattery(s: CampingSettings, soc: Float?): Boolean =
        s.minSoc > 0 && soc != null && soc > 0f && soc < s.minSoc
}
