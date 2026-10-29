package com.trailnest.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF285C4D)
val Ivory = Color(0xFFF7F5EE)
val Ink = Color(0xFF26332E)
val Sage = Color(0xFFE7EBDF)
fun trailColorScheme(dark: Boolean) = if (dark) darkColorScheme(primary = Color(0xFFA7D3BC), background = Color(0xFF17241E), surface = Color(0xFF22332A), onSurface = Ivory) else lightColorScheme(primary = Forest, background = Ivory, surface = Color(0xFFFCFBF7), onSurface = Ink, outlineVariant = Color(0xFFDDDFD5))
@Composable fun Heading(text: String, small: Boolean = false) { TrailText(text, Modifier.semantics { heading() }, fontFamily = FontFamily.Serif, fontSize = if (small) 26.sp else 38.sp, lineHeight = if (small) 36.sp else 48.sp, fontWeight = FontWeight.Medium) }
@Composable fun SectionLabel(text: String) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    TrailText(text.uppercase(locale), style = MaterialTheme.typography.labelMedium,
        letterSpacing = if (locale.language in listOf("ar", "hi", "ja", "zh", "ko")) 0.sp else 1.6.sp, color = MaterialTheme.colorScheme.primary)
}
@Composable private fun contentPadding() = if (androidx.compose.ui.platform.LocalConfiguration.current.let { it.fontScale >= 1.5f || it.screenWidthDp < 400 }) 16.dp else 24.dp
@Composable fun TrailPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(contentPadding()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable fun EmptyPanel(title: String, body: String, action: String? = null, onClick: () -> Unit = {}) {
    TrailPanel(Modifier.fillMaxWidth()) { Heading(title, true); TrailText(body, lineHeight = 24.sp); if (action != null) Button(onClick) { TrailText(action) } }
}
@Composable fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().semantics { paneTitle = title; isTraversalGroup = true }.verticalScroll(rememberScrollState()).padding(contentPadding()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Heading(title); if (subtitle != null) TrailText(subtitle, lineHeight = 24.sp); content()
        Spacer(Modifier.height(32.dp))
    }
}
