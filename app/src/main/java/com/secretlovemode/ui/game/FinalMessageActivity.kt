package com.secretlovemode.ui.game

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.secretlovemode.MyApplication
import com.secretlovemode.R
import com.secretlovemode.ui.main.SlmViewModel

class FinalMessageActivity : AppCompatActivity() {

    private lateinit var slmViewModel: SlmViewModel
    private lateinit var etFinalMessage: EditText
    private lateinit var btnSubmitFinalMessage: Button
    private lateinit var tvFinalMessageTitle: TextView
    private var inputKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_final_message)

        slmViewModel = (application as MyApplication).slmViewModel
        inputKey = intent.getStringExtra("input_key")
        val placeholder = intent.getStringExtra("placeholder") ?: "入力してください"
        val messageText = intent.getStringExtra("message_text")

        etFinalMessage = findViewById(R.id.etFinalMessage)
        btnSubmitFinalMessage = findViewById(R.id.btnSubmitFinalMessage)
        tvFinalMessageTitle = findViewById(R.id.tvFinalMessageTitle)

        // Set title based on inputKey or message text
        tvFinalMessageTitle.text = when (inputKey) {
            "confession" -> "告白メッセージ"
            "name_input" -> "名前を入力してください"
            else -> messageText ?: "メッセージ"
        }
        
        // Set placeholder for EditText
        etFinalMessage.hint = placeholder

        btnSubmitFinalMessage.setOnClickListener {
            val message = etFinalMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                // Save input to ViewModel based on inputKey
                inputKey?.let { key ->
                    slmViewModel.setGameStateInput(key, message)
                }
                
                // 결과를 GameActivity로 반환
                val resultIntent = Intent()
                resultIntent.putExtra("input_key", inputKey)
                resultIntent.putExtra("input_message", message)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "入力してください。", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
