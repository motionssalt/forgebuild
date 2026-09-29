package com.forgebuild.app.color
import android.graphics.Bitmap
object AutoMatcher{
  fun match(reference:Bitmap,target:Bitmap,precision:Precision):Pair<FloatArray,Int>{
    val size=precision.lutSize;val dim=precision.sampleDim
    val ref=Bitmap.createScaledBitmap(reference,dim,dim,true)
    val tgt=Bitmap.createScaledBitmap(target,dim,dim,true)
    val lut=FloatArray(size*size*size*3)
    val tR=percentileMap(ref,tgt,0,size);val tG=percentileMap(ref,tgt,1,size);val tB=percentileMap(ref,tgt,2,size)
    var i=0;for(b in 0 until size)for(g in 0 until size)for(r in 0 until size){lut[i++]=tR[r];lut[i++]=tG[g];lut[i++]=tB[b]}
    if(ref!=reference)ref.recycle();if(tgt!=target)tgt.recycle();return lut to size}
  private fun percentileMap(ref:Bitmap,tgt:Bitmap,channel:Int,nodes:Int):FloatArray{
    val bins=256;val hr=IntArray(bins);val ht=IntArray(bins);accumulate(ref,hr,channel);accumulate(tgt,ht,channel)
    val cr=cdf(hr);val ct=cdf(ht);val map=FloatArray(nodes)
    for(n in 0 until nodes){val inV=n/(nodes-1f);val cIn=ct[(inV*255).toInt().coerceIn(0,255)]
      var lo=0;var hi=255;while(lo<hi){val mid=(lo+hi)/2;if(cr[mid]<cIn)lo=mid+1 else hi=mid}
      map[n]=(lo/255f).coerceIn(0f,1f)}
    return smooth(map)}
  private fun accumulate(bmp:Bitmap,hist:IntArray,channel:Int){val w=bmp.width;val h=bmp.height
    val px=IntArray(w*h);bmp.getPixels(px,0,w,0,0,w,h)
    for(c in px){val v=when(channel){0->(c shr 16)and 0xFF;1->(c shr 8)and 0xFF;else->c and 0xFF};hist[v]++}}
  private fun cdf(hist:IntArray):FloatArray{val c=FloatArray(hist.size);var acc=0f;val total=hist.sum().coerceAtLeast(1)
    for(i in hist.indices){acc+=hist[i];c[i]=acc/total};return c}
  private fun smooth(a:FloatArray):FloatArray{val out=a.copyOf()
    for(i in 1 until a.size-1)out[i]=(a[i-1]+2*a[i]+a[i+1])/4f
    for(i in 1 until out.size)if(out[i]<out[i-1])out[i]=out[i-1];return out} }
