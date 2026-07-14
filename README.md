# CardVault

A tiny PWA that stores your own ID/membership card barcodes on your phone so you don't have to carry the physical cards.

**Live app:** https://iknalos.github.io/CardVault/

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
