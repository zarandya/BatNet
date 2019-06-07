package org.batnet.ui

import android.os.Bundle
import android.os.Message.obtain
import android.support.v7.app.AppCompatActivity
import com.stfalcon.chatkit.messages.MessagesListAdapter
import org.batnet.R

import kotlinx.android.synthetic.main.activity_messaging.*
import org.batnet.App.Companion.db
import org.batnet.db.*
import org.batnet.services.MSG_RECEIVE_MESSAGE
import org.batnet.services.MSG_SEND_MESSAGE
import org.batnet.services.bindSignalProcessingService
import org.batnet.db.*
import org.batnet.services.*
import java.util.*
import kotlin.concurrent.thread

class MessagingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messaging)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        thread {
            val me = db.users.currentUser

            val dialog = db.dialogs[intent.extras!!.getString(DIALOG_ID_INTENT_EXTRA_NAME)!!]!!

            val messagesInDialog = db.messages.getOnDialog(dialog.ID)

            runOnUiThread {
                val adapter = MessagesListAdapter<Message>(me.ID, null)
                messagesList.setAdapter(adapter)


                supportActionBar?.title = dialog.name

                adapter.addToEnd(messagesInDialog, false)

                fun handleReceiveMessage(id: String) {
                    thread {
                        val message = db.messages[id]!!

                        runOnUiThread {
                            adapter.addToStart(message, true)
                        }
                    }
                }

                val service = bindSignalProcessingService {
                    when (it.what) {
                        MSG_RECEIVE_MESSAGE -> handleReceiveMessage(it.obj!! as String)
                    }
                    false
                }


                input.setInputListener {
                    thread {
                        val message = Message(UUID.randomUUID().toString(), it.toString(), me.ID, Date(), dialog.ID)

                        runOnUiThread {
                            adapter.addToStart(message, true)
                        }
                        db.messages += message

                        service.messenger?.send(obtain(null, MSG_SEND_MESSAGE, message.ID))

                    }
                    true
                }
            }
        }


    }



}
