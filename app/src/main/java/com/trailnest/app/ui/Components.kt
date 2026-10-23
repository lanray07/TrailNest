package com.trailnest.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF285C4D)
val Ivory = Color(0xFFF7F5EE)
val Ink = Color(0xFF26332E)
val Sage = Color(0xFFE7EBDF)
@Composable fun Heading(text: String, small: Boolean = false) { Text(text, fontFamily = FontFamily.Serif, fontSize = if (small) 26.sp else 38.sp, lineHeight = if (small) 32.sp else 43.sp, fontWeight = FontWeight.Medium) }
@Composable fun SectionLabel(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.primary) }
@Composable fun TrailPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable fun EmptyPanel(title: String, body: String, action: String? = null, onClick: () -> Unit = {}) {
    TrailPanel(Modifier.fillMaxWidth()) { Heading(title, true); Text(body, lineHeight = 24.sp); if (action != null) Button(onClick) { Text(action) } }
}
@Composable fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Heading(title); if (subtitle != null) Text(subtitle, lineHeight = 24.sp); content()
        Spacer(Modifier.height(32.dp))
    }
}
