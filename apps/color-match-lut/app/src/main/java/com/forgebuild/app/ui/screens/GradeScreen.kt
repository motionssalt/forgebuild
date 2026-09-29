package com.forgebuild.app.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.app.AppState
import com.forgebuild.app.color.*
import com.forgebuild.app.lut.LutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun GradeScreen(modifier:Modifier=Modifier,onDone:()->Unit){
  val scope=rememberCoroutineScope()
  var channel by remember{mutableStateOf(Channel.RGB)}
  var showCurves by remember{mutableStateOf(false)}
  var working by remember{mutableStateOf(false)}
  var graded by remember{mutableStateOf(AppState.gradedPreview)}
  var split by remember{mutableStateOf(false)}
  var selIdx by remember{mutableStateOf(-1)}

  fun recompute(){val tgt=AppState.tgtFrame?:return;val fx=AppState.stack.toList()
    scope.launch{working=true
      graded=withContext(Dispatchers.Default){CorrectionStack.applyToBitmap(fx,tgt)}
      AppState.gradedPreview=graded;working=false} }

  Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Color Match & Manual Grade",style=MaterialTheme.typography.headlineMedium)

    // mode + precision + auto
    ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected=AppState.modeAuto,onClick={AppState.modeAuto=true},label={Text("Automatic")})
        FilterChip(selected=!AppState.modeAuto,onClick={AppState.modeAuto=false},label={Text("Manual")}) }
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Precision.entries.forEach{p->FilterChip(selected=AppState.precision==p,onClick={AppState.precision=p},label={Text(p.name)})} }
      Text(AppState.precision.blurb,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      if(AppState.modeAuto){
        Button(onClick={scope.launch{working=true
          val ref=AppState.refFrame;val tgt=AppState.tgtFrame
          if(ref!=null&&tgt!=null){
            val(lut,size)=withContext(Dispatchers.Default){AutoMatcher.match(ref,tgt,AppState.precision)}
            AppState.stack.removeAll{it is Effect.AutoMatchFx}
            AppState.stack.add(0,Effect.AutoMatchFx(lut,size))
            recompute() }
          working=false}},enabled=!working){Text("Analyze & Auto-Match") } } } }

    // before/after comparison
    Text(if(split)"Split: TARGET ORIGINAL | AFTER MATCH" else "REFERENCE | TARGET AFTER",
      style=MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Column(Modifier.weight(1f)){Text("REF",style=MaterialTheme.typography.labelSmall)
        Bmp(channelView(AppState.refFrame?:return@Column,channel),"ref",Modifier.fillMaxWidth().height(120.dp))}
      Column(Modifier.weight(1f)){Text(if(split)"ORIGINAL" else "AFTER",style=MaterialTheme.typography.labelSmall)
        val img=if(split)AppState.tgtFrame else (graded?:AppState.tgtFrame)
        Bmp(img?.let{channelView(it,channel)},"tgt",Modifier.fillMaxWidth().height(120.dp))} }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      AssistChip(onClick={split=!split},label={Text(if(split)"Show After" else "Split Before/After")})
      Channel.entries.forEach{c->FilterChip(selected=channel==c,onClick={channel=c},label={Text(c.name)})} }

    // effect stack
    Text("Correction Stack",style=MaterialTheme.typography.titleMedium)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=200.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      itemsIndexed(AppState.stack){i,e->
        ElevatedCard(Modifier.fillMaxWidth()){
          Row(Modifier.padding(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Checkbox(checked=e.enabled,onCheckedChange={e.enabled=it;recompute()})
            Text(e.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={selIdx=if(selIdx==i)-1 else i}){Text(if(selIdx==i)"Close" else "Edit")}
            TextButton(onClick={AppState.stack.add(i+1,e.cloneFx());recompute()}){Text("Dup")}
            IconButton(onClick={AppState.stack.removeAt(i);recompute()}){Icon(Icons.Filled.Delete,null)} } } } }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedButton(onClick={AppState.stack.add(Effect.LevelsFx(Levels()));selIdx=AppState.stack.size-1}){Icon(Icons.Filled.Add,null);Text("Levels")}
      OutlinedButton(onClick={AppState.stack.add(Effect.CurvesFx(Curves()));selIdx=AppState.stack.size-1}){Icon(Icons.Filled.Add,null);Text("Curves")}
      OutlinedButton(onClick={recompute()}){Icon(Icons.Filled.Refresh,null);Text("Preview")} }

    // editor for selected effect
    val sel=AppState.stack.getOrNull(selIdx)
    if(sel is Effect.LevelsFx){
      ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text("Levels Editor",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
          Channel.entries.forEach{c->FilterChip(selected=sel.levels.channel==c,onClick={sel.levels.channel=c},label={Text(c.name)})} }
        LvlSlider("In Black",sel.levels.inBlack){sel.levels.inBlack=it}
        LvlSlider("In White",sel.levels.inWhite){sel.levels.inWhite=it}
        LvlSlider("Gamma",sel.levels.gamma,0.1f,4f){sel.levels.gamma=it}
        LvlSlider("Out Black",sel.levels.outBlack){sel.levels.outBlack=it}
        LvlSlider("Out White",sel.levels.outWhite){sel.levels.outWhite=it}
        Button(onClick={recompute()}){Text("Apply to Preview")} } } }
    if(sel is Effect.CurvesFx){
      ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text("Curves Editor (input→output, draggable points)",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
          Channel.entries.forEach{c->FilterChip(selected=sel.curves.channel==c,onClick={sel.curves.channel=c},label={Text(c.name)})} }
        // compact curve grid
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(160.dp)){
          val w=size.width;val h=size.height
          for(i in 0..4){val x=w*i/4f;drawLine(androidx.compose.ui.graphics.Color.Gray,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,h),1f)
            val y=h*i/4f;drawLine(androidx.compose.ui.graphics.Color.Gray,androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(w,y),1f)}
          drawLine(androidx.compose.ui.graphics.Color.DarkGray,androidx.compose.ui.geometry.Offset(0f,h),androidx.compose.ui.geometry.Offset(w,0f),2f)
          val pts=sel.curves.points.sortedBy{it.first}
          for(i in 0 until pts.size-1){
            val(x0,y0)=pts[i];val(x1,y1)=pts[i+1]
            drawLine(androidx.compose.ui.graphics.Color.Cyan,androidx.compose.ui.geometry.Offset(x0*w,h-y0*h),androidx.compose.ui.geometry.Offset(x1*w,h-y1*h),4f) }
          pts.forEach{(x,y)->drawCircle(androidx.compose.ui.graphics.Color.Yellow,10f,androidx.compose.ui.geometry.Offset(x*w,h-y*h))} }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
          OutlinedButton(onClick={sel.curves.points.add(0.25f to 0.25f)}){Text("+Pt L")}
          OutlinedButton(onClick={sel.curves.points.add(0.5f to 0.5f)}){Text("+Pt M")}
          OutlinedButton(onClick={sel.curves.points.add(0.75f to 0.75f)}){Text("+Pt H")}
          OutlinedButton(onClick={sel.curves.reset()}){Text("Reset")} }
        // edit each point via sliders
        sel.curves.points.forEachIndexed{idx,pt->
          var v by remember(pt){mutableStateOf(pt.second)}
          Text("Pt ${idx+1} (in ${"%.2f".format(pt.first)}) → out ${"%.2f".format(v)}",style=MaterialTheme.typography.labelSmall)
          Slider(value=v,onValueChange={v=it;sel.curves.points[idx]=pt.first to it}) }
        Button(onClick={recompute()}){Text("Apply to Preview")} } } }

    if(working){LinearProgressIndicator(Modifier.fillMaxWidth())}
    Button(onClick=onDone,modifier=Modifier.fillMaxWidth()){Icon(Icons.Filled.Check,null);Spacer(Modifier.width(6.dp));Text("Continue to Export")} } }

@Composable fun LvlSlider(label:String,value:Float,min:Float=0f,max:Float=1f,onCh:(Float)->Unit){
  var v by remember(value){mutableStateOf(value)}
  Column{Text("$label: ${"%.2f".format(v)}",style=MaterialTheme.typography.labelSmall)
    Slider(value=v,onValueChange={v=it;onCh(it)},valueRange=min..max)} }
