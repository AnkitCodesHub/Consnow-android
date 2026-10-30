package com.Consnow.core.common

import java.io.IOException
import java.net.SocketTimeoutException

/** Shown whenever the phone has no internet. Compared against in the UI to pick the offline layout. */
const val OFFLINE_MESSAGE = "You're offline. Connect to the internet and try again."

/**
 * Turns a raw exception into text that's safe to show users.
 * Never show e.message directly: it contains things like "Unable to resolve host …railway.app".
 */
fun Exception.toFriendlyMessage(): String = when (this) {
    is SocketTimeoutException -> "Your connection is slow. Please try again."
    is IOException -> OFFLINE_MESSAGE // no network, DNS failure, connection refused
    else -> "Something went wrong. Please try again."
}
