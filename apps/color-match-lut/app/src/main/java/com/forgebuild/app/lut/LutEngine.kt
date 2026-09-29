package com.forgebuild.app.lut
import com.forgebuild.app.color.CorrectionStack;import com.forgebuild.app.color.Effect
object LutEngine{
  fun sample(lut:FloatArray,size:Int,r:Float,g:Float,b:Float):FloatArray{
    val m=(size-1).toFloat();val x=(r*m).coerceIn(0f,m);val y=(g*m).coerceIn(0f,m);val z=(b*m).coerceIn(0f,m)
    val x0=x.toInt().coerceAtMost(size-2);val y0=y.toInt().coerceAtMost(size-2);val z0=z.toInt().coerceAtMost(size-2)
    val fx=x-x0;val fy=y-y0;val fz=z-z0
    fun at(xx:Int,yy:Int,zz:Int,c:Int)=lut[(((zz*size+yy)*size+xx)*3)+c]
    val out=FloatArray(3)
    for(c in 0..2){val c000=at(x0,y0,z0,c);val c100=at(x0+1,y0,z0,c);val c010=at(x0,y0+1,z0,c);val c110=at(x0+1,y0+1,z0,c)
      val c001=at(x0,y0,z0+1,c);val c101=at(x0+1,y0,z0+1,c);val c011=at(x0,y0+1,z0+1,c);val c111=at(x0+1,y0+1,z0+1,c)
      val c00=c000+fx*(c100-c000);val c10=c010+fx*(c110-c010);val c01=c001+fx*(c101-c001);val c11=c011+fx*(c111-c011)
      val c0=c00+fy*(c10-c00);val c1=c01+fy*(c11-c01);out[c]=c0+fz*(c1-c0)}
    return out}
  fun bake(effects:List<Effect>,size:Int):FloatArray{val lut=FloatArray(size*size*size*3);val m=(size-1).toFloat();var i=0
    for(b in 0 until size)for(g in 0 until size)for(r in 0 until size){val o=CorrectionStack.apply(effects,r/m,g/m,b/m);lut[i++]=o[0];lut[i++]=o[1];lut[i++]=o[2]}
    return lut}
  fun toCube(lut:FloatArray,size:Int,title:String="ColorMatchLUT"):String{
    val sb=StringBuilder();sb.append("TITLE \"").append(title).append("\"\n")
    sb.append("# Color space: sRGB / Rec.709 gamma-encoded (0..1)\n")
    sb.append("LUT_3D_SIZE ").append(size).append("\nDOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
    var i=0;val n=size*size*size
    for(k in 0 until n){sb.append(f(lut[i++])).append(' ').append(f(lut[i++])).append(' ').append(f(lut[i++])).append('\n')}
    return sb.toString()}
  fun toHaldBitmap(lut:FloatArray,size:Int):android.graphics.Bitmap{
    var level=2;while(level*level*level*level*level*level<size*size*size)level++
    val side=level*level*level;val bmp=android.graphics.Bitmap.createBitmap(side,side,android.graphics.Bitmap.Config.ARGB_8888)
    val px=IntArray(side*side);var idx=0;val l2=level*level
    for(y in 0 until side)for(x in 0 until side){val i=y*side+x
      val rr=(i/(l2*l2))%l2;val gg=(i/l2)%l2;val bb=i%l2
      val o=sample(lut,size,rr/(l2-1f),gg/(l2-1f),bb/(l2-1f))
      px[idx++]=(0xFF shl 24)or((o[0]*255f).toInt() shl 16)or((o[1]*255f).toInt() shl 8)or(o[2]*255f).toInt()}
    bmp.setPixels(px,0,side,0,0,side,side);return bmp}
  private fun f(v:Float)=String.format("%.6f",v.coerceIn(0f,1f)) }
