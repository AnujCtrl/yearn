package com.anujp.yearn.capture

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Everything the system tells us about who sent a share, each field may be unknown. */
data class SourceHints(
    val referrer: String?,
    val extraReferrer: String?,
    val extraReferrerName: String?,
    val launchedFromPackage: String?,
    val launchedFromUid: Int?,
    val callingPackage: String?,
) {
    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "referrer" to referrer.orNull(),
            "extra_referrer" to extraReferrer.orNull(),
            "extra_referrer_name" to extraReferrerName.orNull(),
            "launched_from_package" to launchedFromPackage.orNull(),
            "launched_from_uid" to (launchedFromUid?.let { JsonPrimitive(it) } ?: JsonNull),
            "calling_package" to callingPackage.orNull(),
        )
    )

    private fun String?.orNull() = if (this == null) JsonNull else JsonPrimitive(this)
}
