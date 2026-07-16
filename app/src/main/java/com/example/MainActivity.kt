package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.LightTextSecondary

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

      // Calculate dynamic system and user preferred themes
      val darkTheme = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme() // "Follow System"
      }

      MyApplicationTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: "home"

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .testTag("app_bottom_bar"),
              contentAlignment = Alignment.BottomCenter
            ) {
              // 1. Bottom bar background surface
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(80.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                  horizontalArrangement = Arrangement.SpaceAround,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // Tab 1: Home
                  IconButton(
                    onClick = {
                      if (currentRoute != "home") {
                        navController.navigate("home") {
                          popUpTo("home") { saveState = true }
                          launchSingleTop = true
                          restoreState = true
                        }
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("nav_home_tab")
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(
                        imageVector = if (currentRoute == "home") Icons.Default.Home else Icons.Outlined.Home,
                        contentDescription = "Home tab icon",
                        tint = if (currentRoute == "home") PrimaryBlue else LightTextSecondary,
                        modifier = Modifier.size(24.dp)
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = "Home",
                        fontSize = 10.sp,
                        color = if (currentRoute == "home") PrimaryBlue else LightTextSecondary,
                        fontWeight = if (currentRoute == "home") FontWeight.Bold else FontWeight.Medium
                      )
                    }
                  }

                  // Spacer to make room for center floating button
                  Spacer(modifier = Modifier.weight(1f))

                  // Tab 3: Settings
                  IconButton(
                    onClick = {
                      if (currentRoute != "settings") {
                        navController.navigate("settings") {
                          popUpTo("home") { saveState = true }
                          launchSingleTop = true
                          restoreState = true
                        }
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("nav_settings_tab")
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(
                        imageVector = if (currentRoute == "settings") Icons.Default.Settings else Icons.Outlined.Settings,
                        contentDescription = "Settings tab icon",
                        tint = if (currentRoute == "settings") PrimaryBlue else LightTextSecondary,
                        modifier = Modifier.size(24.dp)
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = "Settings",
                        fontSize = 10.sp,
                        color = if (currentRoute == "settings") PrimaryBlue else LightTextSecondary,
                        fontWeight = if (currentRoute == "settings") FontWeight.Bold else FontWeight.Medium
                      )
                    }
                  }
                }
              }

              // 2. Centered Voice/Mic floating action button elevated above the bar
              FloatingActionButton(
                onClick = {
                  if (currentRoute != "voice_translate") {
                    navController.navigate("voice_translate") {
                      launchSingleTop = true
                    }
                  }
                },
                shape = CircleShape,
                containerColor = PrimaryBlue,
                elevation = FloatingActionButtonDefaults.elevation(
                  defaultElevation = 8.dp,
                  pressedElevation = 12.dp
                ),
                modifier = Modifier
                  .align(Alignment.TopCenter)
                  .size(62.dp)
                  .testTag("nav_voice_tab")
              ) {
                Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = "Center voice tab mic icon",
                  tint = Color.White,
                  modifier = Modifier.size(28.dp)
                )
              }
            }
          }
        ) { innerPadding ->
          // Secure NavHost filling maximum space with correct bottom/top insets
          NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(bottom = innerPadding.calculateBottomPadding())
              .testTag("app_nav_host")
          ) {
            composable("home") {
              HomeScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
              )
            }

            composable("translate_text") {
              TranslateTextScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("translation_result") {
              TranslationResultScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("scan_translate") {
              ScanAndTranslateScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("ocr_result") {
              OCRResultScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("voice_translate") {
              VoiceTranslationScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("history") {
              HistoryScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }

            composable("settings") {
              SettingsScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
              )
            }
          }
        }

        val downloadConfirm by viewModel.activeDownloadConfirm.collectAsStateWithLifecycle()
        if (downloadConfirm != null) {
          val dc = downloadConfirm!!
          AlertDialog(
            onDismissRequest = { viewModel.activeDownloadConfirm.value = null },
            title = {
              Text(
                text = "Download Required",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            },
            text = {
              Text(
                text = "${dc.langName} ${dc.type} is not installed on this device. Download now?\nSize: ~${dc.sizeMb} MB",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            },
            confirmButton = {
              Button(
                onClick = { viewModel.startDownloadResource(dc) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
              ) {
                Text("Download", color = Color.White, fontWeight = FontWeight.Bold)
              }
            },
            dismissButton = {
              TextButton(onClick = { viewModel.activeDownloadConfirm.value = null }) {
                Text("Cancel", color = LightTextSecondary)
              }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
          )
        }

        val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadingLangName by viewModel.downloadingLangName.collectAsStateWithLifecycle()
        val downloadingType by viewModel.downloadingType.collectAsStateWithLifecycle()
        
        if (isDownloading) {
          AlertDialog(
            onDismissRequest = {}, // Cannot dismiss while downloading
            title = {
              Text(
                text = "Downloading...",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            },
            text = {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "Downloading $downloadingLangName $downloadingType. Please wait.",
                  fontSize = 14.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LinearProgressIndicator(
                  progress = { downloadProgress },
                  modifier = Modifier.fillMaxWidth().height(8.dp),
                  color = PrimaryBlue,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
                  strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                  text = "${(downloadProgress * 100).toInt()}% Completed",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = PrimaryBlue
                )
              }
            },
            confirmButton = {},
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
          )
        }
      }
    }
  }
}

