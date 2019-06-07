package org.batnet.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Messenger
import android.support.v7.app.AppCompatActivity
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.dialogs.DialogsListAdapter
import kotlinx.android.synthetic.main.activity_dialogs.*
import org.batnet.App.Companion.db
import org.batnet.db.DIALOG_ID_INTENT_EXTRA_NAME
import org.batnet.db.Dialog
import org.batnet.db.dialogs
import org.batnet.db.messages
import org.batnet.R
import org.batnet.db.*
import org.batnet.services.MSG_RECEIVE_MESSAGE
import kotlin.concurrent.thread

class DialogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialogs)
        setSupportActionBar(toolbar)

        val dialogsListAdapter = DialogsListAdapter<Dialog>(ImageLoader { _, _, _ -> })
        dialogsList.setAdapter(dialogsListAdapter)

        thread {

            val dialogs = db.dialogs.all

            if (dialogs.size == 0) {
                val d = Dialog("test", "Test")
                dialogs += d
                db.dialogs += d
            }

            runOnUiThread {
                dialogsListAdapter.addItems(dialogs)
            }
        }

        dialogsListAdapter.setOnDialogClickListener {
            startActivity(Intent(this, MessagingActivity::class.java)
                    .putExtra(DIALOG_ID_INTENT_EXTRA_NAME, it.ID)
            )
        }
    }

    private fun handleReceiveMessage(id: String) { thread {
        val message = db.messages[id]!!
        val dialog = db.dialogs[message.dialog]!!
        dialog.lastMessage = message
        // TODO increase unread count or smth
    }}

    private val messenger = Messenger(Handler {
        when (it.what) {
            MSG_RECEIVE_MESSAGE -> {
                handleReceiveMessage(it.obj as String)
            }
        }
        false
    })
}
