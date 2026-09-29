package com.bydmate.app.data.automation

import com.bydmate.app.data.local.entity.TriggerDef

/**
 * Long-press variant of the two button triggers (`button_press` and `steering_key`).
 *
 * Stored in the trigger's `operator` field, which those kinds never read ("==" before), so a
 * rule keeps its kind, its value and its share format; an old build simply treats it as a short
 * press. A short and a long binding may sit on the same button and run different rules.
 */
object LongPress {
    const val OPERATOR = "long"
    const val SHORT_OPERATOR = "=="

    /** How long a button must be held before its long-press rules fire. */
    const val HOLD_MS = 800L

    fun of(trigger: TriggerDef): Boolean = trigger.operator == OPERATOR

    fun set(trigger: TriggerDef, long: Boolean): TriggerDef =
        trigger.copy(operator = if (long) OPERATOR else SHORT_OPERATOR)
}
