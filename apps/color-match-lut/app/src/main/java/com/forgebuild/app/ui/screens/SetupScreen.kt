package com.forgebuild.app.ui.screens
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.app.AppState
import com.forgebuild.app.video.VideoEngine
import com.forgebuild.app.video.VideoInfo
import kotlinx.coroutines.launch

@Composable fun SetupScreen(modifier:Modifier=Modifier,onDone:()->Unit){
  val ctx=LocalContext.current;val scope=rememberCoroutineScope()
  var error by remember{mutableStateOf<String?>(null)}
  var refThumbs by remember{mutableStateOf<List<Pair<Long,Bitmap>>>(emptyList())}
  var tgtThumbs by remember{mutableStateOf<List<Pair<Long,Bitmap>>>(emptyList())}
  var refMs by remember{mutableStateOf(0f)}
  var tgtMs by remember{mutableStateOf(0f)}
  var busy by remember{mutableStateOf(false)}

  fun importVideo(uri:android.net.Uri,isRef:Boolean){
    scope.launch{
      val info=VideoEngine.probe(ctx,uri)
      if(info==null){error="Unsupported or corrupted video file.";return@launch}
      if(isRef){AppState.refVideo=info;refThumbs=VideoEngine.extractThumbnails(ctx,uri,info)}
      else{AppState.tgtVideo=info;tgtThumbs=VideoEngine.extractThumbnails(ctx,uri,info)
        AppState.rangeEndMs=info.durationMs} } }

  val refPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{importVideo(it,true)}}
  val tgtPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{importVideo(it,false)}}

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text("Project Setup",style=MaterialTheme.typography.headlineMedium)
    error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}

    // REFERENCE
    ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Text("Reference Video (has the desired CC)",style=MaterialTheme.typography.titleMedium)
      val rv=AppState.refVideo
      if(rv==null){OutlinedButton(onClick={refPicker.launch(arrayOf("video/*"))}){Icon(Icons.Filled.Add,null);Spacer(Modifier.width(4.dp));Text("Import")}}
      else{
        Text("${rv.width}x${rv.height} · ${fmtMs(rv.durationMs)} · ${"%.1f".format(rv.fps)}fps",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())){refThumbs.forEach{(t,b)->
          Bmp(b,fmtMs(t),Modifier.size(72.dp).padding(2.dp))}}
        Text("Reference frame: ${fmtMs(refMs.toLong())}",style=MaterialTheme.typography.labelMedium)
        Slider(value=refMs,onValueChange={refMs=it},valueRange=0f..rv.durationMs.toFloat())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
          Button(onClick={scope.launch{busy=true
            AppState.refFrame=VideoEngine.extractFrame(ctx,android.net.Uri.parse(rv.uri),refMs.toLong(),640)
            AppState.refFrameMs=refMs.toLong();busy=false}},enabled=!busy){Icon(Icons.Filled.Check,null);Spacer(Modifier.width(4.dp));Text("Extract Frame")}
          TextButton(onClick={AppState.refVideo=null;refThumbs=emptyList();AppState.refFrame=null}){Text("Replace")} }
        Bmp(AppState.refFrame,"Reference frame",Modifier.fillMaxWidth().height(160.dp)) } } }

    // TARGET
    ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Text("Target Video (your ungraded footage)",style=MaterialTheme.typography.titleMedium)
      val tv=AppState.tgtVideo
      if(tv==null){OutlinedButton(onClick={tgtPicker.launch(arrayOf("video/*"))}){Icon(Icons.Filled.Add,null);Spacer(Modifier.width(4.dp));Text("Import")}}
      else{
        Text("${tv.width}x${tv.height} · ${fmtMs(tv.durationMs)}",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())){tgtThumbs.forEach{(t,b)->
          Bmp(b,fmtMs(t),Modifier.size(72.dp).padding(2.dp))}}
        TextButton(onClick={AppState.tgtVideo=null;tgtThumbs=emptyList();AppState.tgtFrame=null;AppState.match=null}){Text("Replace")} } } }

    Button(onClick=onDone,enabled=AppState.refFrame!=null&&AppState.tgtVideo!=null,modifier=Modifier.fillMaxWidth()){
      Text("Continue to Frame Matching") }
  } }
