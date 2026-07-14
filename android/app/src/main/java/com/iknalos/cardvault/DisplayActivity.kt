package com.iknalos.cardvault

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DisplayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        // Scanners need a bright screen: force max brightness and keep the
        // screen awake while a card is showing. Both revert on close.
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Light status bar area so the white screen doesn't clash
        window.statusBarColor = android.graphics.Color.WHITE

        findViewById<android.view.View>(R.id.closeBtn).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.editBtn).setOnClickListener {
            startActivity(
                Intent(this, EditCardActivity::class.java)
                    .putExtra("card_id", intent.getStringExtra("card_id"))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val card = CardStore.load(this).firstOrNull { it.id == intent.getStringExtra("card_id") }
        if (card == null) { finish(); return }  // deleted in the edit screen

        findViewById<TextView>(R.id.dispName).text = card.name
        findViewById<TextView>(R.id.dispValue).text =
            if (card.notes.isEmpty()) card.value else card.value + "\n" + card.notes
        try {
            findViewById<ImageView>(R.id.barcodeImage).setImageBitmap(Barcodes.render(card.value, card.format))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not draw this barcode", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
