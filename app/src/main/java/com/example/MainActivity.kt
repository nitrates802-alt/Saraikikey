package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.preferences.KeyboardThemePreset
import com.example.feature.settings.AiAssistantScreen
import com.example.feature.settings.DictionariesScreen
import com.example.feature.settings.GlideAndClipboardScreen
import com.example.feature.settings.LanguagesScreen
import com.example.feature.settings.PreferencesScreen
import com.example.feature.settings.PrivacyBackupScreen
import com.example.feature.settings.SettingsHomeScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.settings.SetupWizardScreen
import com.example.feature.settings.SoundHapticsScreen
import com.example.feature.settings.ThemesAndResizeScreen
import com.example.ui.theme.SaraikiProTheme

object AppRoutes {
    const val ROUTE_HOME = "settings_home"
    const val ROUTE_WIZARD = "setup_wizard"
    const val ROUTE_LANGUAGES = "languages"
    const val ROUTE_PREFERENCES = "preferences"
    const val ROUTE_THEMES = "themes_resize"
    const val ROUTE_SOUND_HAPTICS = "sound_haptics"
    const val ROUTE_DICTIONARIES = "dictionaries"
    const val ROUTE_GLIDE_CLIPBOARD = "glide_clipboard"
    const val ROUTE_AI_ASSISTANT = "ai_assistant"
    const val ROUTE_PRIVACY = "privacy_backup"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            val useDark = settings.themePreset != KeyboardThemePreset.CLEAN_DAYLIGHT
            val useDynamic = settings.themePreset == KeyboardThemePreset.MATERIAL_YOU

            SaraikiProTheme(darkTheme = useDark, dynamicColor = useDynamic) {
                val navController = rememberNavController()
                val imeStatus by viewModel.imeStatus.collectAsStateWithLifecycle()
                val startDestination = if (imeStatus.isEnabledInSystem) {
                    AppRoutes.ROUTE_HOME
                } else {
                    AppRoutes.ROUTE_WIZARD
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(AppRoutes.ROUTE_HOME) {
                        SettingsHomeScreen(
                            viewModel = viewModel,
                            onOpenWizard = { navController.navigate(AppRoutes.ROUTE_WIZARD) },
                            onOpenLanguages = { navController.navigate(AppRoutes.ROUTE_LANGUAGES) },
                            onOpenPreferences = { navController.navigate(AppRoutes.ROUTE_PREFERENCES) },
                            onOpenThemes = { navController.navigate(AppRoutes.ROUTE_THEMES) },
                            onOpenSoundHaptics = { navController.navigate(AppRoutes.ROUTE_SOUND_HAPTICS) },
                            onOpenDictionaries = { navController.navigate(AppRoutes.ROUTE_DICTIONARIES) },
                            onOpenGlideClipboard = { navController.navigate(AppRoutes.ROUTE_GLIDE_CLIPBOARD) },
                            onOpenAiAssistant = { navController.navigate(AppRoutes.ROUTE_AI_ASSISTANT) },
                            onOpenPrivacy = { navController.navigate(AppRoutes.ROUTE_PRIVACY) }
                        )
                    }
                    composable(AppRoutes.ROUTE_WIZARD) {
                        SetupWizardScreen(
                            viewModel = viewModel,
                            onOpenSettingsHome = {
                                navController.navigate(AppRoutes.ROUTE_HOME) {
                                    popUpTo(AppRoutes.ROUTE_WIZARD) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onBack = if (navController.previousBackStackEntry != null) {
                                { navController.popBackStack() }
                            } else null
                        )
                    }
                    composable(AppRoutes.ROUTE_LANGUAGES) {
                        LanguagesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_PREFERENCES) {
                        PreferencesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_THEMES) {
                        ThemesAndResizeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_SOUND_HAPTICS) {
                        SoundHapticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_DICTIONARIES) {
                        DictionariesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_GLIDE_CLIPBOARD) {
                        GlideAndClipboardScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_AI_ASSISTANT) {
                        AiAssistantScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.ROUTE_PRIVACY) {
                        PrivacyBackupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
