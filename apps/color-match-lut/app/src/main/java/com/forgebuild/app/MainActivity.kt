package com.forgebuild.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.forgebuild.app.ui.screens.*
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

enum class Screen(val label:String,val icon:ImageVector){
  HOME("Home",Icons.Filled.Home),SETUP("Setup",Icons.Filled.Settings),MATCH("Match",Icons.Filled.Search),
  GRADE("Grade",Icons.Filled.Edit),EXPORT("Export",Icons.Filled.Share) }

class MainActivity:ComponentActivity(){
  override fun onCreate(savedInstanceState:Bundle?){
    super.onCreate(savedInstanceState);enableEdgeToEdge()
    setContent{ForgeBuildTheme{
      var screen by remember{mutableStateOf(Screen.HOME)}
      Scaffold(Modifier.fillMaxSize(),bottomBar={NavigationBar{
        Screen.entries.forEach{s->NavigationBarItem(selected=screen==s,onClick={screen=s},
          icon={Icon(s.icon,s.label)},label={Text(s.label)})} } }){pad->
        when(screen){
          Screen.HOME->HomeScreen(Modifier.padding(pad)){screen=Screen.SETUP}
          Screen.SETUP->SetupScreen(Modifier.padding(pad)){screen=Screen.MATCH}
          Screen.MATCH->MatchScreen(Modifier.padding(pad)){screen=Screen.GRADE}
          Screen.GRADE->GradeScreen(Modifier.padding(pad)){screen=Screen.EXPORT}
          Screen.EXPORT->ExportScreen(Modifier.padding(pad)) } } } } } }
