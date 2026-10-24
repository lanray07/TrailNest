package com.trailnest.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** One supported-language source: the generated locale configuration, including regional tags. */
object AppLanguage {
    fun supportedTags(context: Context): List<String> = context.resources.getXml(R.xml.locales_config).use { xml ->
        buildList {
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "locale") {
                    xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let { add(it) }
                }
                xml.next()
            }
        }
    }
    fun selectedTag(): String = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag().orEmpty()
    fun nativeName(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || tag in supportedTags(context))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
