package com.phisherman.kavach.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One logged verdict (PS4 Section 7.6). By default stores NO raw message bodies or
 * audio — only time, channel, level, score, a subject (host/sender/"call"), reason ids
 * and the user's action (PS4 Section 14.2, Minimal data).
 */
@Entity(tableName = "decisions")
data class Decision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,                 // epoch millis
    val channel: String,          // LINK | SMS | VOICE
    val level: String,            // SAFE | SUSPICIOUS | DANGEROUS
    val score: Int,
    val subject: String,          // host for links, sender for SMS, "call" for voice
    val reasons: String,          // comma-joined reason ids
    val userAction: String? = null, // BACK | OPEN_ANYWAY | REPORTED | null
)
