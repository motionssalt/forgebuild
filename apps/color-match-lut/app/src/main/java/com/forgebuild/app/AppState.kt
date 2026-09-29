package com.forgebuild.app
import android.graphics.Bitmap
import androidx.compose.runtime.getValue;import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf;import androidx.compose.runtime.setValue
import com.forgebuild.app.color.Effect;import com.forgebuild.app.color.Precision
import com.forgebuild.app.match.MatchResult;import com.forgebuild.app.video.VideoInfo
object AppState{
  var refVideo by mutableStateOf<VideoInfo?>(null)
  var tgtVideo by mutableStateOf<VideoInfo?>(null)
  var refFrameMs by mutableStateOf(0L)
  var refFrame by mutableStateOf<Bitmap?>(null)
  var tgtFrameMs by mutableStateOf(0L)
  var tgtFrame by mutableStateOf<Bitmap?>(null)
  var match by mutableStateOf<MatchResult?>(null)
  var precision by mutableStateOf(Precision.STANDARD)
  var modeAuto by mutableStateOf(true)
  val stack=mutableStateListOf<Effect>()
  var gradedPreview by mutableStateOf<Bitmap?>(null)
  var rangeStartMs by mutableStateOf(0L)
  var rangeEndMs by mutableStateOf(0L)
  var useRangeSearch by mutableStateOf(false)
  fun resetProject(){refVideo=null;tgtVideo=null;refFrame=null;tgtFrame=null;match=null
    stack.clear();gradedPreview=null;rangeStartMs=0;rangeEndMs=0;useRangeSearch=false} }
