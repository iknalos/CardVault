package com.iknalos.cardvault

data class Card(
    var id: String,
    var name: String,
    var value: String,
    var format: String,   // bwip-js bcid, same identifiers as the CardVault web app
    var notes: String,
    var color: String,    // hex like #6c7bff
    var created: String   // ISO timestamp
)
