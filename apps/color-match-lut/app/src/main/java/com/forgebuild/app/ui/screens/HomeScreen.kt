package com.forgebuild.app.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.app.AppState

@Composable fun HomeScreen(modifier:Modifier=Modifier,onNew:()->Unit){
  Column(modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
    Text("Color Match LUT",style=MaterialTheme.typography.displaySmall)
    Text("Copy the color grade from a reference anime edit onto your own footage, then export it as a .cube / HALD LUT.",
      style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    ElevatedCard(Modifier.fillMaxWidth()){
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Workflow",style=MaterialTheme.typography.titleMedium)
        Text("1. Import reference video + pick the graded frame\n2. Import target video + find the matching frame (exact or range search)\n3. Auto-match color, refine with AE-style Levels & Curves\n4. Export .cube / HALD LUT",
          style=MaterialTheme.typography.bodyMedium)
      }
    }
    Button(onClick={AppState.resetProject();onNew()},modifier=Modifier.fillMaxWidth()){
      Icon(Icons.Filled.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("New Project") }
    if(AppState.refVideo!=null||AppState.tgtVideo!=null){
      OutlinedButton(onClick=onNew,modifier=Modifier.fillMaxWidth()){Text("Resume Current Project")} }
  } }
