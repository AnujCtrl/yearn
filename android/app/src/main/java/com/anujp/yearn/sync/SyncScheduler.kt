package com.anujp.yearn.sync

interface SyncScheduler {
    /** Ask for an upload pass as soon as the network allows. */
    fun enqueueNow()

    /** Make sure the periodic retry exists. */
    fun ensureDaily()
}

/** Placeholder until Task 8 wires WorkManager; it is deleted then. */
object NoopSyncScheduler : SyncScheduler {
    override fun enqueueNow() = Unit
    override fun ensureDaily() = Unit
}
