package com.anujp.yearn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujp.yearn.YearnApp.Companion.yearnGraph
import com.anujp.yearn.ui.YearnRoot
import com.anujp.yearn.ui.home.HomeViewModel
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val graph = yearnGraph
                HomeViewModel(graph.dao, graph.scheduler) { ZonedDateTime.now() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { YearnRoot(home) }
    }
}
