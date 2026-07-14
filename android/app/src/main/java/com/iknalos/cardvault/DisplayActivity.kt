package com.iknalos.cardvault

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows a card. Two modes, chosen by the card's format:
 *  - barcode: white screen, max brightness, rendered barcode for scanners
 *  - NFC tap card: colored screen with a huge label, spoken aloud (TTS)
 */
class DisplayActivity : AppCompatActivity() {

    private var isTapCard = false
    private var tts: TextToSpeech? = null
    private var announced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val card = loadCard()
        if (card == null) { finish(); return }
        isTapCard = card.format == Barcodes.NFC_FORMAT

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (isTapCard) {
            setContentView(R.layout.activity_announce)
            findViewById<View>(R.id.annCloseBtn).setOnClickListener { finish() }
            findViewById<View>(R.id.annEditBtn).setOnClickListener { openEditor() }
            findViewById<View>(R.id.speakBtn).setOnClickListener { speak(loadCard()) }
            val toggle = findViewById<TextView>(R.id.annDetailsToggle)
            val detailsView = findViewById<TextView>(R.id.annDetails)
            toggle.setOnClickListener {
                val show = detailsView.visibility != View.VISIBLE
                detailsView.visibility = if (show) View.VISIBLE else View.GONE
                toggle.text = if (show) "▾ Card details" else "▸ Card details"
            }
        } else {
            setContentView(R.layout.activity_display)
            // Scanners need a bright screen: force max brightness while showing.
            window.attributes = window.attributes.apply { screenBrightness = 1f }
            window.statusBarColor = Color.WHITE
            findViewById<View>(R.id.closeBtn).setOnClickListener { finish() }
            findViewById<View>(R.id.editBtn).setOnClickListener { openEditor() }
        }
    }

    private fun loadCard(): Card? =
        CardStore.load(this).firstOrNull { it.id == intent.getStringExtra("card_id") }

    private fun openEditor() {
        startActivity(
            Intent(this, EditCardActivity::class.java)
                .putExtra("card_id", intent.getStringExtra("card_id"))
        )
    }

    override fun onResume() {
        super.onResume()
        val card = loadCard()
        if (card == null) { finish(); return }  // deleted in the edit screen

        if (isTapCard) {
            val color = try { Color.parseColor(card.color) } catch (e: Exception) { Color.parseColor("#6c7bff") }
            findViewById<LinearLayout>(R.id.announceRoot).setBackgroundColor(color)
            window.statusBarColor = color
            findViewById<TextView>(R.id.annName).text = card.name
            findViewById<TextView>(R.id.annNotes).text = card.notes
            val hasDetails = card.details.isNotEmpty()
            findViewById<TextView>(R.id.annDetailsToggle).visibility = if (hasDetails) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.annDetails).text = card.details
            if (!announced) {
                announced = true
                vibrate()
                speak(card)
            }
        } else {
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

    private fun speak(card: Card?) {
        if (card == null) return
        val text = "This is your ${card.name}." +
                if (card.notes.isNotEmpty()) " ${card.notes}" else ""
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "announce")
        } else {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "announce")
                }
            }
        }
    }

    private fun vibrate() {
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(150)
            }
        } catch (e: Exception) { /* vibration is best-effort */ }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
