package org.batnet.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

import android.arch.persistence.room.OnConflictStrategy.IGNORE

@Dao
interface DialogsDao {

    @get:Query("SELECT * FROM $TABLE_DIALOGS")
    val all: MutableList<Dialog>

    @Query("SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERS_ID IN " +
            "(SELECT $COLUMN_MEMBERSHIP_USER_ID FROM $TABLE_DIALOG_MEMBERS " +
                "WHERE $COLUMN_MEMBERSHIP_USER_ID = :dialogId" +
            ")"
    )
    fun getUsers(dialogId: String): List<User>

    @Query("SELECT * FROM $TABLE_DIALOGS WHERE $COLUMN_DIALOGS_ID = :id")
    operator fun get(id: String): Dialog?

    @Insert(onConflict = IGNORE)
    operator fun plusAssign(dialog: Dialog)

    @Insert(onConflict = IGNORE)
    fun insertMemberships(membershipEntities: List<DialogMembershipEntity>)
}
