package com.kodraliu.localrock.shared.schedule

/** The robot's cron for a server job's cron: the job writes `?` for the unused field, the robot `*`. */
internal fun robotCronOf(jobCron: String): String? {
    val fields = jobCron.trim().split(Regex("\\s+"))
    if (fields.size != 5) return null
    return fields.joinToString(" ") { if (it == "?") "*" else it }
}

/** Why a missing native timer cannot be put back from [job], or null when it can. */
internal fun restoreBlockedReason(job: ServerSchedule): String? = when {
    job.nativeName.isNullOrBlank() -> "This schedule has no native timer reference, so there is nothing to restore."
    robotCronOf(job.cron) == null -> "This schedule's time could not be read, so it was not restored."
    // The robot numbers rooms differently from the job, so only whole-home entries can be rebuilt.
    job.settingsText("segments").let { it.isNotBlank() && it != "0" } ->
        "Room schedules can't be restored because the robot numbers rooms differently. Delete and recreate this schedule."
    else -> null
}
