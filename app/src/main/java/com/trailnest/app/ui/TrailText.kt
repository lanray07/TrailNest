package com.trailnest.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

/** Optional measurement hook for on-device layout checks; absent in normal app composition. */
val LocalTextLayoutAudit = staticCompositionLocalOf<((String, TextLayoutResult) -> Unit)?> { null }

@Composable fun TrailText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified, style: TextStyle = LocalTextStyle.current) {
    val audit = LocalTextLayoutAudit.current
    androidx.compose.material3.Text(text, modifier, color = color, fontSize = fontSize,
        fontFamily = fontFamily, fontWeight = fontWeight, lineHeight = lineHeight,
        letterSpacing = letterSpacing, style = style.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)), onTextLayout = { audit?.invoke(text, it) })
}
