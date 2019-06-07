package org.batnet.receiver

sealed class UltrasoundMessage

data class TextMessage(val msg: String): UltrasoundMessage()

data class ChatMessage(val uuid: String, val sender: String, val dialog: String, val text: String): UltrasoundMessage()

data class BeaconMessage(val frequencies: Map<Int, Double>): UltrasoundMessage()

data class BeaconResponse(val frequencies: IntArray): UltrasoundMessage()