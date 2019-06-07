package org.batnet.db

import android.arch.persistence.room.*
import com.stfalcon.chatkit.commons.models.IMessage
import org.batnet.App.Companion.db
import org.batnet.utils.LazyWhenOnUIThread
import java.util.*

const val TABLE_MESSAGES = "messages"
const val COLUMN_MESSAGES_ID = "message_id"
const val COLUMN_MESSAGES_TEXT = "text"
const val COLUMN_MESSAGES_DATE_CREATED = "date_created"
const val COLUMN_MESSAGES_USER = "user"
const val COLUMN_MESSAGES_DIALOG = "dialog"

@Entity(
        tableName = TABLE_MESSAGES,
        foreignKeys = [
            ForeignKey(entity = User::class, parentColumns = [COLUMN_USERS_ID], childColumns = [COLUMN_MESSAGES_USER]),
            ForeignKey(entity = Dialog::class, parentColumns = [COLUMN_DIALOGS_ID], childColumns = [COLUMN_MESSAGES_DIALOG])
        ]
)
data class Message(
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
) : IMessage {

    @Ignore
    private val user: Lazy<User> = LazyWhenOnUIThread { db.users[author]!! }

    // Can't believe I still have to do this Java boilerplate

    @Deprecated("Use with interface only. ", ReplaceWith("ID"))
    override fun getId(): String = ID

    @Deprecated("Use with interface only. ", ReplaceWith("dateCreated"))
    override fun getCreatedAt(): Date = dateCreated

    @Deprecated("Use with interface only. ", ReplaceWith("author"))
    override fun getUser(): User = user.value

    @Deprecated("Use with interface only. ", ReplaceWith("content"))
    override fun getText(): String = content
}