package com.you.secretvault

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val vault = SecretVault()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val pin = findViewById<EditText>(R.id.pin)
        val unlock = findViewById<Button>(R.id.unlock)
        val secret = findViewById<TextView>(R.id.secret)

        // On launch: if rooted, warn and disable the UI (same as iOS plan).
        if (vault.isRooted()) {
            status.text = "device not trusted"
            pin.isEnabled = false
            unlock.isEnabled = false
            return
        }
        status.text = "device trusted"

        unlock.setOnClickListener {
            // Gate on BOTH the license and the PIN — two boolean flips to watch.
            if (vault.isLicenseValid()) {
                val result = vault.revealSecret(pin.text.toString())
                secret.text = result ?: "wrong PIN"
            } else {
                secret.text = "license invalid"
            }
        }
    }
}
