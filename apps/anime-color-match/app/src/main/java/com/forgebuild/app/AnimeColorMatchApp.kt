package com.forgebuild.app

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.theme.SpacingTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeColorMatchApp() {
    var activeTab by remember { mutableStateOf(1) } // 0: Ingest, 1: Auto Progression, 2: Manual Stack, 3: Export
    var precision by remember { mutableStateOf(LUTPrecision.STANDARD) }

    // Reference & Target Bitmaps
    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var targetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gradedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Initialize with sample anime frames
    LaunchedEffect(Unit) {
        val ref = ColorEngine.createSampleAnimeReferenceBitmap()
        val target = ColorEngine.createSampleAnimeTargetBitmap()
        refBitmap = ref
        targetBitmap = target
    }

    // Effect Layers Stack
    var layers by remember {
        mutableStateOf(
            listOf(
                EffectLayer(
                    id = "auto_match_base",
                    name = "Auto Match Base",
                    type = EffectType.AUTO_MATCH,
                    enabled = true,
                    opacity = 100,
                    autoMatchData = AutoMatchData(
                        blackShift = Triple(8, 12, 24),
                        whiteShift = Triple(1.14f, 0.98f, 1.05f),
                        midGamma = Triple(1.22f, 0.96f, 1.10f),
                        saturationShift = 0.22f,
                        hueShift = 12.4f,
                        contrast = 1.18f,
                        activeStage = 6
                    )
                ),
                EffectLayer(
                    id = "levels_layer_1",
                    name = "AE Levels 1",
                    type = EffectType.LEVELS,
                    enabled = true,
                    opacity = 100,
                    levelsData = LevelsData(
                        rgb = LevelsChannelConfig(0, 255, 1.0f, 0, 255),
                        red = LevelsChannelConfig(0, 255, 1.05f, 0, 255),
                        green = LevelsChannelConfig(0, 255, 0.98f, 0, 255),
                        blue = LevelsChannelConfig(0, 255, 1.12f, 5, 255)
                    )
                )
            )
        )
    }
    var activeLayerId by remember { mutableStateOf("levels_layer_1") }

    // Update graded preview when layers change
    LaunchedEffect(layers, targetBitmap) {
        val src = targetBitmap
        if (src != null) {
            gradedBitmap = ColorEngine.applyColorGradeToBitmap(src, layers)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ChromaMatch Anime",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("AMV CC Studio", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    FilledTonalButton(
                        onClick = {
                            refBitmap = ColorEngine.createSampleAnimeReferenceBitmap()
                            targetBitmap = ColorEngine.createSampleAnimeTargetBitmap()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset Sample", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                    label = { Text("1. Ingest") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("2. Auto Match") }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("3. AE Stack") }
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    label = { Text("4. Export LUT") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (activeTab) {
                0 -> IngestTab(
                    refBitmap = refBitmap,
                    targetBitmap = targetBitmap,
                    onSetRef = { refBitmap = it },
                    onSetTarget = { targetBitmap = it },
                    onProceed = { activeTab = 1 }
                )
                1 -> AutoProgressionTab(
                    refBitmap = refBitmap,
                    targetBitmap = targetBitmap,
                    onApply = { autoData ->
                        val autoLayer = EffectLayer(
                            id = "auto_match_${System.currentTimeMillis()}",
                            name = "Auto Match",
                            type = EffectType.AUTO_MATCH,
                            autoMatchData = autoData
                        )
                        layers = listOf(autoLayer) + layers.filter { it.type != EffectType.AUTO_MATCH }
                        activeTab = 2
                    }
                )
                2 -> ManualStackTab(
                    layers = layers,
                    activeLayerId = activeLayerId,
                    onSelectLayer = { activeLayerId = it },
                    onUpdateLayers = { layers = it },
                    refBitmap = refBitmap,
                    targetBitmap = targetBitmap
                )
                3 -> ExportTab(
                    layers = layers,
                    precision = precision,
                    onPrecisionChange = { precision = it }
                )
            }
        }
    }
}

@Composable
fun IngestTab(
    refBitmap: Bitmap?,
    targetBitmap: Bitmap?,
    onSetRef: (Bitmap) -> Unit,
    onSetTarget: (Bitmap) -> Unit,
    onProceed: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Direct Image Import & Video Registration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select reference anime frame (with target color grade) and target uncorrected footage/image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reference Frame Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1. Reference Frame (Goal CC)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSetRef(ColorEngine.createSampleAnimeReferenceBitmap()) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Import Image", style = MaterialTheme.typography.labelSmall)
                    }
                }
                refBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Reference Frame",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
            }
        }

        // Target Frame Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2. Target Frame (Raw Footage)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSetTarget(ColorEngine.createSampleAnimeTargetBitmap()) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Import Image", style = MaterialTheme.typography.labelSmall)
                    }
                }
                targetBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Target Frame",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
            }
        }

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Proceed to Automated Color Matching")
        }
    }
}

