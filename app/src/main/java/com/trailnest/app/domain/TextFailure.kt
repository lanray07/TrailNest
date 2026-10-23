package com.trailnest.app.domain

/** Stable message key, resolved at the Android presentation boundary. */
class TextFailure(val resourceName: String) : IllegalArgumentException(resourceName)
