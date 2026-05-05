package com.tiktokscroller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceRecognitionManager(
    private val context: Context,
    private val onSkipDetected: () -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "VoiceRecognition"
        private const val RESTART_DELAY_MS = 300L
        private val SKIP_KEYWORDS = setOf("skip", "skipped", "next", "escape")
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }
        isActive = true
        createAndStartRecognizer()
    }

    fun stop() {
        isActive = false
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
        onListeningStateChanged(false)
    }

    private fun createAndStartRecognizer() {
        if (!isActive) return

        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                1000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L
            )
        }

        try {
            speechRecognizer?.startListening(intent)
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            restartWithDelay()
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                Log.d(TAG, "Recognition error: $errorMsg")
                restartWithDelay()
            }

            override fun onResults(results: Bundle?) {
                processResults(results)
                restartWithDelay()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processResults(partialResults)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return

        for (match in matches) {
            val words = match.lowercase().split(" ")
            if (words.any { it in SKIP_KEYWORDS }) {
                Log.d(TAG, "Skip command detected in: \"$match\"")
                onSkipDetected()
                return
            }
        }
    }

    private fun restartWithDelay() {
        if (!isActive) return
        speechRecognizer?.cancel()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            createAndStartRecognizer()
        }, RESTART_DELAY_MS)
    }
}
