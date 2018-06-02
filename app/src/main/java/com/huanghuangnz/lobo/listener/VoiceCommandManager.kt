package com.huanghuangnz.lobo.listener

import android.os.AsyncTask
import android.util.Log
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import java.io.File
import java.io.IOException

class VoiceCommandManager constructor(val commandListener: CommandListener): edu.cmu.pocketsphinx.RecognitionListener{

    val TAG = this.javaClass.canonicalName

    private val kwSearch = "keyWord"
    private val sSearch = "secret"
    private val secretPhrase = "嘛哩嘛哩哄"
    private val acceptableActions = listOf("芝麻开灯", "土豆开门", "芝麻关门", "土豆关灯")
    private val acceptableAssistant = listOf("小林小林", "小黄小黄", "林小曦")


    private lateinit var currentAction: String
    private lateinit var recognizer: SpeechRecognizer

    override fun onResult(hypothesis: Hypothesis?) {
        Log.d(TAG, "onResult: ${hypothesis?.hypstr}")
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        if (hypothesis == null) {
            return
        }
        Log.d(TAG, "onPartialResult: ${hypothesis.hypstr}")
        this.onHandleMatch(hypothesis.hypstr)
    }

    private fun onHandleMatch(hypstr: String) {
        val keyWord = hypstr.split(" ")[0]

        when {
        // to kw search
            secretPhrase == keyWord -> {
                commandListener.onActionConfirm(currentAction)
                switchSearch(kwSearch)
            }
        // to secret search
            acceptableActions.contains(keyWord) -> {
                currentAction = keyWord
                commandListener.onAction(keyWord)
                switchSearch(sSearch)
            }
        // continue kw search
            acceptableAssistant.contains(keyWord) -> {
                commandListener.onWakeUp(keyWord)
                switchSearch(kwSearch)
            }
            else -> switchSearch(kwSearch)
        }

    }

    override fun onTimeout() {
        switchSearch(kwSearch)
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
    }

    override fun onError(p0: Exception?) {
        Log.d(TAG, "onError")
    }

    fun init(assetDir: File){
        SetupTask(assetDir).execute()
    }


    inner class SetupTask internal constructor(val assetDir: File) : AsyncTask<Void, Void, java.lang.Exception>() {

        override fun doInBackground(vararg params: Void): java.lang.Exception? {
            try {
                this@VoiceCommandManager.setupRecognizer(assetDir)
            } catch (e: IOException) {
                return e
            }

            return null
        }

        override fun onPostExecute(result: java.lang.Exception?) {
            if (result != null) {
                commandListener.onErrorSetup()
            } else {
                this@VoiceCommandManager.switchSearch(kwSearch)
            }
        }
    }

    private fun switchSearch(searchName: String) {
        recognizer.stop()

        if (kwSearch == searchName){
            commandListener.onListening()
            recognizer.startListening(searchName)
        } else {
            recognizer.startListening(searchName, 5000)
        }
    }

    @Throws(IOException::class)
    private fun setupRecognizer(assetsDir: File) {

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(assetsDir, "cn-zh-ptm"))
                .setDictionary(File(assetsDir, "zh_broadcastnews_utf8.dic"))
                .setString("-mllr", File(assetsDir, "mllr_matrix").path)
                .recognizer

        recognizer.addListener(this)

        // Create keyword-activation search.
        val keyWordFile = File(assetsDir, "keywords")
        recognizer.addKeywordSearch(kwSearch, keyWordFile)

        recognizer.addKeyphraseSearch(sSearch, secretPhrase)

    }

    fun destroy() {
        if (recognizer != null) {
            recognizer.cancel()
            recognizer.shutdown()
        }
    }

}

interface CommandListener{
    fun onAction(action: String)

    fun onWakeUp(assistant: String)

    fun onActionConfirm(action: String)

    fun onErrorSetup()
    fun onListening()
}