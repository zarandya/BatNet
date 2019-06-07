package org.batnet.db

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.*
import android.arch.persistence.room.migration.Migration

@Database(entities = [Message::class, User::class, Dialog::class, DialogMembershipEntity::class, UserIdHolder::class, MessageToForward::class], version = 3)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getUsersDao(): UsersDao
    abstract fun getDialogsDao(): DialogsDao
    abstract fun getMessagesDao(): MessagesDao
}

inline val AppDatabase.users get() = getUsersDao()
inline val AppDatabase.dialogs get() = getDialogsDao()
inline val AppDatabase.messages get() = getMessagesDao()

fun migration(from: Int, to: Int, migration: (SupportSQLiteDatabase) -> Unit) = object : Migration(from, to) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration(database)
    }
}