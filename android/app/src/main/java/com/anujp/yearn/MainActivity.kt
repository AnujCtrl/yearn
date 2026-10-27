package com.anujp.yearn

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujp.yearn.YearnApp.Companion.yearnGraph
import com.anujp.yearn.ui.YearnRoot
import com.anujp.yearn.ui.detail.DetailViewModel
import com.anujp.yearn.ui.home.HomeViewModel
import com.anujp.yearn.ui.settings.SettingsViewModel
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val graph = yearnGraph
                HomeViewModel(graph.dao, graph.scheduler, graph.settings.lastSuccessfulSync) { ZonedDateTime.now() }
            }
        }
    }

    private val settings: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val graph = yearnGraph
                // The application context: the view model outlives this activity across rotations and folds.
                val app = applicationContext
                val power = app.getSystemService(PowerManager::class.java)
                SettingsViewModel(
                    graph.settings,
                    graph.dao,
                    graph.clientFactory,
                    power = { power?.isIgnoringBatteryOptimizations(app.packageName) ?: true },
                    scheduler = graph.scheduler,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YearnRoot(home, settings) { id ->
                // One per save, kept for the activity's life so a note save started on leaving still finishes.
                viewModel(key = "detail:$id") {
                    val graph = yearnGraph
                    DetailViewModel(id, graph.dao, graph.service)
                }
            }
        }
    }
}
