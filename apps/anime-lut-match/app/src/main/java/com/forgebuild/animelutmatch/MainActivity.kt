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
import com.forgebuild.animelutmatch.media.ImageLoader
import com.forgebuild.animelutmatch.media.MediaSource
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

    var refSource by mutableStateOf<MediaSource?>(null)
    var tgtSource by mutableStateOf<MediaSource?>(null)
    private var pickIsReference = true

    /** Bytes producer for the export the user just requested; consumed by the SAF callback. */
    private var pendingSave: (() -> ByteArray)? = null

    private val mediaPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val type = contentResolver.getType(uri).orEmpty()
            if (type.startsWith("image/")) {
                val bmp = ImageLoader.decode(this, uri) ?: return@registerForActivityResult
                // Clear the correction settings and only THIS side's locked frame; the other
                // side's frame survives (v3 fix — see MatchSession.resetForImport).
                session.resetForImport(pickIsReference)
                val src = MediaSource.Image(bmp)
                session.logLine("Imported image (${bmp.width}×${bmp.height}) as ${if (pickIsReference) "reference" else "target"}")
                if (pickIsReference) { refSource?.release(); refSource = src }
                else { tgtSource?.release(); tgtSource = src }
                screen = if (pickIsReference) Screen.RefFrame else Screen.TgtFrame
            } else {
                val ex = try {
                    VideoFrameExtractor(this, uri)
                } catch (_: Exception) { null }
                if (ex == null || ex.durationMs <= 0 || ex.width <= 0) {
                    ex?.release(); return@registerForActivityResult
                }
                session.resetForImport(pickIsReference)
                val src = MediaSource.Video(ex)
                session.logLine("Imported video (${ex.width}×${ex.height}, ${ex.durationMs / 1000.0}s) as ${if (pickIsReference) "reference" else "target"}")
                if (pickIsReference) { refSource?.release(); refSource = src }
                else { tgtSource?.release(); tgtSource = src }
                screen = if (pickIsReference) Screen.RefFrame else Screen.TgtFrame
            }
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

    fun pickMedia(isReference: Boolean) {
        pickIsReference = isReference
        mediaPicker.launch(arrayOf("video/*", "image/*"))
    }

    /** Fresh project: drop both sources, clear the whole session, back to Home. */
    fun newProject() {
        refSource?.release(); refSource = null
        tgtSource?.release(); tgtSource = null
        session.reset()
        screen = Screen.Home
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
                    refSource = refSource,
                    tgtSource = tgtSource,
                    onPickMedia = ::pickMedia,
                    onNewProject = ::newProject,
                    onExportCube = ::exportCube,
                    onExportHald = ::exportHald,
                )
            }
        }
    }

    override fun onDestroy() {
        refSource?.release(); tgtSource?.release()
        super.onDestroy()
    }
}
