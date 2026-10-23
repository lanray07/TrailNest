package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trailnest.app.billing.BillingState
import java.text.DateFormat
import java.util.Date

@Composable fun BillingPanel(state: BillingState, buy: () -> Unit, restore: () -> Unit) {
    TrailPanel(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.ui_trailnest_premium), true)
        if (!state.configured) {
            TrailText(stringResource(R.string.ui_purchasing_is_not_available_in_this_build_no_products_prices_or_p))
            TrailText(stringResource(R.string.ui_your_checklist_journal_and_one_offline_geometry_pack_are_availabl))
        } else {
            TrailText(stringResource(R.string.billing_benefit))
            if (state.premiumUntil != null) {
                val locale = LocalConfiguration.current.locales[0]
                val until = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(state.premiumUntil))
                TrailText(stringResource(R.string.billing_active, until))
            } else if (state.price != null) TrailText(stringResource(R.string.billing_offer, state.price))
            else if (!state.busy) TrailText(stringResource(R.string.billing_no_offer))
            if (state.busy) TrailText(stringResource(R.string.billing_checking))
            if (state.pending) TrailText(stringResource(R.string.billing_pending))
            if (state.problem) FormError(stringResource(R.string.billing_problem))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.premium && !state.pending) Button(onClick = buy, enabled = state.canBuy, modifier = Modifier.testTag("billing-subscribe")) { TrailText(stringResource(R.string.billing_subscribe)) }
                OutlinedButton(onClick = restore, enabled = !state.busy, modifier = Modifier.testTag("billing-restore")) { TrailText(stringResource(R.string.billing_restore)) }
            }
        }
    }
}
