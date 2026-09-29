package com.forgebuild.app.match
import android.graphics.Bitmap;import kotlin.math.abs
data class MatchResult(val timeMs:Long,val confidence:Float,val frame:Bitmap?,val cropRect:android.graphics.Rect?)
object FrameMatcher{
  fun findBest(reference:Bitmap,candidates:List<Pair<Long,Bitmap>>):MatchResult{
    if(candidates.isEmpty())return MatchResult(0L,0f,null,null)
    val ref=Features(reference);var best=MatchResult(0L,0f,null,null);var bestScore=-1f
    for((t,cand) in candidates){val f=Features(cand);val s1=coarseScore(ref,f);if(s1<0.15f)continue
      val s2=featureScore(ref,f);val (s3,crop)=spatialVerify(reference,cand)
      val total=0.3f*s1+0.35f*s2+0.35f*s3
      if(total>bestScore){bestScore=total;best=MatchResult(t,total.coerceIn(0f,1f),cand,crop)}}
    return best}
  private class Features(bmp:Bitmap){val w=32;val h=32;val lum=FloatArray(w*h);val edge=FloatArray(w*h);var ahash=0L
    init{val s=Bitmap.createScaledBitmap(bmp,w,h,true);val px=IntArray(w*h);s.getPixels(px,0,w,0,0,w,h);var sum=0f
      for(i in px.indices){val c=px[i];val l=(0.299f*((c shr 16)and 0xFF)+0.587f*((c shr 8)and 0xFF)+0.114f*(c and 0xFF))/255f;lum[i]=l;sum+=l}
      val mean=sum/px.size;for(i in lum.indices)if(lum[i]>mean)ahash=ahash or (1L shl (i%64))
      for(y in 1 until h-1)for(x in 1 until w-1){val gx=lum[y*w+x+1]-lum[y*w+x-1];val gy=lum[(y+1)*w+x]-lum[(y-1)*w+x]
        edge[y*w+x]=kotlin.math.sqrt(gx*gx+gy*gy)}
      if(s!=bmp)s.recycle()} }
  private fun coarseScore(a:Features,b:Features):Float{
    var num=0f;var da=0f;var db=0f;val ma=a.lum.average().toFloat();val mb=b.lum.average().toFloat()
    for(i in a.lum.indices){val x=a.lum[i]-ma;val y=b.lum[i]-mb;num+=x*y;da+=x*x;db+=y*y}
    val corr=if(da>0&&db>0)(num/kotlin.math.sqrt(da*db)).coerceIn(-1f,1f) else 0f
    val ham=java.lang.Long.bitCount(a.ahash xor b.ahash);val hs=1f-ham/64f
    return ((corr+1f)/2f*0.6f+hs*0.4f).coerceIn(0f,1f)}
  private fun featureScore(a:Features,b:Features):Float{
    val thA=pct(a.edge,0.8f);val thB=pct(b.edge,0.8f);var inter=0;var uni=0
    for(i in a.edge.indices){val ea=a.edge[i]>thA;val eb=b.edge[i]>thB;if(ea&&eb)inter++;if(ea||eb)uni++}
    return if(uni==0)0f else (inter.toFloat()/uni).coerceIn(0f,1f)}
  private fun pct(a:FloatArray,p:Float):Float{val s=a.sorted();return s[(p*(s.size-1)).toInt().coerceIn(0,s.size-1)]}
  private fun spatialVerify(ref:Bitmap,tgt:Bitmap):Pair<Float,android.graphics.Rect>{
    val S=64;val r=Bitmap.createScaledBitmap(ref,S,S,true);val t=Bitmap.createScaledBitmap(tgt,S,S,true)
    var best=Float.MAX_VALUE;var bx=0;var by=0;val step=4;val win=24
    for(oy in -8..8 step step)for(ox in -8..8 step step){var acc=0f;var n=0
      for(y in win/2 until S-win/2 step 2)for(x in win/2 until S-win/2 step 2){
        val tx=(x+ox).coerceIn(0,S-1);val ty=(y+oy).coerceIn(0,S-1);acc+=lumDiff(r.getPixel(x,y),t.getPixel(tx,ty));n++}
      val m=if(n>0)acc/n else Float.MAX_VALUE;if(m<best){best=m;bx=ox;by=oy}}
    val score=(1f-(best/0.5f)).coerceIn(0f,1f)
    val rect=android.graphics.Rect((bx+8).coerceIn(0,S),(by+8).coerceIn(0,S),S-(bx+8).coerceIn(0,S),S-(by+8).coerceIn(0,S))
    if(r!=ref)r.recycle();if(t!=tgt)t.recycle();return score to rect}
  private fun lumDiff(c1:Int,c2:Int):Float{
    val l1=(0.299f*((c1 shr 16)and 0xFF)+0.587f*((c1 shr 8)and 0xFF)+0.114f*(c1 and 0xFF))/255f
    val l2=(0.299f*((c2 shr 16)and 0xFF)+0.587f*((c2 shr 8)and 0xFF)+0.114f*(c2 and 0xFF))/255f
    return abs(l1-l2)} }
