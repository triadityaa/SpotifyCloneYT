package com.plcoding.spotifycloneyt

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plcoding.spotifycloneyt.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val notificationPermission: TestRule = grantNotificationPermission()

    @Test
    fun launchesHomeScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The song list is inside HomeFragment, so this also checks the NavHostFragment.
            // (Matching R.id.navHostFragment is ambiguous: NavHostFragment gives its own
            // container view the same id.)
            onView(withId(R.id.rvAllSongs)).check(matches(isDisplayed()))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun survivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            onView(withId(R.id.rvAllSongs)).check(matches(isDisplayed()))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
