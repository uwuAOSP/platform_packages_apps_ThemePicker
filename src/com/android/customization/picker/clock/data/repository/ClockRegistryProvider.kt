/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.customization.picker.clock.data.repository

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.view.LayoutInflater
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DefaultClockProvider
import com.android.systemui.shared.plugins.PluginActionManager
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import com.android.systemui.shared.plugins.PluginInstance
import com.android.systemui.shared.plugins.PluginManagerImpl
import com.android.systemui.shared.plugins.PluginPrefs
import com.android.systemui.shared.plugins.VersionCheckerImpl
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager_Factory
import com.android.wallpaper.config.BaseFlags
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Provide the [ClockRegistry] singleton. Note that we need to make sure that the [PluginManager]
 * needs to be connected before [ClockRegistry] is ready to use.
 */
class ClockRegistryProvider(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val clockRegistry: ClockRegistry by lazy {
        val flags = BaseFlags.get()
        ClockRegistry(
            context,
            createPluginManager(context),
            coroutineScope,
            mainDispatcher,
            backgroundDispatcher,
            isEnabled = flags.isCustomClocksEnabled(context),
            handleAllUsers = false,
            DefaultClockProvider(
                layoutInflater = LayoutInflater.from(context),
                resources = context.resources,
                isClockReactiveVariantsEnabled = flags.isClockReactiveVariantsEnabled(),
                vibrator = null,
            ),
            keepAllLoaded = true,
            subTag = "Picker",
        )
    }

    init {
        // Listeners in ClockRegistry get cleaned up when app ended
        clockRegistry.registerListeners()
    }

    fun get() = clockRegistry

    private fun createPluginManager(context: Context): PluginManager {
        val pluginConfig = PluginManager.Config(getPrivilegedPluginPackages(context))

        val instanceFactory =
            PluginInstance.Factory(
                VersionCheckerImpl(),
                this::class.java.classLoader!!,
                pluginConfig,
            )

        /*
         * let SystemUI handle plugin, in this class assume plugins are enabled
         */
        val pluginEnabler =
            object : PluginEnabler {
                override fun setEnabled(component: ComponentName) = Unit

                override fun setDisabled(component: ComponentName, reason: DisableReason) = Unit

                override fun isEnabled(component: ComponentName): Boolean {
                    return true
                }

                override fun getDisableReason(componentName: ComponentName): DisableReason {
                    return DisableReason.ENABLED
                }
            }

        val pluginActionManager =
            PluginActionManager.Factory(
                context,
                context.packageManager,
                context.mainExecutor,
                Executors.newSingleThreadExecutor(),
                context.getSystemService(NotificationManager::class.java),
                pluginEnabler,
                pluginConfig,
                instanceFactory,
            )
        return PluginManagerImpl(
            context,
            pluginActionManager,
            UncaughtExceptionPreHandlerManager_Factory.create().get(),
            pluginEnabler,
            PluginPrefs(context),
            pluginConfig,
        )
    }

    private fun getPrivilegedPluginPackages(context: Context): List<String> {
        val resId = context.resources.getIdentifier("config_pluginAllowlist", "array", "android")
        if (resId != 0) {
            return context.resources.getStringArray(resId).toList()
        }

        return listOf(
            "com.android.systemui",
            "com.android.systemui.clocks.bignum",
            "com.android.systemui.clocks.calligraphy",
            "com.android.systemui.clocks.flex",
            "com.android.systemui.clocks.growth",
            "com.android.systemui.clocks.handwritten",
            "com.android.systemui.clocks.inflate",
            "com.android.systemui.clocks.metro",
            "com.android.systemui.clocks.numoverlap",
            "org.uwuaosp.clock",
            "com.android.systemui.clocks.weather",
        )
    }
}
