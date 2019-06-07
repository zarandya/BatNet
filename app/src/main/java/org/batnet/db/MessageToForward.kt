package org.batnet.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.util.*

const val TABLE_MESSAGES_TO_FORWARD = "messages_to_forward"

/**
 * Used to forward messages not for me. kept in a separate table because the original has fk constraints
 */
@Entity(tableName = TABLE_MESSAGES_TO_FORWARD)
data class MessageToForward(
        @PrimaryKey
        @ColumnInfo(name = COLUMN_MESSAGES_ID)
        val ID: String,

        @ColumnInfo(name = COLUMN_MESSAGES_TEXT)
        val content: String,

        @ColumnInfo(name = COLUMN_MESSAGES_USER)
        val author: String,

        @ColumnInfo(name = COLUMN_MESSAGES_DATE_CREATED)
        val dateCreated: Date,

        @ColumnInfo(name = COLUMN_MESSAGES_DIALOG)
        val dialog: String
)