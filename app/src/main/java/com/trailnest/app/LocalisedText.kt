package com.trailnest.app

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.trailnest.app.domain.TextFailure
import java.text.NumberFormat
import java.util.Locale

fun Context.userMessage(error: Throwable, @StringRes fallback: Int): String {
    val id = if (error is TextFailure) errorResources[error.resourceName] ?: fallback else fallback
    return getString(id)
}

fun Context.distanceText(metres: Double, imperial: Boolean, locale: Locale = resources.configuration.locales[0]): String {
    val number = NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }
        .format(metres / if (imperial) 1609.344 else 1000.0)
    return getString(if (imperial) R.string.distance_miles_value else R.string.distance_km_value, number)
}
@Composable fun distanceText(metres: Double, imperial: Boolean) = LocalContext.current.distanceText(metres, imperial, LocalConfiguration.current.locales[0])
@Composable fun countryLabel(iso: String): String = Locale("", iso).getDisplayCountry(LocalConfiguration.current.locales[0])

private val defaultKit = listOf(R.string.checklist_default_0, R.string.checklist_default_1, R.string.checklist_default_2,
    R.string.checklist_default_3, R.string.checklist_default_4, R.string.checklist_default_5, R.string.checklist_default_6,
    R.string.checklist_default_7, R.string.checklist_default_8, R.string.checklist_default_9, R.string.checklist_default_10)
fun Context.checklistDefault(index: Int) = getString(defaultKit[index])
@Composable fun checklistDefault(index: Int) = stringResource(defaultKit[index])
fun Context.englishChecklistDefault(index: Int): String {
    val config = android.content.res.Configuration(resources.configuration).apply { setLocale(Locale.UK) }
    return createConfigurationContext(config).getString(defaultKit[index])
}

// Static references keep the error resource contract checked by the Android compiler.
private val errorResources: Map<String, Int> = mapOf(
    "error_a_completed_walk_cannot_be_dated_in_the_future" to R.string.error_a_completed_walk_cannot_be_dated_in_the_future,
    "error_choose_a_date_from_1970_onwards" to R.string.error_choose_a_date_from_1970_onwards,
    "error_choose_a_gpx_file_with_at_least_two_track_or_route_points" to R.string.error_choose_a_gpx_file_with_at_least_two_track_or_route_points,
    "error_choose_a_photo_smaller_than_20_mb" to R.string.error_choose_a_photo_smaller_than_20_mb,
    "error_choose_a_return_time_outside_a_daylight_saving_clock_change" to R.string.error_choose_a_return_time_outside_a_daylight_saving_clock_change,
    "error_choose_a_supported_image_file" to R.string.error_choose_a_supported_image_file,
    "error_choose_an_expected_return_time_in_the_future" to R.string.error_choose_an_expected_return_time_in_the_future,
    "error_contact_details_must_be_500_characters_or_fewer" to R.string.error_contact_details_must_be_500_characters_or_fewer,
    "error_each_walk_can_contain_up_to_eight_photos" to R.string.error_each_walk_can_contain_up_to_eight_photos,
    "error_enter_a_distance_from_zero_to_1_000_zero_means_no_distance_was_me" to R.string.error_enter_a_distance_from_zero_to_1_000_zero_means_no_distance_was_me,
    "error_enter_a_duration_between_1_and_10_080_minutes" to R.string.error_enter_a_duration_between_1_and_10_080_minutes,
    "error_enter_a_walk_title_of_up_to_120_characters" to R.string.error_enter_a_walk_title_of_up_to_120_characters,
    "error_enter_an_estimated_duration_between_1_and_10_080_minutes" to R.string.error_enter_an_estimated_duration_between_1_and_10_080_minutes,
    "error_external_xml_declarations_are_not_allowed" to R.string.error_external_xml_declarations_are_not_allowed,
    "error_gpx_exceeds_50_000_points" to R.string.error_gpx_exceeds_50_000_points,
    "error_gpx_must_be_smaller_than_5_mb" to R.string.error_gpx_must_be_smaller_than_5_mb,
    "error_import_one_continuous_route_or_track" to R.string.error_import_one_continuous_route_or_track,
    "error_import_one_track_at_a_time" to R.string.error_import_one_track_at_a_time,
    "error_invalid_photo_reference" to R.string.error_invalid_photo_reference,
    "error_mixed_routes_and_tracks_are_not_supported" to R.string.error_mixed_routes_and_tracks_are_not_supported,
    "error_multiple_track_segments_could_conceal_gaps_import_one_continuous_" to R.string.error_multiple_track_segments_could_conceal_gaps_import_one_continuous_,
    "error_save_the_walk_before_adding_a_photo" to R.string.error_save_the_walk_before_adding_a_photo,
    "error_the_document_root_must_be_gpx" to R.string.error_the_document_root_must_be_gpx,
    "error_the_image_cannot_be_decoded" to R.string.error_the_image_cannot_be_decoded,
    "error_the_route_has_no_length" to R.string.error_the_route_has_no_length,
    "error_the_selected_photo_cannot_be_opened" to R.string.error_the_selected_photo_cannot_be_opened,
    "error_unable_to_checkpoint_recording_free_device_storage_before_continu" to R.string.error_unable_to_checkpoint_recording_free_device_storage_before_continu,
    "error_unable_to_open_destination" to R.string.error_unable_to_open_destination,
    "error_unable_to_open_this_file" to R.string.error_unable_to_open_this_file,
    "error_use_an_item_name_between_1_and_120_characters" to R.string.error_use_an_item_name_between_1_and_120_characters
)
