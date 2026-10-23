package com.trailnest.app.domain

/** Isolate machine-readable fragments when embedding them in a paragraph of either direction. */
object DirectionalText { fun ltr(value: String) = "\u2066$value\u2069" }
