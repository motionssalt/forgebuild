package com.forgebuild.animelutmatch

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.forgebuild.animelutmatch.lut.LutGen
import com.forgebuild.animelutmatch.model.MatchSession
import com.forgebuild.animelutmatch.ui.App
import com.forgebuild.animelutmatch.ui.Screen
import com.forgebuild.animelutmatch.video.VideoFrameExtractor
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    val session = MatchSession()
    var screen by mutableStateOf<Screen>(Screen.Home)

    var refExtractor by mutableStateOf<VideoFrameExtractor?>(null)
    var tgtExtractor by mutableStateOf<VideoFrameExtractor?>(null)
    private var pickIsReference = true

    /** Bytes producer for the export the user just requested; consumed by the SAF callback. */
    private var pendingSave: (() -> ByteArray)? = null

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val ex = VideoFrameExtractor(this, uri)
            if (ex.durationMs <= 0 || ex.width <= 0) { ex.release(); return@registerForActivityResult }
            if (pickIsReference) { refExtractor?.release(); refExtractor = ex }
            else { tgtExtractor?.release(); tgtExtractor = ex }
            screen = if (pickIsReference) Screen.RefFrame else Screen.TgtFrame
        }

    // Engine SafeSave (Storage Access Framework) — user picks destination, app writes.
    private val cubeSaver = SafeSave.registerCreateDocument(this, "application/octet-stream") { uri ->
        val bytes = pendingSave?.invoke(); pendingSave = null
        if (uri != null && bytes != null) SafeSave.writeBytes(this, uri, bytes)
    }
    private val haldSaver = SafeSave.registerCreateDocument(this, "image/png") { uri ->
        val bytes = pendingSave?.invoke(); pendingSave = null
        if (uri != null && bytes != null) SafeSave.writeBytes(this, uri, bytes)
    }

    fun pickVideo(isReference: Boolean) {
        pickIsReference = isReference
        videoPicker.launch(arrayOf("video/*"))
    }

    fun exportCube() {
        pendingSave = { LutGen.toCube(session).toByteArray(Charsets.UTF_8) }
        cubeSaver.launch("${session.name}.cube")
    }

    fun exportHald() {
        pendingSave = {
            val bos = ByteArrayOutputStream()
            LutGen.toHald(session).compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
        haldSaver.launch("${session.name}-hald.png")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                App(
                    session = session,
                    screen = screen,
                    onScreen = { screen = it },
                    refExtractor = refExtractor,
                    tgtExtractor = tgtExtractor,
                    onPickVideo = ::pickVideo,
                    onExportCube = ::exportCube,
                    onExportHald = ::exportHald,
                )
            }
        }
    }

    override fun onDestroy() {
        refExtractor?.release(); tgtExtractor?.release()
        super.onDestroy()
    }
}
