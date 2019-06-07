package org.batnet.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.stfalcon.chatkit.commons.models.IUser

const val TABLE_USERS = "users"
const val COLUMN_USERS_ID = "user_id"
const val COLUMN_USERS_NAME = "name"

@Entity(tableName = TABLE_USERS)
class User(
        @PrimaryKey
        @ColumnInfo(name = COLUMN_USERS_ID)
        val ID: String,

        @ColumnInfo(name = COLUMN_USERS_NAME)
        val username: String
): IUser {
    override fun getAvatar(): String = ""

    @Deprecated("Use with interface only. ", ReplaceWith("username"))
    override fun getName(): String = username

    @Deprecated("Use with interface only. ", ReplaceWith("ID"))
    override fun getId(): String = ID
}