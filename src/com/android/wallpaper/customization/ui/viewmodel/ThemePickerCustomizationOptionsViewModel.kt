/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wallpaper.customization.ui.viewmodel

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.android.customization.packtheme.ui.viewmodel.PackThemeViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.grid.ui.viewmodel.GridPickerViewModel
import com.android.customization.picker.icon.ui.viewmodel.AppIconPickerViewModel
import com.android.customization.picker.mode.ui.viewmodel.DarkModeViewModel
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel2
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.APP_ICONS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.COLORS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.GRID
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.CLOCK
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.SHORTCUTS
import com.android.wallpaper.picker.common.preview.ui.viewmodel.WorkspacePreviewScreen
import com.android.wallpaper.picker.customization.ui.view.ApplyButton
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_DISABLED
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_ENABLED
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_IN_PROGRESS
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsData
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsViewModelFactory
import com.android.wallpaper.picker.customization.ui.viewmodel.DefaultCustomizationOptionsViewModel
import com.android.wallpaper.picker.preview.ui.util.AccessibilityUtil
import com.android.wallpaper.util.ActivityUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemePickerCustomizationOptionsViewModel
@AssistedInject
constructor(
    @ApplicationContext private val appContext: Context,
    defaultCustomizationOptionsViewModelFactory: DefaultCustomizationOptionsViewModel.Factory,
    keyguardQuickAffordancePickerViewModel2Factory: KeyguardQuickAffordancePickerViewModel2.Factory,
    colorPickerViewModelFactory: ColorPickerViewModel.Factory,
    clockPickerViewModelFactory: ClockPickerViewModel.Factory,
    gridPickerViewModelFactory: GridPickerViewModel.Factory,
    appIconPickerViewModelFactory: AppIconPickerViewModel.Factory,
    val darkModeViewModel: DarkModeViewModel,
    val packThemeViewModel: PackThemeViewModel,
    @Assisted private val viewModelScope: CoroutineScope,
    @Assisted("destination") initialDeepLinkDestination: String?,
    @Assisted("shortcutSlotId") initialDeepLinkShortcutSlotId: String?,
) : CustomizationOptionsViewModel {

    private val defaultCustomizationOptionsViewModel =
        defaultCustomizationOptionsViewModelFactory.create(
            viewModelScope,
            initialDeepLinkDestination,
            initialDeepLinkShortcutSlotId,
        )

    override val wallpaperCarouselViewModel =
        defaultCustomizationOptionsViewModel.wallpaperCarouselViewModel

    val clockPickerViewModel = clockPickerViewModelFactory.create(viewModelScope = viewModelScope)
    val keyguardQuickAffordancePickerViewModel2 =
        keyguardQuickAffordancePickerViewModel2Factory.create(
            viewModelScope = viewModelScope,
            initialDeepLinkShortcutSlotId = initialDeepLinkShortcutSlotId,
        )
    val colorPickerViewModel2 = colorPickerViewModelFactory.create(viewModelScope = viewModelScope)
    val gridPickerViewModel = gridPickerViewModelFactory.create(viewModelScope = viewModelScope)
    val appIconPickerViewModel =
        appIconPickerViewModelFactory.create(viewModelScope = viewModelScope)

    override val customizationOptionsData: Flow<CustomizationOptionsData> =
        if (BaseFlags.get(appContext).isExtendibleThemeManager()) {
            combine(
                gridPickerViewModel.isGridCustomizationAvailable,
                appIconPickerViewModel.isIconStyleAvailable,
                appIconPickerViewModel.isShapeOptionsAvailable,
            ) { isGridCustomizationAvailable, isIconStyleAvailable, isShapeOptionsAvailable ->
                ThemePickerCustomizationOptionsData(
                    isGridCustomizationAvailable = isGridCustomizationAvailable,
                    isIconStyleAvailable = isIconStyleAvailable,
                    isShapeAvailable = isShapeOptionsAvailable,
                    isColorCustomizationAvailable = !ActivityUtils.isSUWMode(appContext),
                )
            }
        } else {
            combine(
                gridPickerViewModel.isGridCustomizationAvailable,
                appIconPickerViewModel.isThemedIconAvailable,
                appIconPickerViewModel.isShapeOptionsAvailable,
            ) { isGridCustomizationAvailable, isThemedIconAvailable, isShapeOptionsAvailable ->
                ThemePickerCustomizationOptionsData(
                    isGridCustomizationAvailable = isGridCustomizationAvailable,
                    isIconStyleAvailable = isThemedIconAvailable,
                    isShapeAvailable = isShapeOptionsAvailable,
                    isColorCustomizationAvailable = !ActivityUtils.isSUWMode(appContext),
                )
            }
        }

    private var onApplyJob: Job? = null

    override val selectedOption = defaultCustomizationOptionsViewModel.selectedOption

    /**
     * The home screen preview workspace rotates every 3 seconds when the user is in the color
     * picker. Otherwise only the Launcher workspace is shown.
     */
    val workspacePreviewScreen =
        selectedOption.flatMapLatest {
            if (
                it == ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.COLORS
            ) {
                flow {
                    val screenList = WorkspacePreviewScreen.entries
                    var idx = 0
                    emit(screenList[idx])
                    while (true) {
                        delay(3000.milliseconds)
                        idx = (idx + 1) % screenList.size
                        emit(screenList[idx])
                    }
                }
            } else {
                flowOf(WorkspacePreviewScreen.LAUNCHER)
            }
        }

    override val discardChangesDialogViewModel =
        defaultCustomizationOptionsViewModel.discardChangesDialogViewModel

    override fun handleBackPressed(): Boolean {
        // Note we need to check isApplyInProgress since applyButtonState is a state flow that the
        // downstream value is calculated asynchronously and may not be up to date.
        if (applyButtonState.value == APPLY_BUTTON_ENABLED && !isApplyInProgress.value) {
            defaultCustomizationOptionsViewModel.showDiscardChangesDialogViewModel(
                // Hide the picker's clock when we start the transition back to the primary screen.
                onDiscard = { clockPickerViewModel.setShowPickerClockControllerView(false) }
            )
            return true
        }
        // Hide the picker's clock when we start the transition back to the primary screen.
        clockPickerViewModel.setShowPickerClockControllerView(false)
        return defaultCustomizationOptionsViewModel.handleBackPressed()
    }

    override fun resetPreview() {
        defaultCustomizationOptionsViewModel.resetPreview()

        keyguardQuickAffordancePickerViewModel2.resetPreview()
        gridPickerViewModel.resetPreview()
        if (BaseFlags.get(appContext).isExtendibleThemeManager()) {
            appIconPickerViewModel.resetPreview2()
        } else {
            appIconPickerViewModel.resetPreview()
        }
        clockPickerViewModel.resetPreview()
        // resetPreview happens when transition back to the primary screen ends. Show the keyguard
        // preview renderer's smartspace and the clock.
        clockPickerViewModel.setShowKeyguardPreviewRendererSmartspace(true)
        colorPickerViewModel2.resetPreview()
        darkModeViewModel.resetPreview()
    }

    override fun onTransitionToSecondaryScreenComplete() {
        defaultCustomizationOptionsViewModel.onTransitionToSecondaryScreenComplete()
        if (selectedOption.value == CLOCK) {
            // Show the picker's clock when we complete the transition to land on the secondary
            // clock customization screen.
            clockPickerViewModel.setShowPickerClockControllerView(true)
        }
    }

    override fun refetchThemeInfo() {
        if (BaseFlags.get(appContext).isPackThemeEnabled()) {
            packThemeViewModel.refetchPackTheme()
        }
    }

    val onCustomizeClockClicked: Flow<(() -> Unit)?> =
        selectedOption.map {
            if (it == null) {
                {
                    defaultCustomizationOptionsViewModel.selectOption(CLOCK)
                    // When we are about to transition to the clock customization screen, hide the
                    // keyguard preview renderer's smartspace as well as the clock. Because, we will
                    // show the picker's clock controller view clock when the transition ends.
                    // Please also see clockPickerViewModel.setShowPickerClockControllerView().
                    clockPickerViewModel.setShowKeyguardPreviewRendererSmartspace(false)
                }
            } else {
                null
            }
        }

    val onCustomizeShortcutClicked: Flow<(() -> Unit)?> =
        selectedOption.map {
            if (it == null) {
                { defaultCustomizationOptionsViewModel.selectOption(SHORTCUTS) }
            } else {
                null
            }
        }

    val onCustomizeColorsClicked: Flow<(() -> Unit)?> =
        selectedOption.map {
            if (it == null) {
                { defaultCustomizationOptionsViewModel.selectOption(COLORS) }
            } else {
                null
            }
        }

    val onCustomizeIconsClicked: Flow<(() -> Unit)?> =
        selectedOption.map {
            if (it == null) {
                { defaultCustomizationOptionsViewModel.selectOption(APP_ICONS) }
            } else {
                null
            }
        }

    val onCustomizeShapeGridClicked: Flow<(() -> Unit)?> =
        selectedOption.map {
            if (it == null) {
                { defaultCustomizationOptionsViewModel.selectOption(GRID) }
            } else {
                null
            }
        }
    private val isApplyInProgress: MutableStateFlow<Boolean> = MutableStateFlow(false)
    @OptIn(ExperimentalCoroutinesApi::class)
    val onApplyButtonClicked: Flow<((onComplete: () -> Unit) -> Unit)?> =
        selectedOption
            .flatMapLatest {
                when (it) {
                    CLOCK -> clockPickerViewModel.onApply
                    SHORTCUTS -> keyguardQuickAffordancePickerViewModel2.onApply
                    GRID -> gridPickerViewModel.onApply
                    APP_ICONS ->
                        if (BaseFlags.get(appContext).isExtendibleThemeManager()) {
                            appIconPickerViewModel.iconStyleAndShapeOnApply
                        } else {
                            appIconPickerViewModel.shapeAndThemedIconOnApply
                        }
                    COLORS ->
                        combine(colorPickerViewModel2.onApply, darkModeViewModel.onApply) {
                            colorOnApply,
                            darkModeOnApply ->
                            if (colorOnApply == null && darkModeOnApply == null) {
                                null
                            } else {
                                {
                                    colorOnApply?.invoke()
                                    darkModeOnApply?.invoke()
                                }
                            }
                        }
                    else -> flow { emit(null) }
                }
            }
            .map { onApply ->
                if (onApply != null) {
                    fun(onComplete: () -> Unit) {
                        // Prevent double apply
                        if (onApplyJob?.isActive != true) {
                            onApplyJob =
                                viewModelScope.launch {
                                    isApplyInProgress.value = true
                                    onApply()
                                    onComplete()
                                    isApplyInProgress.value = false
                                    onApplyJob = null
                                }
                        }
                    }
                } else {
                    null
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val applyButtonState: StateFlow<ApplyButton.ApplyButtonState> =
        combine(isApplyInProgress, onApplyButtonClicked) { isApplyInProgress, onApplyButtonClicked
                ->
                if (isApplyInProgress) {
                    APPLY_BUTTON_IN_PROGRESS
                } else if (onApplyButtonClicked == null) {
                    APPLY_BUTTON_DISABLED
                } else {
                    APPLY_BUTTON_ENABLED
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), APPLY_BUTTON_DISABLED)

    val isApplyButtonVisible: Flow<Boolean> = selectedOption.map { it != null }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return AccessibilityUtil.isAccessibilityEnabled(
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        )
    }

    val shouldShowToolbar =
        combine(selectedOption, colorPickerViewModel2.currentScreen) {
            selectedOption,
            colorPickerScreen ->
            val isInDrillDown =
                selectedOption == COLORS &&
                    (colorPickerScreen == ColorPickerViewModel.Screen.VARIANT_PICKER ||
                        colorPickerScreen == ColorPickerViewModel.Screen.FREEFORM_PICKER)
            !isInDrillDown
        }

    @ViewModelScoped
    @AssistedFactory
    interface Factory : CustomizationOptionsViewModelFactory {
        override fun create(
            viewModelScope: CoroutineScope,
            @Assisted("destination") initialDeepLinkDestination: String?,
            @Assisted("shortcutSlotId") initialDeepLinkShortcutSlotId: String?,
        ): ThemePickerCustomizationOptionsViewModel
    }
}
