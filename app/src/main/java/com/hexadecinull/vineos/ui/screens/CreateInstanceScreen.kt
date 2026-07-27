package com.hexadecinull.vineos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexadecinull.vineos.ui.viewmodel.CreateInstanceState
import com.hexadecinull.vineos.ui.viewmodel.CreateInstanceViewModel

private val emojiChoices = listOf("\uD83D\uDFE2", "\uD83D\uDD35", "\uD83D\uDFE1", "\uD83D\uDD34", "\uD83D\uDFE3", "\uD83E\uDDE1", "\u26AA", "\u26AB")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInstanceScreen(
    romId: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: CreateInstanceViewModel = hiltViewModel(),
) {
    val rom by vm.rom.collectAsStateWithLifecycle()
    val name by vm.instanceName.collectAsStateWithLifecycle()
    val ram by vm.selectedRamMb.collectAsStateWithLifecycle()
    val storage by vm.selectedStorageMb.collectAsStateWithLifecycle()
    val emoji by vm.selectedEmoji.collectAsStateWithLifecycle()
    val isFormValid by vm.isFormValid.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(romId) { vm.loadRom(romId) }
    LaunchedEffect(state) {
        val s = state
        if (s is CreateInstanceState.Success) onCreated(s.instanceId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Instance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val currentRom = rom
        if (currentRom == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Based on ${currentRom.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { vm.instanceName.value = it },
                label = { Text("Instance name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(emojiChoices) { choice ->
                        val selected = choice == emoji
                        Surface(
                            onClick = { vm.selectedEmoji.value = choice },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(choice, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }

            SliderSetting(
                label = "RAM",
                value = ram,
                valueLabel = "$ram MB",
                range = 512f..4096f,
                step = 512,
                onChange = { vm.selectedRamMb.value = it },
            )

            SliderSetting(
                label = "Storage",
                value = storage,
                valueLabel = "$storage MB",
                range = 2048f..16384f,
                step = 1024,
                onChange = { vm.selectedStorageMb.value = it },
            )

            if (state is CreateInstanceState.Error) {
                Text(
                    (state as CreateInstanceState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { vm.createInstance(currentRom) },
                enabled = isFormValid && state !is CreateInstanceState.Creating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state is CreateInstanceState.Creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Create Instance")
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
        )
    }
}
