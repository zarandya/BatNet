package org.batnet.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import com.stfalcon.chatkit.commons.models.IDialog
import org.batnet.App.Companion.db
import org.batnet.utils.lazyListWhenOnUIThread

const val TABLE_DIALOGS = "dialogs"
const val COLUMN_DIALOGS_ID = "dialog_id"
const val COLUMN_DIALOGS_NAME = "dialog_name"

const val DIALOG_ID_INTENT_EXTRA_NAME = "dialog_id"

@Entity(tableName = TABLE_DIALOGS)
data class Dialog(
        @PrimaryKey
        @ColumnInfo(name = COLUMN_DIALOGS_ID)
        val ID: String,

        @ColumnInfo(name = COLUMN_DIALOGS_NAME)
        val name: String
): IDialog<Message> {

    @Ignore
    private val usersLazy = lazyListWhenOnUIThread { db.dialogs.getUsers(id) }

    @Ignore private var lastMessage: Message? = null

    override fun getDialogPhoto(): String = ""

    override fun getUnreadCount(): Int = 0 // TODO implement this

    override fun setLastMessage(message: Message?) {
        lastMessage = message
    } // TODO implement this

    @Deprecated("Use with interface only. ", ReplaceWith("ID"))
    override fun getId(): String = ID

    override fun getUsers(): List<User> = usersLazy.value

    override fun getLastMessage(): Message? = lastMessage

    @Deprecated("Use with interface only. ", ReplaceWith("name"))
    override fun getDialogName(): String = name

    companion object {
        private const val serialVersionUID = -5140896331181019668L
    }
}