package org.batnet.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import java.util.*

@Dao
interface MessagesDao {

    @Query("SELECT * FROM $TABLE_MESSAGES WHERE $COLUMN_MESSAGES_DIALOG = :dialogId " +
            "ORDER BY $COLUMN_MESSAGES_DATE_CREATED DESC LIMIT 100")
    fun getOnDialog(dialogId: String): List<Message>

    @Query("SELECT * FROM $TABLE_MESSAGES WHERE $COLUMN_MESSAGES_ID = :id")
    operator fun get(id: String): Message?

    @Insert
    operator fun plusAssign(message: Message)

    @Insert
    operator fun plusAssign(message: MessageToForward)

    @get:Query("SELECT * FROM $TABLE_MESSAGES_TO_FORWARD")
    val toForward: List<MessageToForward>

    @Query("DELETE FROM $TABLE_MESSAGES_TO_FORWARD WHERE $COLUMN_MESSAGES_DATE_CREATED < :dateLimit")
    fun removeOldMessagesToForward(dateLimit: Date)
}
