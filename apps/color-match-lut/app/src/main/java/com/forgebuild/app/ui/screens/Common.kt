package com.forgebuild.app.ui.screens
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable fun Bmp(b:Bitmap?,label:String,modifier:Modifier=Modifier){
  if(b!=null){Image(bitmap=b.asImageBitmap(),contentDescription=label,modifier=modifier,contentScale=ContentScale.Fit)}
  else{Surface(modifier=modifier,color=MaterialTheme.colorScheme.surfaceContainerHighest,shape=MaterialTheme.shapes.medium){
    Box(Modifier.fillMaxSize()){Text(label,modifier=Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall)} } } }

fun fmtMs(ms:Long):String{val s=ms/1000;val f=((ms%1000)*24/1000)
  return "%02d:%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60,f)}

/** Channel-isolated view: emphasize one channel like AE's channel views. */
fun channelView(src:Bitmap,ch:com.forgebuild.app.color.Channel):Bitmap{
  val w=src.width;val h=src.height;val out=src.copy(Bitmap.Config.ARGB_8888,true)
  val px=IntArray(w*h);out.getPixels(px,0,w,0,0,w,h)
  for(i in px.indices){val c=px[i]
    val r=(c shr 16)and 0xFF;val g=(c shr 8)and 0xFF;val b=c and 0xFF
    px[i]=when(ch){
      com.forgebuild.app.color.Channel.RED->{val v=r;(0xFF shl 24)or(v shl 16)or(v/3 shl 8)or(v/3)}
      com.forgebuild.app.color.Channel.GREEN->{val v=g;(0xFF shl 24)or(v/3 shl 16)or(v shl 8)or(v/3)}
      com.forgebuild.app.color.Channel.BLUE->{val v=b;(0xFF shl 24)or(v/3 shl 16)or(v/3 shl 8)or(v)}
      com.forgebuild.app.color.Channel.RGB->c } }
  out.setPixels(px,0,w,0,0,w,h);return out}
