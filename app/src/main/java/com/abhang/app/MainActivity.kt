package com.abhang.app

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.abhang.app.data.AbhangRepository
import com.abhang.app.data.ReadingStore
import com.abhang.app.ui.FavoritesScreen
import com.abhang.app.ui.AboutScreen
import com.abhang.app.ui.AbhangTheme
import com.abhang.app.ui.CategoryScreen
import com.abhang.app.ui.DetailScreen
import com.abhang.app.ui.HomeScreen
import com.abhang.app.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Both system bars sit over our bars: burnt saffron (light) or dark brown (dark), so status icons stay light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val store = ReadingStore(applicationContext)
        setContent {
            AbhangTheme {
                var loaded by remember { mutableStateOf<AbhangRepository?>(null) }
                LaunchedEffect(Unit) { loaded = withContext(Dispatchers.IO) { AbhangRepository.load(applicationContext) } }
                // Build the search indexes now, off the main thread, so the first query is instant.
                LaunchedEffect(loaded) { loaded?.let { r -> withContext(Dispatchers.Default) { r.warmUp() } } }
                val repo = loaded
                if (repo == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading), Modifier.padding(top = 12.dp))
                        }
                    }
                    return@AbhangTheme
                }
                val nav = rememberNavController()
                val intArg = { name: String -> navArgument(name) { type = NavType.IntType } }
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            repo, store,
                            onCategory = { nav.navigate("category/$it") },
                            onSearch = { nav.navigate("search") },
                            onAbout = { nav.navigate("about") },
                            onFavorites = { nav.navigate("favorites") },
                            onAbhang = { id -> nav.navigate("detail/$id") },
                        )
                    }
                    composable("category/{id}", listOf(intArg("id"))) {
                        CategoryScreen(
                            repo, it.arguments!!.getInt("id"),
                            onBack = { nav.popBackStack() },
                            onAbhang = { id -> nav.navigate("detail/$id") },
                        )
                    }
                    composable("detail/{id}", listOf(intArg("id"))) {
                        DetailScreen(
                            repo, store, it.arguments!!.getInt("id"),
                            onBack = { nav.popBackStack() },
                            onAbout = { nav.navigate("about") },
                        )
                    }
                    composable("favorites") {
                        FavoritesScreen(repo, store, onBack = { nav.popBackStack() }, onAbhang = { id -> nav.navigate("detail/$id") })
                    }
                    composable("search") {
                        SearchScreen(repo, onBack = { nav.popBackStack() }, onAbhang = { id -> nav.navigate("detail/$id") })
                    }
                    composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
