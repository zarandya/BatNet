package org.batnet.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

import android.arch.persistence.room.OnConflictStrategy.IGNORE

@Dao
interface UsersDao {
    @Query("SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERS_ID = :id")
    operator fun get(id: String): User?

    @Insert(onConflict = IGNORE)
    operator fun plusAssign(user: User)

    @get:Query("SELECT * FROM $TABLE_USER_ID")
    @set:Insert
    var currentUserId: UserIdHolder?

}

val UsersDao.currentUser get() = this[currentUserId!!.uid]!!