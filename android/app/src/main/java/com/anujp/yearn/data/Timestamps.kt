package com.anujp.yearn.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The one timestamp format on the phone: ISO-8601 with a numeric offset, always +HH:MM, never Z. */
object Timestamps {
    val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    fun format(z: ZonedDateTime): String = FORMAT.format(z)
}
