package org.batnet.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey

const val TABLE_DIALOG_MEMBERS = "dialog_members"
const val COLUMN_MEMBERSHIP_DIALOG_ID = "membership_dialog_id"
const val COLUMN_MEMBERSHIP_USER_ID = "membership_user_id"

@Entity(
        tableName = TABLE_DIALOG_MEMBERS,
        primaryKeys = [COLUMN_MEMBERSHIP_DIALOG_ID, COLUMN_MEMBERSHIP_USER_ID],
        foreignKeys = [
            ForeignKey(entity = Dialog::class, parentColumns = [COLUMN_DIALOGS_ID], childColumns = [COLUMN_MEMBERSHIP_DIALOG_ID]),
            ForeignKey(entity = User::class, parentColumns = [COLUMN_USERS_ID], childColumns = [COLUMN_MEMBERSHIP_USER_ID])
        ]
)
class DialogMembershipEntity(
        @ColumnInfo(name = COLUMN_MEMBERSHIP_DIALOG_ID)
        val dialogId: String,

        @ColumnInfo(name = COLUMN_MEMBERSHIP_USER_ID)
        val userId: String
)
