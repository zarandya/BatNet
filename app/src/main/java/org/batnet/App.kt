package org.batnet

import android.app.Application
import android.arch.persistence.room.Room
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION.SDK_INT
import org.batnet.db.AppDatabase
import org.batnet.db.User
import org.batnet.db.UserIdHolder
import org.batnet.db.users
import org.batnet.services.SignalProcessingService
import java.util.UUID.randomUUID
import kotlin.concurrent.thread

class App: Application() {

    override fun onCreate() {
        super.onCreate()

        db = Room
                .databaseBuilder(applicationContext, AppDatabase::class.java, "batnet-database")
                .fallbackToDestructiveMigration()
                .build()

        thread {
            var me: UserIdHolder? = db.users.currentUserId
            if (me == null) {
                val id = randomUUID()
                me = UserIdHolder(id.toString())
                val meUser = User(id.toString(), "John Smith")
                db.users += meUser
                db.users.currentUserId = me


            }
        }

        if (SDK_INT > VERSION_CODES.O) {
            startForegroundService(Intent(this, SignalProcessingService::class.java))
        }
        else {
            startService(Intent(this, SignalProcessingService::class.java))
        }

    }

    companion object {
        lateinit var db: AppDatabase private set
    }
}

