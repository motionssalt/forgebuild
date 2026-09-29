package com.forgebuild.app.video
import android.content.Context;import android.graphics.Bitmap;import android.media.MediaMetadataRetriever
import android.net.Uri;import android.os.Build;import android.util.Log
import kotlinx.coroutines.Dispatchers;import kotlinx.coroutines.withContext
import kotlin.math.max
data class VideoInfo(val uri:String,val durationMs:Long,val width:Int,val height:Int,val fps:Float){
  val frameCountEstimate:Int get()=if(fps>0f)((durationMs/1000f)*fps).toInt() else 0
  val aspect:Float get()=if(height>0)width.toFloat()/height else 16f/9f }
object VideoEngine{
  private const val TAG="VideoEngine"
  fun probe(context:Context,uri:Uri):VideoInfo?{val r=MediaMetadataRetriever();return try{
    r.setDataSource(context,uri)
    val dur=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L
    val w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0
    val h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
    val fps=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?:30f
    if(dur<=0||w<=0||h<=0)null else VideoInfo(uri.toString(),dur,w,h,fps)
  }catch(e:Exception){Log.e(TAG,"probe",e);null}finally{try{r.release()}catch(_:Exception){}}}
  private fun scaled(r:MediaMetadataRetriever,us:Long,dim:Int):Bitmap?{
    return if(Build.VERSION.SDK_INT>=27){
      try{r.getScaledFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,dim,dim)}catch(e:Exception){null}
    }else{val f=try{r.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)}catch(e:Exception){null}
      f?.let{Bitmap.createScaledBitmap(it,dim,dim,true)} } }
  fun extractFrame(context:Context,uri:Uri,timeMs:Long,maxDim:Int=0):Bitmap?{val r=MediaMetadataRetriever();return try{
    r.setDataSource(context,uri);val us=max(0L,timeMs)*1000L
    if(maxDim>0)scaled(r,us,maxDim)
    else try{r.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)}catch(e:Exception){null}
  }catch(e:Exception){Log.e(TAG,"frame@$timeMs",e);null}finally{try{r.release()}catch(_:Exception){}}}
  suspend fun extractThumbnails(context:Context,uri:Uri,info:VideoInfo,count:Int=12,thumbDim:Int=192):List<Pair<Long,Bitmap>>=
    withContext(Dispatchers.IO){val out=ArrayList<Pair<Long,Bitmap>>(count);val r=MediaMetadataRetriever()
    try{r.setDataSource(context,uri);val step=if(count>1)info.durationMs/(count-1).coerceAtLeast(1) else info.durationMs
      for(i in 0 until count){val t=(i*step).coerceIn(0L,info.durationMs)
        scaled(r,t*1000L,thumbDim)?.let{out.add(t to it)}}
    }catch(e:Exception){Log.e(TAG,"thumbs",e)}finally{try{r.release()}catch(_:Exception){}};out}
  suspend fun scanRange(context:Context,uri:Uri,startMs:Long,endMs:Long,samples:Int=24,dim:Int=160):List<Pair<Long,Bitmap>>=
    withContext(Dispatchers.IO){val lo=minOf(startMs,endMs).coerceAtLeast(0L);val hi=maxOf(startMs,endMs)
    val span=(hi-lo).coerceAtLeast(1L);val n=samples.coerceAtLeast(2);val out=ArrayList<Pair<Long,Bitmap>>(n);val r=MediaMetadataRetriever()
    try{r.setDataSource(context,uri);for(i in 0 until n){val t=lo+(span*i/(n-1))
      scaled(r,t*1000L,dim)?.let{out.add(t to it)}}
    }catch(e:Exception){Log.e(TAG,"scan",e)}finally{try{r.release()}catch(_:Exception){}};out}
}
