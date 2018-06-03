package com.huanghuangnz.lobo.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

const val REQUEST_TEST_TO_SPEECH_ID = "REQUESTID"

class VoiceSynthesizer constructor(val context: Context): TextToSpeech.OnInitListener {

    private val TAG = this.javaClass.canonicalName

    private lateinit var tts: TextToSpeech

    fun init(){
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            val result = tts.setLanguage(tts.defaultLanguage)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "This Language is not supported")
            } else {
                speak("语音系统初始化成功，萝卜欢迎您！")
            }

        } else {
            Log.e("TTS", "Initilization Failed!")
        }
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, REQUEST_TEST_TO_SPEECH_ID)
    }

}
