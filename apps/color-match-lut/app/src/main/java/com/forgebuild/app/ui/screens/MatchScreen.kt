package com.forgebuild.app.ui.screens
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.app.AppState
import com.forgebuild.app.match.FrameMatcher
import com.forgebuild.app.video.VideoEngine
import kotlinx.coroutines.launch

@Composable fun MatchScreen(modifier:Modifier=Modifier,onDone:()->Unit){
  val ctx=LocalContext.current;val scope=rememberCoroutineScope()
  val tv=AppState.tgtVideo
  var searching by remember{mutableStateOf(false)}
  var notice by remember{mutableStateOf<String?>(null)}
  var candidates by remember{mutableStateOf<List<Pair<Long,Bitmap>>>(emptyList())}
  if(tv==null){Column(modifier.padding(16.dp)){Text("Import a target video first (Setup).")};return}
  var exactMs by remember{mutableStateOf(0f)}
  var rStart by remember{mutableStateOf(AppState.rangeStartMs.toFloat())}
  var rEnd by remember{mutableStateOf(AppState.rangeEndMs.toFloat().coerceAtLeast(1f))}

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text("Frame Matching",style=MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
      Column(Modifier.weight(1f)){Text("REFERENCE",style=MaterialTheme.typography.labelMedium)
        Bmp(AppState.refFrame,"ref",Modifier.fillMaxWidth().height(140.dp))}
      Column(Modifier.weight(1f)){Text("TARGET",style=MaterialTheme.typography.labelMedium)
        Bmp(AppState.tgtFrame,"target",Modifier.fillMaxWidth().height(140.dp))} }

    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      FilterChip(selected=!AppState.useRangeSearch,onClick={AppState.useRangeSearch=false},label={Text("Exact Frame")})
      FilterChip(selected=AppState.useRangeSearch,onClick={AppState.useRangeSearch=true},label={Text("Search Range")}) }

    if(!AppState.useRangeSearch){
      Text("Target frame: ${fmtMs(exactMs.toLong())}",style=MaterialTheme.typography.labelMedium)
      Slider(value=exactMs,onValueChange={exactMs=it},valueRange=0f..tv.durationMs.toFloat())
      Button(onClick={scope.launch{searching=true
        val f=VideoEngine.extractFrame(ctx,Uri.parse(tv.uri),exactMs.toLong(),640)
        AppState.tgtFrame=f;AppState.tgtFrameMs=exactMs.toLong()
        val ref=AppState.refFrame
        AppState.match=if(f!=null&&ref!=null)FrameMatcher.findBest(ref,listOf(exactMs.toLong() to Bitmap.createScaledBitmap(f,160,160,true))) else null
        searching=false}},enabled=!searching){Text("Extract This Frame")}
    } else {
      Text("Search range: ${fmtMs(rStart.toLong())} → ${fmtMs(rEnd.toLong())}",style=MaterialTheme.typography.labelMedium)
      Text("Start",style=MaterialTheme.typography.labelSmall)
      Slider(value=rStart,onValueChange={rStart=it.coerceAtMost(rEnd)},valueRange=0f..tv.durationMs.toFloat())
      Text("End",style=MaterialTheme.typography.labelSmall)
      Slider(value=rEnd,onValueChange={rEnd=it.coerceAtLeast(rStart)},valueRange=0f..tv.durationMs.toFloat())
      Row(Modifier.horizontalScroll(rememberScrollState())){candidates.forEach{(t,b)->
        Bmp(b,fmtMs(t),Modifier.size(64.dp).padding(2.dp))}}
      Button(onClick={scope.launch{searching=true;notice=null
        AppState.rangeStartMs=rStart.toLong();AppState.rangeEndMs=rEnd.toLong()
        candidates=VideoEngine.scanRange(ctx,Uri.parse(tv.uri),rStart.toLong(),rEnd.toLong())
        val ref=AppState.refFrame
        if(ref==null||candidates.isEmpty()){notice="Unable to confidently identify a matching frame.\nTry: expanding the search range, selecting the frame manually, or choosing a different reference frame."}
        else{
          val res=FrameMatcher.findBest(ref,candidates)
          AppState.match=res
          val full=VideoEngine.extractFrame(ctx,Uri.parse(tv.uri),res.timeMs,640)
          AppState.tgtFrame=full;AppState.tgtFrameMs=res.timeMs
          if(res.confidence<0.45f)notice="Low confidence match (${(res.confidence*100).toInt()}%). You can accept it or pick another frame manually."
        }
        searching=false}},enabled=!searching){
        Icon(Icons.Filled.Search,null);Spacer(Modifier.width(4.dp));Text("Find Matching Frame") }
    }

    notice?.let{Text(it,color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall)}
    val m=AppState.match
    if(m!=null){
      ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(m.confidence>=0.45f)"Best Match" else "Low confidence match",style=MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress={m.confidence},modifier=Modifier.fillMaxWidth())
        Text("Confidence: ${"%.1f".format(m.confidence*100)}%  ·  @ ${fmtMs(m.timeMs)}",style=MaterialTheme.typography.bodyMedium)
        m.cropRect?.let{Text("Corresponding region located on target (crop/registration estimated).",style=MaterialTheme.typography.bodySmall)}
      } } }
    if(searching){LinearProgressIndicator(Modifier.fillMaxWidth())}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Button(onClick=onDone,enabled=AppState.tgtFrame!=null){Icon(Icons.Filled.Check,null);Spacer(Modifier.width(4.dp));Text("Accept Match")}
      OutlinedButton(onClick={AppState.match=null;AppState.tgtFrame=null}){Icon(Icons.Filled.Refresh,null);Spacer(Modifier.width(4.dp));Text("Find Another")} }
  } }
