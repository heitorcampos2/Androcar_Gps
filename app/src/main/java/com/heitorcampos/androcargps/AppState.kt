package com.heitorcampos.androcargps

import kotlinx.coroutines.flow.MutableStateFlow

data class SenderStatus(
    val running: Boolean = false,
    val clients: Int = 0,
    val accuracy: Float? = null,
    val lastFixAt: Long = 0L,
    val error: String? = null,
)

data class ReceiverStatus(
    val running: Boolean = false,
    val connected: Boolean = false,
    val host: String? = null,
    val message: String = "",
    val isError: Boolean = false,
    val lastFixAt: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedKmh: Float? = null,
    val accuracy: Float? = null,
    val mockError: String? = null,
)

/** Estado compartilhado entre os serviços e as telas. */
object AppState {
    val sender = MutableStateFlow(SenderStatus())
    val receiver = MutableStateFlow(ReceiverStatus())
}
