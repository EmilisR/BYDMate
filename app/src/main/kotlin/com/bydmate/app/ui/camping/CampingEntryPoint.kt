package com.bydmate.app.ui.camping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bydmate.app.camping.CampingController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Hands the @Singleton [CampingController] to composables outside the camping screen's view model. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CampingEntryPoint {
    fun campingController(): CampingController
}

/** Whether a camping session runs right now, for the dashboard entry. */
@Composable
fun rememberCampingActive(): State<Boolean> {
    val context = LocalContext.current
    val controller = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, CampingEntryPoint::class.java)
            .campingController()
    }
    val state = controller.state.collectAsState()
    return remember(state) { derivedStateOf { state.value.active } }
}
