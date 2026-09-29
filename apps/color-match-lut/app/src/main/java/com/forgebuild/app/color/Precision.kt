package com.forgebuild.app.color
enum class Precision(val lutSize:Int,val sampleDim:Int,val blurb:String){
  LOW(17,256,"17^3 LUT - fastest - quick previews"),
  STANDARD(33,384,"33^3 LUT - balanced accuracy/speed"),
  HIGH(64,512,"64^3 LUT - complex nonlinear transforms") }
