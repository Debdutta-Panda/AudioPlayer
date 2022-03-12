package com.algogence.audioplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.media.audiofx.Visualizer.OnDataCaptureListener
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.algogence.audioplayer.ui.theme.AudioPlayerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val currentTime = mutableStateOf(0)
    private val totalTime = mutableStateOf(0)
    private val waveForm = mutableStateOf(ByteArray(0))
    private var permitted = false
    val AUDIO_PERMISSION_REQUEST_CODE = 102

    val WRITE_EXTERNAL_STORAGE_PERMS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )
    private val audioProgress = mutableStateOf(0f)
    private val audioPlayer = AudioPlayer()
    class AudioPlayer{
        private var visualizer: Visualizer? = null
        private var url: String? = null
        private var mediaPlayer: MediaPlayer? = null
        private var preparing = false
        private var prepared = false
        fun play(url: String){
            if(prepared||preparing){
                stop()
            }
            this.url = url
            CoroutineScope(Dispatchers.IO).launch{
                assureMediaPlayer()
                setupStreamMode()
                startPlaying()
            }
        }

        fun resume(){
            if(prepared&&mediaPlayer?.isPlaying==false){
                mediaPlayer?.start()
            }
        }

        fun pause(){
            if(mediaPlayer?.isPlaying==true){
                mediaPlayer?.pause()
            }
        }

        fun stop() {
            releaseVisualizer()
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
            preparing = false
            prepared = false
            listeners.forEach {
                it(
                    Progress(
                        ByteArray(0),
                        progress = 0,
                        total = 0,
                        progressFactor = 0f,
                        samplingRate = 0
                    )
                )
            }
        }

        private fun releaseVisualizer() {
            if (visualizer == null) return
            visualizer!!.release()
        }

        private fun startPlaying() {
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        }

        private fun setupStreamMode() {
            if(preparing){
               return
            }
            preparing = true
            prepared = false
            mediaPlayer?.setOnPreparedListener {
                prepared = true
                preparing = false
                setupVisualizer()
            }
            mediaPlayer?.setOnBufferingUpdateListener { mediaPlayer, i ->
                Log.d("buffering_update",i.toString())
            }
            mediaPlayer?.setOnCompletionListener {
                stop()
            }
            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }

        private fun setupVisualizer() {
            visualizer = mediaPlayer?.audioSessionId?.let { Visualizer(it) }
            visualizer?.enabled = false
            visualizer?.captureSize = Visualizer.getCaptureSizeRange()[1]

            visualizer?.setDataCaptureListener(object : OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer, bytes: ByteArray,
                    samplingRate: Int
                ) {
                    onWaveForm(bytes,samplingRate)
                }

                override fun onFftDataCapture(
                    visualizer: Visualizer, bytes: ByteArray,
                    samplingRate: Int
                ) {
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false)

            visualizer?.enabled = true
        }

        private fun onWaveForm(bytes: ByteArray, samplingRate: Int) {
            val progress = mediaPlayer?.currentPosition
            val total = mediaPlayer?.duration
            val percentage: Float = when{
                progress==null->0f
                total==null->0f
                total==0->0f
                else->progress.toFloat()/total.toFloat()
            }
            onProgress(bytes,samplingRate,progress,total,percentage)
        }
        data class Progress(
            val waveForm: ByteArray,
            val samplingRate: Int,
            val progress: Int?,
            val total: Int?,
            val progressFactor: Float
        )

        private val listeners = mutableListOf<(Progress)->Unit>()

        fun addListener(listener: (Progress)->Unit){
            listeners.add(listener)
        }
        fun removeListener(listener: (Progress)->Unit){
            listeners.remove(listener)
        }

        private fun onProgress(
            bytes: ByteArray,
            samplingRate: Int,
            progress: Int?,
            total: Int?,
            percentage: Float
        ) {
            val progress = Progress(
                bytes,
                samplingRate,
                progress,
                total,
                percentage
            )
            listeners.forEach {
                it(progress)
            }
        }

        private fun assureMediaPlayer() {
            if(mediaPlayer==null){
                mediaPlayer = MediaPlayer()
            }
        }

        fun removeListeners() {
            listeners.clear()
        }

        fun seekToFactor(it: Float) {
            mediaPlayer?.seekTo((it*(mediaPlayer?.duration?:0)).toInt())
        }
    }
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            AudioPlayerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ){
                        Button(
                            onClick = {
                                onClickNew()
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text("PlayNew")
                        }
                        Spacer(modifier = Modifier.size(24.dp))
                        Button(
                            onClick = {
                                onClickPause()
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text("Pause")
                        }
                        Spacer(modifier = Modifier.size(24.dp))
                        Button(
                            onClick = {
                                onClickResume()
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text("Resume")
                        }
                        Spacer(modifier = Modifier.size(24.dp))
                        Button(
                            onClick = {
                                onClickStop()
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text("Stop")
                        }
                        Spacer(modifier = Modifier.size(24.dp))
                        Slider(
                            modifier = Modifier.fillMaxWidth(),
                            value = audioProgress.value,
                            onValueChange = {
                                audioProgress.value = it
                                seekToFactor(it)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Gray,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray,
                            )
                        )
                        Row(modifier = Modifier.wrapContentSize()){
                            Text(currentTime.value.toString())
                            Text("/")
                            Text(totalTime.value.toString())
                        }
                        val points = remember {
                            mutableStateOf<FloatArray>(FloatArray(0))
                        }
                        val strokeWidth = 2f
                        val paint = remember { Paint()}
                        Canvas(
                            modifier = Modifier.size(100.dp),
                            contentDescription = ""
                        ){

                            drawContext.canvas.nativeCanvas.apply {
                                val bytes = waveForm.value
                                if (bytes != null) {
                                    if (points == null || points.value.size < bytes.size * 4) {
                                        points.value = FloatArray(bytes.size * 4)
                                    }
                                    paint.setStrokeWidth(getHeight() * strokeWidth)
                                    val rect = Rect()
                                    rect.set(0, 0, getWidth(), getHeight())
                                    for (i in 0 until bytes.size - 1) {
                                        points.value[i * 4] = (rect.width() * i / (bytes.size - 1)).toFloat()
                                        points.value[i * 4 + 1] = ((rect.height() / 2).toFloat()
                                                + (bytes.get(i) + 128).toByte() * (rect.height() / 3) / 128)
                                        points.value[i * 4 + 2] =
                                            (rect.width() * (i + 1) / (bytes.size - 1)).toFloat()
                                        points.value[i * 4 + 3] = ((rect.height() / 2
                                                + (bytes.get(i + 1) + 128).toByte() * (rect.height() / 3) / 128)).toFloat()
                                    }
                                    drawLines(points.value, paint)
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private fun seekToFactor(it: Float) {
        audioPlayer.seekToFactor(it)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                WRITE_EXTERNAL_STORAGE_PERMS,
                AUDIO_PERMISSION_REQUEST_CODE
            )
        } else {
            permitted = true
        }
    }

    private fun onClickStop() {
        if(!permitted){
            return
        }
        audioPlayer.stop()
    }

    private fun onClickResume() {
        if(!permitted){
            return
        }
        audioPlayer.resume()
    }

    private fun onClickPause() {
        if(!permitted){
            return
        }
        audioPlayer.pause()
    }

    private fun onClickNew() {
        if(!permitted){
            return
        }
        audioPlayer.play("https://app.learnpea.com/public/Teri-Chahat-Ke-Deewane.mp3")
        audioPlayer.removeListeners()
        audioPlayer.addListener {
            audioProgress.value = it.progressFactor
            currentTime.value = it.progress?:0
            totalTime.value = it.total?:0
            waveForm.value = it.waveForm
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                permitted = true
            }
        }
    }
}