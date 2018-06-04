package com.huanghuangnz.lobo.voicerecognization

import android.os.AsyncTask
import android.util.Log
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import java.io.File
import java.io.IOException

data class Action(
        val name: String,
        val isAssistant: Boolean,
        val isSecured: Boolean
)

class VoiceCommandManager constructor(val commandListener: CommandListener): edu.cmu.pocketsphinx.RecognitionListener{

    val TAG = this.javaClass.canonicalName

    val kwSearch = "keyWord"
    val sSearch = "secret"
    private val secretPhrase = "嘛哩嘛哩哄"
    private val actions = listOf(
            Action("芝麻开灯", false, false),
            Action("土豆开门", false, true),
            Action("芝麻关门", false, true),
            Action("土豆关灯", false, false),
            Action("小林小林", true, false),
            Action("小黄小黄", true, false),
            Action("林小曦", true, false)
    )
    var inited = false

    private lateinit var currentAction: Action
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

        if (secretPhrase == keyWord && recognizer.searchName == sSearch) {
            commandListener.onActionConfirm(currentAction)
            switchSearch(kwSearch)
            return
        }
        // to secret search
        actions.find { it.name == keyWord }?.let {
            if (it.isAssistant) {
                commandListener.onWakeUp(it)
                switchSearch(kwSearch)
            }
            else if (it.isSecured) {
                currentAction = it
                commandListener.onPendingConfirm(it)
                switchSearch(sSearch)
            }
            else if (!it.isSecured) {
                commandListener.onActionConfirm(it)
                switchSearch(kwSearch)
            }
            else switchSearch(kwSearch)
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

    fun switchSearch(searchName: String) {
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

        inited = true
    }

    fun destroy() {
        if (recognizer != null) {
            recognizer.cancel()
            recognizer.shutdown()
        }
    }

}

interface CommandListener{

    fun onPendingConfirm(action: Action)

    fun onWakeUp(assistant: Action)

    fun onActionConfirm(action: Action)

    fun onErrorSetup()

    fun onListening()
}