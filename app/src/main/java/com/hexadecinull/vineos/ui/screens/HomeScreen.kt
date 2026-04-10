package com.hexadecinull.vineos.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.ui.components.InstanceCard

// ─── Home Screen ──────────────────────────────────────────────────────────────

/**
 * Main screen listing all VM instances.
 * Shows an empty state with instructions when no instances exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    instances: List<VMInstance>,
    onLaunchInstance: (VMInstance) -> Unit,
    onStopInstance: (VMInstance) -> Unit,
    onInstanceDetail: (VMInstance) -> Unit,
    onDeleteInstance: (VMInstance) -> Unit,
    onCreateInstance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Instances") },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateInstance,
                icon = {
                    Icon(Icons.Filled.Add, contentDescription = null)
                },
                text = { Text("New Instance") },
            )
        },
        modifier = modifier
    ) { innerPadding ->
        AnimatedContent(
            targetState = instances.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "homeContent"
        ) { isEmpty ->
            if (isEmpty) {
                HomeEmptyState(
                    onCreateInstance = onCreateInstance,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp  // FAB clearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = instances,
                        key = { it.id }
                    ) { instance ->
                        InstanceCard(
                            instance = instance,
                            onLaunchClick = onLaunchInstance,
                            onStopClick = onStopInstance,
                            onCardClick = onInstanceDetail,
                            onDeleteClick = onDeleteInstance,
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyState(
    onCreateInstance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No instances yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create a virtual Android instance to get started. Download a ROM first if you haven't already.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreateInstance) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create Instance")
        }
    }
}
