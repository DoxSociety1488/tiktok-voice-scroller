package com.tiktokscroller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        private const val RESTART_DELAY_MS = 50L
        private const val BUSY_RETRY_DELAY_MS = 200L

        private val EXACT_KEYWORDS = setOf(
            "skip", "skipped", "skipping", "skip it",
            "next", "next one", "next video",
            "escape", "scroll", "swipe"
        )

        private val FUZZY_PATTERNS = listOf(
            "skip", "next", "escape", "scroll", "swipe", "скип"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var skipCooldown = false

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
        handler.removeCallbacksAndMessages(null)
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                800L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                800L
            )
        }

        try {
            speechRecognizer?.startListening(intent)
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            restartWithDelay(RESTART_DELAY_MS)
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

                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> BUSY_RETRY_DELAY_MS
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RESTART_DELAY_MS
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 1000L
                    else -> RESTART_DELAY_MS
                }
                restartWithDelay(delay)
            }

            override fun onResults(results: Bundle?) {
                processResults(results)
                restartWithDelay(RESTART_DELAY_MS)
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
            val text = match.lowercase().trim()
            Log.d(TAG, "Heard: \"$text\"")

            if (containsSkipCommand(text)) {
                Log.d(TAG, "Skip command detected in: \"$text\"")
                triggerSkip()
                return
            }
        }
    }

    private fun containsSkipCommand(text: String): Boolean {
        if (text in EXACT_KEYWORDS) return true

        val words = text.split("\\s+".toRegex())
        if (words.any { it in EXACT_KEYWORDS }) return true

        for (pattern in FUZZY_PATTERNS) {
            if (text.contains(pattern)) return true
        }

        return false
    }

    private fun triggerSkip() {
        if (skipCooldown) return
        skipCooldown = true
        onSkipDetected()
        handler.postDelayed({ skipCooldown = false }, 1000)
    }

    private fun restartWithDelay(delay: Long) {
        if (!isActive) return
        speechRecognizer?.cancel()
        handler.postDelayed({
            createAndStartRecognizer()
        }, delay)
    }
}
