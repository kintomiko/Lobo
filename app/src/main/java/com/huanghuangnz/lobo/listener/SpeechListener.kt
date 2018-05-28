package com.huanghuangnz.lobo.listener

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.EditText
import org.apache.commons.lang3.StringUtils
import java.util.ArrayList

class SpeechListener(val mSpeechRecognizer: SpeechRecognizer, val mSpeechIntent: Intent, val metTextHint: EditText) : RecognitionListener {

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
                    metTextHint.text.clear()
                    metTextHint.text.insert(0, command)
                }
            }
        }
    }

}