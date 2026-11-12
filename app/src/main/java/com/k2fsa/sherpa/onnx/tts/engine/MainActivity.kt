@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import PreferenceHelper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.tts.engine.ui.theme.SherpaOnnxTtsEngineTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SherpaOnnxTtsEngineTheme {
                val context = LocalContext.current
                val preferences = remember(context) { PreferenceHelper(context) }
                val scope = rememberCoroutineScope()
                var selectedVoiceId by rememberSaveable {
                    mutableStateOf(RomanianVoice.fromId(preferences.getVoice()).id)
                }
                var text by rememberSaveable {
                    mutableStateOf("Bună ziua! Acesta este un test al vocii românești.")
                }
                var isBusy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("Alege o voce și apasă „Testează vocea”.") }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("SherpaOnnx TTS Română") })
                    }
                ) { contentPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "Motor de sinteză vocală românească",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Alege vocea",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    RomanianVoice.values().forEach { voice ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isBusy) {
                                                    selectedVoiceId = voice.id
                                                    preferences.setVoice(voice.id)
                                                    status = "Voce selectată: ${voice.displayName}"
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = selectedVoiceId == voice.id,
                                                enabled = !isBusy,
                                                onClick = {
                                                    selectedVoiceId = voice.id
                                                    preferences.setVoice(voice.id)
                                                    status = "Voce selectată: ${voice.displayName}"
                                                },
                                            )
                                            Text(
                                                text = voice.displayName,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("Text de citit") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 8,
                                enabled = !isBusy,
                            )

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy,
                                onClick = {
                                    val textToSpeak = text.trim()
                                    if (textToSpeak.isEmpty()) {
                                        status = "Introdu textul pe care vrei să-l asculți."
                                        return@Button
                                    }

                                    val voiceId = selectedVoiceId
                                    isBusy = true
                                    status = "Se încarcă modelul și se generează vocea…"
                                    scope.launch {
                                        try {
                                            val audio = withContext(Dispatchers.IO) {
                                                TtsEngine.selectVoice(context, voiceId)
                                                val synthesizer = TtsEngine.tts
                                                    ?: error("Motorul TTS nu a creat sintetizatorul.")
                                                val config = GenerationConfig(
                                                    sid = TtsEngine.speakerId,
                                                    speed = TtsEngine.speed,
                                                )
                                                config.phonemeCodepoints =
                                                    TtsEngine.phonemize(textToSpeak)
                                                synthesizer.generateWithConfig(
                                                    text = "",
                                                    config = config,
                                                )
                                            }
                                            status = "Se redă vocea…"
                                            playAudio(audio)
                                            status = "Redare terminată."
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to generate voice preview", e)
                                            status = e.localizedMessage
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { "Eroare la redarea vocii: $it" }
                                                ?: "Nu s-a putut genera vocea. Verifică modelul și bibliotecile native."
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                },
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (isBusy) "Se procesează…" else "Testează vocea")
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy,
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent("com.android.settings.TTS_SETTINGS")
                                        )
                                    } catch (e: ActivityNotFoundException) {
                                        Log.e(TAG, "Android TTS settings activity is unavailable", e)
                                        status = "Setările TTS Android nu sunt disponibile pe acest dispozitiv."
                                    }
                                },
                            ) {
                                Text("Setări TTS Android")
                            }

                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun playAudio(audio: GeneratedAudio) = withContext(Dispatchers.IO) {
    check(audio.samples.isNotEmpty()) { "Modelul nu a generat audio." }
    val samples = ShortArray(audio.samples.size) { index ->
        (audio.samples[index].coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).toInt().toShort()
    }
    val channelMask = AudioFormat.CHANNEL_OUT_MONO
    val encoding = AudioFormat.ENCODING_PCM_16BIT
    val minBufferSize = AudioTrack.getMinBufferSize(audio.sampleRate, channelMask, encoding)
    check(minBufferSize > 0) { "Nu s-a putut configura ieșirea audio." }

    val attributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()
    val format = AudioFormat.Builder()
        .setEncoding(encoding)
        .setChannelMask(channelMask)
        .setSampleRate(audio.sampleRate)
        .build()
    val track = AudioTrack(
        attributes,
        format,
        maxOf(minBufferSize, 4096),
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE,
    )

    try {
        check(track.state == AudioTrack.STATE_INITIALIZED) { "Nu s-a putut inițializa redarea audio." }
        track.play()
        var offset = 0
        while (offset < samples.size) {
            val written = track.write(samples, offset, minOf(2048, samples.size - offset))
            check(written > 0) { "Redarea audio a eșuat (cod $written)." }
            offset += written
        }
        while (track.playbackHeadPosition < samples.size) {
            delay(20)
        }
    } finally {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.stop()
        }
        track.release()
    }
}
