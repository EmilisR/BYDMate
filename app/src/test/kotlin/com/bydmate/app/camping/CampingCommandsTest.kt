package com.bydmate.app.camping

import com.bydmate.app.data.vehicle.CommandTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingCommandsTest {

    private val everything = CampingSettings(
        screenOff = true, clusterOff = true, interiorLightsOff = true, exteriorLightsOff = true,
        lockDoors = true, unlockOnStop = true, climate = true, temperature = 22, climateAuto = false,
        fanLevel = 3, air = CampingAir.INSIDE, airflow = CampingAirflow.FEET, climateOffOnStop = true,
    )

    @Test fun `every command camping can send resolves to a car write`() {
        val all = CampingCommands.start(everything) + CampingCommands.stop(everything, lowBattery = true) +
            CampingCommands.start(CampingSettings(air = CampingAir.OUTSIDE, airflow = CampingAirflow.FACE_FEET)) +
            CampingCommands.start(CampingSettings(airflow = CampingAirflow.FACE))
        for (cmd in all) {
            assertTrue("no car write for $cmd", CommandTranslator.resolve(cmd).isNotEmpty())
        }
    }

    @Test fun `climate powers on first and sends fan only in manual`() {
        val manual = CampingCommands.climate(everything)
        assertEquals(CampingCommands.AC_ON, manual.first())
        assertTrue(manual.contains("风量3"))
        assertTrue(manual.contains(CampingCommands.AIR_INSIDE))
        assertTrue(manual.contains("吹脚"))

        val auto = CampingCommands.climate(CampingSettings(climateAuto = true, air = CampingAir.KEEP))
        assertTrue(auto.contains(CampingCommands.AC_AUTO))
        assertFalse(auto.any { it.startsWith("风量") })
        assertFalse(auto.contains(CampingCommands.AIR_INSIDE) || auto.contains(CampingCommands.AIR_OUTSIDE))
    }

    @Test fun `switched-off parts are never written`() {
        val none = CampingSettings(
            screenOff = true, interiorLightsOff = false, exteriorLightsOff = false,
            lockDoors = false, climate = false,
        )
        assertTrue(CampingCommands.start(none).isEmpty())
        assertTrue(CampingCommands.stop(none, lowBattery = true).isEmpty())
    }

    @Test fun `lock is the last start command`() {
        assertEquals(CampingCommands.LOCK, CampingCommands.start(everything).last())
    }

    @Test fun `stop leaves climate on unless asked or battery low`() {
        val keep = everything.copy(climateOffOnStop = false, unlockOnStop = false)
        assertTrue(CampingCommands.stop(keep, lowBattery = false).isEmpty())
        assertEquals(listOf(CampingCommands.AC_OFF), CampingCommands.stop(keep, lowBattery = true))
    }

    @Test fun `temperature and fan are clamped to the car range`() {
        assertEquals("设置温度16", CampingCommands.temperature(5))
        assertEquals("设置温度30", CampingCommands.temperature(40))
        assertEquals("风量7", CampingCommands.fan(9))
    }

    @Test fun `low battery guard`() {
        val s = CampingSettings(minSoc = 20)
        assertTrue(CampingCommands.lowBattery(s, 19.5f))
        assertFalse(CampingCommands.lowBattery(s, 20f))
        assertFalse(CampingCommands.lowBattery(s, null))
        assertFalse(CampingCommands.lowBattery(s, 0f))
        assertFalse(CampingCommands.lowBattery(s.copy(minSoc = 0), 5f))
    }

    @Test fun `nothing selected is empty`() {
        assertTrue(
            CampingSettings(
                screenOff = false, clusterOff = false, interiorLightsOff = false,
                exteriorLightsOff = false, lockDoors = false, climate = false,
            ).isEmpty,
        )
        assertFalse(CampingSettings().isEmpty)
    }
}
