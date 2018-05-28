package com.huanghuangnz.lobo.listener

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import org.apache.commons.lang3.StringUtils
import java.util.ArrayList

class SpeechListener(val mSpeechRecognizer: SpeechRecognizer, val mSpeechIntent: Intent, val metTextHint: TextView) : RecognitionListener {

    private val TAG = this.javaClass.canonicalName

    private val VALID_COMMANDS = setOf(
            "芝麻开门"
    )
    private val MATCH_THREADHOLD: Int = 3

    override fun onReadyForSpeech(p0: Bundle?) {
        Log.d(TAG,"onReadyForSpeech")
    }
    override fun onRmsChanged(p0: Float) {
//        Log.d(TAG,"onRmsChanged")
    }
    override fun onBufferReceived(p0: ByteArray?) {
        Log.d(TAG,"onBufferReceived")
    }
    override fun onPartialResults(partialResult: Bundle?) {
        partialResult?.let {
            val results = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG,"onPartialResults: $results")
            processCommand(results)
        }
    }
    override fun onEvent(p0: Int, p1: Bundle?) {
        Log.d(TAG,"onEvent")
    }
    override fun onBeginningOfSpeech() {
        Log.d(TAG,"onBeginningOfSpeech")
    }
    override fun onEndOfSpeech() {
        Log.d(TAG,"onEndOfSpeech")
    }
    override fun onError(errorCode: Int) {
        if ( errorCode == SpeechRecognizer.ERROR_CLIENT || errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ){
            Log.d(TAG,"Client Error: $errorCode")
        } else {
            mSpeechRecognizer.startListening(mSpeechIntent)
        }
    }

    private var exitComment: Boolean = false

    override fun onResults(result: Bundle?) {
        Log.d(TAG,"onResults")
        result?.let {
            val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "get result: $matches")
            processCommand(matches)
            if (!exitComment) {
                mSpeechRecognizer.startListening(mSpeechIntent)
            }
        }
    }

    private fun processCommand(matches: ArrayList<String>) {
        VALID_COMMANDS.forEach { command ->
            matches.forEach { match ->
                if (StringUtils.getLevenshteinDistance(command, match) < MATCH_THREADHOLD){
                    metTextHint.clearComposingText()
                    metTextHint.text = command
                }
            }
        }
    }

    private fun getSpeechRecognizeIntent(partialResult: Boolean = false): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, javaClass
                .`package`.name)

        // Given an hint to the recognizer about what the user is going to say
        //There are two form of language model available
        //1.LANGUAGE_MODEL_WEB_SEARCH : For short phrases
        //2.LANGUAGE_MODEL_FREE_FORM  : If not sure about the words or phrases and its domain.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)

        // Specify how many results you want to receive. The results will be
        // sorted where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 20)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResult)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        return intent
    }

}