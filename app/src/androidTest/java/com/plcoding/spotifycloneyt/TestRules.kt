package com.plcoding.spotifycloneyt

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * Grants POST_NOTIFICATIONS on Android 13+ so the runtime permission dialog doesn't cover the
 * app during the test. The permission doesn't exist on older versions.
 */
fun grantNotificationPermission(): TestRule =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        RuleChain.emptyRuleChain()
    }
