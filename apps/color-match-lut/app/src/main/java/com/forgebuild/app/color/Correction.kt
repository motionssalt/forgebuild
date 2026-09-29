package com.forgebuild.app.color
import android.graphics.Bitmap;import kotlin.math.pow
enum class Channel{RGB,RED,GREEN,BLUE}
data class Levels(var inBlack:Float=0f,var inWhite:Float=1f,var gamma:Float=1f,var outBlack:Float=0f,var outWhite:Float=1f,var channel:Channel=Channel.RGB){
  fun apply(v:Float):Float{val range=(inWhite-inBlack).coerceAtLeast(1e-5f)
    var x=((v-inBlack)/range).coerceIn(0f,1f);x=x.pow(1f/gamma.coerceAtLeast(0.01f))
    return (outBlack+x*(outWhite-outBlack)).coerceIn(0f,1f)} }
data class Curves(var points:MutableList<Pair<Float,Float>>=mutableListOf(0f to 0f,1f to 1f),var channel:Channel=Channel.RGB){
  fun apply(v:Float):Float{val p=points.sortedBy{it.first};if(p.isEmpty())return v
    if(v<=p.first().first)return p.first().second;if(v>=p.last().first)return p.last().second
    for(i in 0 until p.size-1){val (x0,y0)=p[i];val (x1,y1)=p[i+1]
      if(v in x0..x1){val t=if(x1-x0<1e-6f)0f else (v-x0)/(x1-x0);return (y0+t*(y1-y0)).coerceIn(0f,1f)}}
    return v}
  fun reset(){points=mutableListOf(0f to 0f,1f to 1f)} }
sealed class Effect{abstract val name:String;abstract var enabled:Boolean;abstract fun cloneFx():Effect
  data class LevelsFx(var levels:Levels,override var enabled:Boolean=true):Effect(){
    override val name get()="Levels (${levels.channel})";override fun cloneFx()=LevelsFx(levels.copy(),enabled)}
  data class CurvesFx(var curves:Curves,override var enabled:Boolean=true):Effect(){
    override val name get()="Curves (${curves.channel})";override fun cloneFx()=CurvesFx(curves.copy(points=curves.points.toMutableList()),enabled)}
  data class AutoMatchFx(val lut:FloatArray,val size:Int,override var enabled:Boolean=true):Effect(){
    override val name get()="Auto Match";override fun cloneFx()=AutoMatchFx(lut,size,enabled)} }
object CorrectionStack{
  fun apply(effects:List<Effect>,r:Float,g:Float,b:Float):FloatArray{
    var rr=r;var gg=g;var bb=b
    for(e in effects){if(!e.enabled)continue;when(e){
      is Effect.LevelsFx->{val L=e.levels;when(L.channel){
        Channel.RGB->{rr=L.apply(rr);gg=L.apply(gg);bb=L.apply(bb)}
        Channel.RED->rr=L.apply(rr);Channel.GREEN->gg=L.apply(gg);Channel.BLUE->bb=L.apply(bb)}}
      is Effect.CurvesFx->{val C=e.curves;when(C.channel){
        Channel.RGB->{rr=C.apply(rr);gg=C.apply(gg);bb=C.apply(bb)}
        Channel.RED->rr=C.apply(rr);Channel.GREEN->gg=C.apply(gg);Channel.BLUE->bb=C.apply(bb)}}
      is Effect.AutoMatchFx->{val o=com.forgebuild.app.lut.LutEngine.sample(e.lut,e.size,rr,gg,bb);rr=o[0];gg=o[1];bb=o[2]}}}
    return floatArrayOf(rr.coerceIn(0f,1f),gg.coerceIn(0f,1f),bb.coerceIn(0f,1f))}
  fun applyToBitmap(effects:List<Effect>,src:Bitmap):Bitmap{
    val w=src.width;val h=src.height;val out=src.copy(Bitmap.Config.ARGB_8888,true)
    val px=IntArray(w*h);out.getPixels(px,0,w,0,0,w,h)
    for(i in px.indices){val c=px[i]
      val r=((c shr 16)and 0xFF)/255f;val g=((c shr 8)and 0xFF)/255f;val b=(c and 0xFF)/255f
      val o=apply(effects,r,g,b)
      px[i]=(0xFF shl 24)or((o[0]*255f).toInt() shl 16)or((o[1]*255f).toInt() shl 8)or(o[2]*255f).toInt()}
    out.setPixels(px,0,w,0,0,w,h);return out} }
