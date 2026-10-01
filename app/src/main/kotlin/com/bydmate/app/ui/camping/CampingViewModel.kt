package com.bydmate.app.ui.camping

import androidx.lifecycle.ViewModel
import com.bydmate.app.camping.CampingController
import com.bydmate.app.camping.CampingSettings
import com.bydmate.app.camping.CampingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CampingViewModel @Inject constructor(
    private val controller: CampingController,
) : ViewModel() {

    private val _settings = MutableStateFlow(controller.settingsStore.load())
    val settings: StateFlow<CampingSettings> = _settings.asStateFlow()

    val state: StateFlow<CampingState> = controller.state

    /** Every change is kept at once: the next visit opens with the same setup. */
    fun edit(change: CampingSettings.() -> CampingSettings) {
        _settings.update { it.change() }
        controller.settingsStore.save(_settings.value)
    }

    fun start() {
        controller.clearMessages()
        controller.start(_settings.value)
    }

    fun stop() = controller.stop()

    fun dismissMessage() = controller.clearMessages()
}
