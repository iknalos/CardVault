# CardVault

Store your own ID/membership card barcodes on your phone so you don't have to carry the physical cards. Two versions in this repo:

- **Web app (PWA):** https://iknalos.github.io/CardVault/ — works on any phone, install via "Add to Home Screen"
- **Android app (Kotlin):** download `CardVault.apk` from [Releases](https://github.com/iknalos/CardVault/releases) — adds encrypted storage, optional fingerprint/PIN lock, and automatic max screen brightness when showing a barcode

## Android app

Designed to be senior-friendly: large text, big buttons, two-screen simplicity.

- Card data is stored with **EncryptedSharedPreferences** — AES-256 encrypted with a key kept in the phone's hardware Keystore, so card numbers are unreadable even if the file is extracted
- Optional **app lock** (fingerprint or device PIN) in the ⋮ menu, off by default
- Scan cards with the camera (ZXing), same 13 barcode formats as the web version
- Showing a card forces **maximum screen brightness** and keeps the screen awake
- JSON **backup export/import** uses the same format as the web app, so cards move freely between both

Source lives in [`android/`](android/); the APK is built and released automatically by GitHub Actions on every push that touches it.

## How it works

1. Open the link on your phone (Chrome/Safari) and **Add to Home Screen** to install it.
2. Tap **+** and point the camera at the barcode on a physical card — it auto-detects the value and format.
3. Name the card, pick a color, save.
4. At the scanner/reader: tap the card, and the barcode is shown full-screen on a white background. Turn brightness up for stubborn scanners.

## Privacy

All card data lives in `localStorage` **on your device only**. Nothing is uploaded anywhere. Use *Export backup* in the ⋯ menu to save a JSON copy (keep it somewhere private — it contains your card numbers).

## Supported barcode types

QR Code, Code 128, Code 39, Code 93, EAN-13/8, UPC-A/E, ITF, Codabar, PDF417, Aztec, Data Matrix.

> Note: this only covers **optical barcodes**. RFID/NFC tap badges can't be emulated by a web app (and mostly not by phones at all).

## Tech

Single-file vanilla JS PWA. Scanning via [html5-qrcode](https://github.com/mebjas/html5-qrcode), rendering via [bwip-js](https://github.com/metafloor/bwip-js) (both bundled in `vendor/` for offline use). Service worker caches everything, so the app works with no connection.
