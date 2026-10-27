package com.anujp.yearn.sync

interface SyncScheduler {
    /** Ask for an upload pass as soon as the network allows. */
    fun enqueueNow()

    /** Make sure the periodic retry exists. */
    fun ensureDaily()
}