@Composable
fun AutoProgressionTab(
    refBitmap: Bitmap?,
    targetBitmap: Bitmap?,
    onApply: (AutoMatchData) -> Unit
) {
    var activeStage by remember { mutableStateOf(1) }
    var isRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var logs by remember {
        mutableStateOf(
            listOf(
                ProgressionLog("1", "00:01.12", 1, "INFO", "Initialized color engine with Reference & Target still frames."),
                ProgressionLog("2", "00:01.35", 1, "ANALYZE", "Evaluating 256-bin RGB cumulative distribution functions...")
            )
        )
    }

    val stages = remember {
        listOf(
            WorkflowStage(1, "Spatial & Luma Registration", "1. Luma", "Calculates overlapping ROI and baseline luma delta.", mapOf("ROI" to "100%", "Luma" to "-14.2 cd/m²"), 800),
            WorkflowStage(2, "Black Point & Pedestal", "2. Pedestal", "Lifts R/G/B shadow endpoints to reproduce anime deep navy shadow tint.", mapOf("Shadow Tint" to "Navy Indigo", "Shift" to "+8R, +11G, +24B"), 900),
            WorkflowStage(3, "White Point & Gain", "3. Gain", "Balances specular highlights and warm/cool white roll-off.", mapOf("Gain Factor" to "1.14x R, 0.98x G", "Roll-off" to "Soft Cine"), 900),
            WorkflowStage(4, "Midtone Gamma & S-Curve", "4. Gamma", "Fits CDF percentiles to power curve for cel shading depth.", mapOf("Mid Gamma" to "1.22R, 0.96G", "Contrast" to "+18%"), 1000),
            WorkflowStage(5, "Hue & Chrominance", "5. Chroma", "Shifts chroma vectors in LAB/HSV space for iconic anime palettes.", mapOf("Hue Rotation" to "+12.4°", "Sat Boost" to "+22%"), 1000),
            WorkflowStage(6, "3D LUT Lattice Bake", "6. 3D Bake", "Bakes nonlinear color transformation into tetrahedral 3D LUT.", mapOf("Lattice" to "33x33x33 nodes", "Residual ΔE" to "1.14"), 800)
        )
    }

    // Step progression animation loop
    LaunchedEffect(isRunning, activeStage) {
        if (isRunning && activeStage < 6) {
            delay(1000)
            activeStage++
            val s = stages[activeStage - 1]
            logs = logs + ProgressionLog(
                id = System.currentTimeMillis().toString(),
                timestamp = "00:0${activeStage + 1}.42",
                stageId = activeStage,
                level = "TRANSFORM",
                message = "Executing Stage $activeStage: ${s.name} (${s.description})"
            )
            if (activeStage == 6) {
                isRunning = false
                logs = logs + ProgressionLog(
                    id = System.currentTimeMillis().toString(),
                    timestamp = "00:07.10",
                    stageId = 6,
                    level = "SUCCESS",
                    message = "Color migration solved! 3D tetrahedral LUT lattice converged."
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Controls Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Automated Workflow Progression", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Stage $activeStage of 6: ${stages[activeStage - 1].shortName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (!isRunning) {
                                activeStage = 1
                                isRunning = true
                            } else {
                                isRunning = false
                            }
                        }
                    ) {
                        Text(if (isRunning) "Pause" else "Play Live")
                    }
                    Button(
                        onClick = {
                            onApply(
                                AutoMatchData(
                                    blackShift = Triple(8, 12, 24),
                                    whiteShift = Triple(1.14f, 0.98f, 1.05f),
                                    midGamma = Triple(1.22f, 0.96f, 1.10f),
                                    saturationShift = 0.22f,
                                    hueShift = 12.4f,
                                    contrast = 1.18f,
                                    activeStage = 6
                                )
                            )
                        }
                    ) {
                        Text("Apply & Refine")
                    }
                }
            }
        }

        // Live Evolving Frame Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targetBitmap?.let { src ->
                    val preview = remember(activeStage, src) {
                        val mockLayer = listOf(
                            EffectLayer(
                                id = "prog_preview",
                                name = "Progression",
                                type = EffectType.AUTO_MATCH,
                                autoMatchData = AutoMatchData(
                                    blackShift = Triple(8, 12, 24),
                                    whiteShift = Triple(1.14f, 0.98f, 1.05f),
                                    midGamma = Triple(1.22f, 0.96f, 1.10f),
                                    saturationShift = 0.22f,
                                    hueShift = 12.4f,
                                    contrast = 1.18f,
                                    activeStage = activeStage
                                )
                            )
                        )
                        ColorEngine.applyColorGradeToBitmap(src, mockLayer, stageLimit = activeStage)
                    }
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Active Stage Frame",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
            }
        }

        // Live Terminal / Logs Console
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(12.dp)
            ) {
                Text(
                    "ENGINE PROGRESSION LOGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(logs) { log ->
                        Text(
                            "[${log.timestamp}] [${log.level}] S0${log.stageId}: ${log.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (log.level) {
                                "SUCCESS" -> MaterialTheme.colorScheme.primary
                                "TRANSFORM" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManualStackTab(
    layers: List<EffectLayer>,
    activeLayerId: String,
    onSelectLayer: (String) -> Unit,
    onUpdateLayers: (List<EffectLayer>) -> Unit,
    refBitmap: Bitmap?,
    targetBitmap: Bitmap?
) {
    var activeChannel by remember { mutableStateOf(ChannelType.RGB) }
    var viewChannel by remember { mutableStateOf(ViewChannel.RGB) }
    val activeLayer = layers.find { it.id == activeLayerId } ?: layers.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Channel View Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VIEW ISOLATION:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ViewChannel.values().forEach { ch ->
                    FilterChip(
                        selected = viewChannel == ch,
                        onClick = { viewChannel = ch },
                        label = { Text(ch.name) }
                    )
                }
            }
        }

        // Live Result
        targetBitmap?.let { src ->
            val preview = remember(layers, viewChannel, src) {
                ColorEngine.applyColorGradeToBitmap(src, layers, viewChannel = viewChannel)
            }
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Manual Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        }

        // Effect Stack List
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CORRECTION STACK (${layers.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val newLevels = EffectLayer(
                                    id = "levels_${System.currentTimeMillis()}",
                                    name = "Levels ${layers.count { it.type == EffectType.LEVELS } + 1}",
                                    type = EffectType.LEVELS,
                                    levelsData = LevelsData()
                                )
                                onUpdateLayers(layers + newLevels)
                                onSelectLayer(newLevels.id)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+ Levels", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                layers.forEach { layer ->
                    val isSelected = layer.id == activeLayerId
                    Surface(
                        onClick = { onSelectLayer(layer.id) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(layer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text("${layer.opacity}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Levels Sliders (After Effects Controls)
        if (activeLayer?.type == EffectType.LEVELS && activeLayer.levelsData != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("After Effects Levels Sliders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    var cfg = when (activeChannel) {
                        ChannelType.RGB -> activeLayer.levelsData.rgb
                        ChannelType.RED -> activeLayer.levelsData.red
                        ChannelType.GREEN -> activeLayer.levelsData.green
                        ChannelType.BLUE -> activeLayer.levelsData.blue
                    }

                    // Input Black
                    Text("Input Black: ${cfg.inputBlack}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = cfg.inputBlack.toFloat(),
                        onValueChange = { v ->
                            val updated = cfg.copy(inputBlack = v.toInt())
                            val updatedData = when (activeChannel) {
                                ChannelType.RGB -> activeLayer.levelsData.copy(rgb = updated)
                                ChannelType.RED -> activeLayer.levelsData.copy(red = updated)
                                ChannelType.GREEN -> activeLayer.levelsData.copy(green = updated)
                                ChannelType.BLUE -> activeLayer.levelsData.copy(blue = updated)
                            }
                            onUpdateLayers(layers.map { if (it.id == activeLayer.id) it.copy(levelsData = updatedData) else it })
                        },
                        valueRange = 0f..255f
                    )

                    // Gamma
                    Text("Gamma: ${String.format("%.2f", cfg.gamma)}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = cfg.gamma,
                        onValueChange = { v ->
                            val updated = cfg.copy(gamma = v)
                            val updatedData = when (activeChannel) {
                                ChannelType.RGB -> activeLayer.levelsData.copy(rgb = updated)
                                ChannelType.RED -> activeLayer.levelsData.copy(red = updated)
                                ChannelType.GREEN -> activeLayer.levelsData.copy(green = updated)
                                ChannelType.BLUE -> activeLayer.levelsData.copy(blue = updated)
                            }
                            onUpdateLayers(layers.map { if (it.id == activeLayer.id) it.copy(levelsData = updatedData) else it })
                        },
                        valueRange = 0.2f..3.0f
                    )

                    // Input White
                    Text("Input White: ${cfg.inputWhite}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = cfg.inputWhite.toFloat(),
                        onValueChange = { v ->
                            val updated = cfg.copy(inputWhite = v.toInt())
                            val updatedData = when (activeChannel) {
                                ChannelType.RGB -> activeLayer.levelsData.copy(rgb = updated)
                                ChannelType.RED -> activeLayer.levelsData.copy(red = updated)
                                ChannelType.GREEN -> activeLayer.levelsData.copy(green = updated)
                                ChannelType.BLUE -> activeLayer.levelsData.copy(blue = updated)
                            }
                            onUpdateLayers(layers.map { if (it.id == activeLayer.id) it.copy(levelsData = updatedData) else it })
                        },
                        valueRange = 0f..255f
                    )
                }
            }
        }
    }
}

@Composable
fun ExportTab(
    layers: List<EffectLayer>,
    precision: LUTPrecision,
    onPrecisionChange: (LUTPrecision) -> Unit
) {
    var exportedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bake & Export 3D Color LUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Generate standard .cube and HALD CLUT PNG files compatible with Premiere, After Effects, DaVinci Resolve, and games.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Precision
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lattice Sampling Precision", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LUTPrecision.values().forEach { p ->
                        FilterChip(
                            selected = precision == p,
                            onClick = { onPrecisionChange(p) },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        }

        // Export Actions
        Button(
            onClick = {
                val cubeText = ColorEngine.generateCubeLUT(layers, precision)
                exportedMessage = "Successfully baked 3D Cube LUT (${precision.pointCount} sample nodes)!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Bake & Export .cube LUT")
        }

        Button(
            onClick = {
                val hald = ColorEngine.generateHaldBitmap(layers, 8)
                exportedMessage = "Generated 512x512 HALD 8 CLUT image!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Face, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Generate HALD CLUT Image")
        }

        exportedMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
