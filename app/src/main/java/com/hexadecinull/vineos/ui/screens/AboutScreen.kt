package com.hexadecinull.vineos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hexadecinull.vineos.ui.viewmodel.AboutInfoSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(sections: List<AboutInfoSection>, onBack: () -> Unit, onLicenses: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("About VineOS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            sections.forEach { section ->
                SettingsSection(title = section.title) {
                    section.items.forEach { item ->
                        TextInfoRow(label = item.label, value = item.value)
                    }
                }
            }

            SettingsSection(title = "Open Source") {
                NavigateSettingsItem(
                    icon = Icons.Outlined.Article,
                    title = "Licenses",
                    subtitle = "Third-party libraries bundled with VineOS",
                    onClick = onLicenses,
                )
            }
        }
    }
}

@Composable
private fun TextInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
    }
}
