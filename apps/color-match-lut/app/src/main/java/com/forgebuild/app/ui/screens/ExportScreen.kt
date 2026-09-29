package com.forgebuild.app.ui.screens
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.app.AppState
import com.forgebuild.app.lut.LutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable fun ExportScreen(modifier:Modifier=Modifier){
  val ctx=LocalContext.current;val scope=rememberCoroutineScope()
  var status by remember{mutableStateOf<String?>(null)}
  var working by remember{mutableStateOf(false)}
  var mode by remember{mutableStateOf("BOTH")}
  var pendingCube by remember{mutableStateOf<String?>(null)}
  var pendingHald by remember{mutableStateOf<Bitmap?>(null)}
  val size=AppState.precision.lutSize

  fun writeOs(os:OutputStream?,data:String){os?.use{it.write(data.toByteArray())}}
  val cubeSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
    uri?.let{ctx.contentResolver.openOutputStream(it).use{os->pendingCube?.let{c->os?.write(c.toByteArray())}}
      status="Saved .cube LUT"} }
  val haldSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
    uri?.let{ctx.contentResolver.openOutputStream(it)?.use{os->pendingHald?.compress(Bitmap.CompressFormat.PNG,100,os)}
      status="Saved HALD CLUT (PNG)"} }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text("Generate & Export LUT",style=MaterialTheme.typography.headlineMedium)
    Text("The full correction stack (auto-match + ${AppState.stack.size-1} manual effect(s)) is baked into a ${size}^3 3D LUT. Color space: sRGB / Rec.709 gamma-encoded 0..1.",
      style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Column(Modifier.weight(1f)){Text("REFERENCE",style=MaterialTheme.typography.labelSmall)
        Bmp(AppState.refFrame,"ref",Modifier.fillMaxWidth().height(120.dp))}
      Column(Modifier.weight(1f)){Text("RESULT",style=MaterialTheme.typography.labelSmall)
        Bmp(AppState.gradedPreview?:AppState.tgtFrame,"result",Modifier.fillMaxWidth().height(120.dp))} }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf("CUBE","HALD","BOTH").forEach{m->FilterChip(selected=mode==m,onClick={mode=m},label={Text(m)})} }
    Button(enabled=!working,onClick={scope.launch{working=true;status=null
      val(lut,sz)=withContext(Dispatchers.Default){LutEngine.bake(AppState.stack.toList(),size) to size}
      if(mode=="CUBE"||mode=="BOTH"){pendingCube=LutEngine.toCube(lut,sz,"ColorMatchLUT")}
      if(mode=="HALD"||mode=="BOTH"){pendingHald=withContext(Dispatchers.Default){LutEngine.toHaldBitmap(lut,sz)}}
      status="LUT baked (${sz}^3). Choose destination(s) below."
      working=false}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Filled.Share,null);Spacer(Modifier.width(6.dp));Text("Generate LUT")}
    if(pendingCube!=null){Button(onClick={cubeSaver.launch("color-match.cube")},modifier=Modifier.fillMaxWidth()){Text("Save .cube")}}
    if(pendingHald!=null){Button(onClick={haldSaver.launch("color-match-hald.png")},modifier=Modifier.fillMaxWidth()){Text("Save HALD PNG")}}
    status?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)}
    if(working){LinearProgressIndicator(Modifier.fillMaxWidth())} } }
