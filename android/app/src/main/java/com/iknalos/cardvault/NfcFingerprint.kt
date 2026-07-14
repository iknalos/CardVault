package com.iknalos.cardvault

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

/**
 * Reads everything the phone's NFC stack decodes for a tag: technology list,
 * ATQA/SAK, ATS/historical bytes, MIFARE type/size, and any readable NDEF or
 * memory. The raw RF signal is NOT available on Android — the controller only
 * exposes decoded bytes — so this is the deepest fingerprint a phone can take.
 *
 * All tag I/O must run off the main thread; callers use readOffline().
 */
object NfcFingerprint {

    private fun hex(bytes: ByteArray?): String =
        bytes?.joinToString(" ") { "%02X".format(it) } ?: ""

    /** Human-readable multi-line summary. Best-effort: skips anything the card won't reveal. */
    fun read(tag: Tag): String {
        val sb = StringBuilder()
        val techs = tag.techList.map { it.substringAfterLast('.') }
        sb.append("Type: ").append(describeType(techs)).append('\n')
        sb.append("Tag ID (UID): ").append(hex(tag.id)).append('\n')
        sb.append("Technologies: ").append(techs.joinToString(", ")).append('\n')

        NfcA.get(tag)?.let { a ->
            sb.append("ATQA: ").append(hex(a.atqa)).append('\n')
            sb.append("SAK: ").append("%02X".format(a.sak)).append('\n')
        }
        NfcB.get(tag)?.let { b -> sb.append("App data: ").append(hex(b.applicationData)).append('\n') }
        NfcF.get(tag)?.let { f -> sb.append("Manufacturer: ").append(hex(f.manufacturer)).append('\n') }
        NfcV.get(tag)?.let { v ->
            sb.append("DSFID: ").append("%02X".format(v.dsfId))
              .append("  RespFlags: ").append("%02X".format(v.responseFlags)).append('\n')
        }

        IsoDep.get(tag)?.let { iso ->
            val ats = iso.historicalBytes ?: iso.hiLayerResponse
            if (ats != null && ats.isNotEmpty()) sb.append("ATS/history: ").append(hex(ats)).append('\n')
        }

        MifareClassic.get(tag)?.let { mc ->
            sb.append("MIFARE Classic: ").append(mc.size).append(" bytes, ")
              .append(mc.sectorCount).append(" sectors\n")
            sb.append(readMifareClassic(mc))
        }

        MifareUltralight.get(tag)?.let { mu ->
            sb.append("MIFARE Ultralight / NTAG\n")
            sb.append(readUltralight(mu))
        }

        Ndef.get(tag)?.let { ndef ->
            sb.append("NDEF: ").append(ndef.type)
              .append(", capacity ").append(ndef.maxSize).append(" bytes\n")
            sb.append(readNdef(ndef))
        }

        return sb.toString().trimEnd()
    }

    private fun describeType(techs: List<String>): String = when {
        "MifareClassic" in techs -> "MIFARE Classic"
        "MifareUltralight" in techs -> "MIFARE Ultralight / NTAG"
        "IsoDep" in techs && "NfcA" in techs -> "ISO 14443-4 A (e.g. DESFire / smartcard)"
        "IsoDep" in techs && "NfcB" in techs -> "ISO 14443-4 B"
        "NfcV" in techs -> "ISO 15693 (NfcV)"
        "NfcF" in techs -> "FeliCa (NfcF)"
        "NfcA" in techs -> "ISO 14443-3 A"
        else -> techs.firstOrNull() ?: "Unknown"
    }

    private fun readMifareClassic(mc: MifareClassic): String {
        val out = StringBuilder()
        var readable = 0
        var protectedCount = 0
        try {
            mc.connect()
            for (s in 0 until mc.sectorCount) {
                val ok = try {
                    mc.authenticateSectorWithKeyA(s, MifareClassic.KEY_DEFAULT) ||
                        mc.authenticateSectorWithKeyA(s, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY) ||
                        mc.authenticateSectorWithKeyA(s, MifareClassic.KEY_NFC_FORUM)
                } catch (e: Exception) { false }
                if (ok) readable++ else protectedCount++
            }
        } catch (e: Exception) {
            return "  (could not read: card moved away)\n"
        } finally {
            try { mc.close() } catch (e: Exception) {}
        }
        out.append("  Sectors: ").append(readable).append(" open with default keys, ")
           .append(protectedCount).append(" protected\n")
        if (protectedCount > 0) out.append("  This card is protected — its contents can't be read.\n")
        return out.toString()
    }

    private fun readUltralight(mu: MifareUltralight): String {
        return try {
            mu.connect()
            val pages = mu.readPages(0)  // first 4 pages
            "  First pages: " + hex(pages) + "\n"
        } catch (e: Exception) {
            "  (memory not readable)\n"
        } finally {
            try { mu.close() } catch (e: Exception) {}
        }
    }

    private fun readNdef(ndef: Ndef): String {
        return try {
            ndef.connect()
            val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
            if (msg == null) return "  (empty)\n"
            val out = StringBuilder()
            msg.records.forEach { r ->
                val text = decodeRecord(r.payload)
                if (text.isNotEmpty()) out.append("  Content: ").append(text).append('\n')
            }
            if (out.isEmpty()) "  (no text content)\n" else out.toString()
        } catch (e: Exception) {
            "  (NDEF not readable)\n"
        } finally {
            try { ndef.close() } catch (e: Exception) {}
        }
    }

    /** Best-effort text extraction from an NDEF record payload (text & URI records). */
    private fun decodeRecord(payload: ByteArray?): String {
        if (payload == null || payload.isEmpty()) return ""
        return try {
            // Text record: first byte = status, low 6 bits = language-code length
            val langLen = payload[0].toInt() and 0x3F
            if (payload.size > langLen + 1) {
                String(payload, langLen + 1, payload.size - langLen - 1, Charsets.UTF_8)
                    .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".:/@#-_+" }
                    .trim()
            } else ""
        } catch (e: Exception) { "" }
    }
}
