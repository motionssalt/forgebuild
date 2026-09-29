package com.forgebuild.taskflow.ui

import com.forgebuild.taskflow.data.Task

/**
 * Utility to guarantee unique, collision-proof, and stable keys for Jetpack Compose LazyColumn/LazyRow lists.
 *
 * Prevents `java.lang.IllegalArgumentException: Key "<id>" was already used` crashes caused by:
 *  1. Hashcode collisions or identical content in messages/tasks.
 *  2. Recurring tasks having shared IDs across template and generated occurrences.
 *  3. Overnight tasks or multi-segment tasks sharing a base task ID.
 *  4. Duplicate item emissions during state updates, rollovers, or sweeps.
 *  5. Zero or negative unpersisted task IDs.
 */
object TaskKeyUtils {

    /**
     * Builds a list of guaranteed-unique, stable keys matching each task in [tasks].
     *
     * Guarantee: For any input list (even if all tasks have identical IDs, or are duplicates),
     * every returned key is strictly unique within the list and stable across recompositions.
     */
    fun buildUniqueKeys(tasks: List<Task>): List<String> {
        val seenCounts = mutableMapOf<String, Int>()
        return tasks.map { t ->
            val typeTag = if (t.isRecurringTemplate) "tpl" else "inst"
            val timeTag = t.fixedTime ?: t.scheduledDate ?: t.createdAt
            val baseKey = "task_${t.id}_${typeTag}_$timeTag"
            val count = seenCounts.getOrDefault(baseKey, 0)
            seenCounts[baseKey] = count + 1
            if (count == 0) baseKey else "${baseKey}_dup$count"
        }
    }

    /**
     * Builds a list of guaranteed-unique, stable keys matching each chat message in [messages].
     */
    fun buildUniqueMessageKeys(messageIds: List<String>): List<String> {
        val seenCounts = mutableMapOf<String, Int>()
        return messageIds.map { id ->
            val baseKey = "msg_$id"
            val count = seenCounts.getOrDefault(baseKey, 0)
            seenCounts[baseKey] = count + 1
            if (count == 0) baseKey else "${baseKey}_dup$count"
        }
    }
}
