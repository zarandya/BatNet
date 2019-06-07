package org.batnet.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

const val TABLE_USER_ID = "user_id_table"

@Entity(
        tableName = TABLE_USER_ID,
        foreignKeys = [
                ForeignKey(entity = User::class, parentColumns = [COLUMN_USERS_ID], childColumns = [COLUMN_USERS_ID])
        ]
)
data class UserIdHolder(
        @PrimaryKey
        @ColumnInfo(name = COLUMN_USERS_ID)
        val uid: String
)