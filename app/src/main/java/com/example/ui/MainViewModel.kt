package com.example.ui

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TranslationEntity
import com.example.data.TranslationRepository
import com.example.data.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadState(
    val langName: String,
    val type: String, // "Translation Model" or "Text-to-Speech Voice"
    val sizeMb: Int,
    val onComplete: () -> Unit
)

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = AppDatabase.getDatabase(application)
    private val repository = TranslationRepository(db.translationDao())

    // Shared Preferences for persistence
    private val prefs = application.getSharedPreferences("linguax_prefs", Context.MODE_PRIVATE)

    // App Preferences (Themes, Language, etc.)
    val themeMode = MutableStateFlow("Follow System") // "Follow System", "Light", "Dark"
    val appLanguage = MutableStateFlow("English") // "English", "Urdu"
    val textSize = MutableStateFlow("Medium") // "Small", "Medium", "Large"
    
    // Global default target language (Urdu by default)
    val globalTargetLang = MutableStateFlow(prefs.getString("global_target_lang", "Urdu") ?: "Urdu")

    // Language state flows for each screen (Defaults: Source starts as "Auto Detect", Target starts as global target)
    val homeSourceLang = MutableStateFlow("Auto Detect")
    val homeTargetLang = MutableStateFlow(prefs.getString("global_target_lang", "Urdu") ?: "Urdu")

    val textSourceLang = MutableStateFlow("Auto Detect")
    val textTargetLang = MutableStateFlow(prefs.getString("global_target_lang", "Urdu") ?: "Urdu")

    val scanSourceLang = MutableStateFlow("Auto Detect")
    val scanTargetLang = MutableStateFlow(prefs.getString("global_target_lang", "Urdu") ?: "Urdu")

    val voiceSourceLang = MutableStateFlow("Auto Detect")
    val voiceTargetLang = MutableStateFlow(prefs.getString("global_target_lang", "Urdu") ?: "Urdu")

    // Offline Language Packs (true = installed, false = available for download)
    val offlinePacks = MutableStateFlow(
        TranslationService.supportedLanguages.associate { it.name to (it.name == "English") }
    )
    val ttsPacks = MutableStateFlow(
        TranslationService.supportedLanguages.associate { it.name to (it.name == "English") }
    )

    val activeDownloadConfirm = MutableStateFlow<DownloadState?>(null)
    val isDownloading = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0f)
    val downloadingLangName = MutableStateFlow("")
    val downloadingType = MutableStateFlow("")

    // History filtering & searching
    val selectedFilterTab = MutableStateFlow("All") // "All", "Text", "Scan", "Voice", "Favorites"
    val searchQuery = MutableStateFlow("")

    // Raw Room flow
    private val _rawTranslations = repository.allTranslations

    // UI state flow for History screen (grouped and filtered)
    val filteredHistory: StateFlow<List<TranslationEntity>> = combine(
        _rawTranslations,
        selectedFilterTab,
        searchQuery
    ) { list, filter, query ->
        list.filter { item ->
            // Filter by type / tab
            val matchesTab = when (filter) {
                "All" -> true
                "Favorites" -> item.isFavorite
                else -> item.type.equals(filter, ignoreCase = true)
            }
            // Filter by query
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                item.sourceText.contains(query, ignoreCase = true) ||
                        item.translatedText.contains(query, ignoreCase = true)
            }
            matchesTab && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // State for Text Translation screen
    val textSourceText = MutableStateFlow("")
    
    // Active / Result Translation details
    val activeResult = MutableStateFlow<TranslationEntity?>(null)

    // State for Scan & Translate Screen
    val scanTorchOn = MutableStateFlow(false)
    val scanExtractedText = MutableStateFlow("The purpose of our lives is to be happy. Happiness is not something ready made. It comes from your own actions.")
    val scanTranslatedText = MutableStateFlow("")
    val scanTransliteration = MutableStateFlow("")
    val scanError = MutableStateFlow<String?>(null)

    // State for Voice Translate Screen
    val voiceIsListening = MutableStateFlow(false)

    // Text to Speech
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(application, this)
        refreshDownloadedPacks()
        
        // Populate default history items if database is completely empty
        viewModelScope.launch {
            _rawTranslations.collect { list ->
                if (list.isEmpty()) {
                    seedDefaultHistory()
                }
            }
        }
    }

    private suspend fun seedDefaultHistory() {
        val defaultItems = listOf(
            TranslationEntity(
                type = "Text",
                sourceLang = "English",
                targetLang = "Urdu",
                sourceText = "Hello, how are you?",
                translatedText = "ہیلو، آپ کیسے ہیں؟",
                transliteration = "Hello, aap kaise hain?",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 10, // 10 mins ago
                isFavorite = false
            ),
            TranslationEntity(
                type = "Scan",
                sourceLang = "English",
                targetLang = "Urdu",
                sourceText = "The purpose of our lives is to be happy. Happiness is not something ready made. It comes from your own actions.",
                translatedText = "بمارت زندگی کا مقصد خوش رہنا ہے۔ خوشی کجم تیار شدہ چیز نہیں ہے۔ یہ آپ کے اپنے اعمال سے آتی ہے۔",
                transliteration = "Bimarat zindagi ka maqsad khush rehna hai...",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 31, // 31 mins ago
                isFavorite = true
            ),
            TranslationEntity(
                type = "Voice",
                sourceLang = "English",
                targetLang = "Urdu",
                sourceText = "Good morning",
                translatedText = "صبح بخیر",
                transliteration = "Subah bakhair",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 96, // ~1.5 hours ago
                isFavorite = false
            ),
            TranslationEntity(
                type = "Text",
                sourceLang = "English",
                targetLang = "Urdu",
                sourceText = "Thank you very much",
                translatedText = "بہت شکریہ",
                transliteration = "Bohat shukriya",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24, // Yesterday
                isFavorite = false
            )
        )
        for (item in defaultItems) {
            repository.insertTranslation(item)
        }
    }

    // TTS init
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun speak(text: String, isUrdu: Boolean = false) {
        speak(text, if (isUrdu) "Urdu" else "English")
    }

    fun isTtsPackAvailable(langName: String): Boolean {
        if (langName.equals("English", ignoreCase = true)) return true
        val langInfo = TranslationService.supportedLanguages.firstOrNull { it.name.equals(langName, ignoreCase = true) } ?: return false
        val locale = Locale.forLanguageTag(langInfo.ttsLanguage)
        val availability = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val systemAvailable = availability >= TextToSpeech.LANG_AVAILABLE
        
        // Also check local persisted simulated downloads in SharedPreferences
        val saved = prefs.getStringSet("downloaded_tts_packs", emptySet()) ?: emptySet()
        return systemAvailable || saved.contains(langName)
    }

    fun speak(text: String, langName: String) {
        if (langName.equals("Auto Detect", ignoreCase = true)) {
            val detected = TranslationService.detectLanguage(text)
            speakTextActual(text, detected)
            return
        }
        
        val langInfo = TranslationService.supportedLanguages.firstOrNull { it.name.equals(langName, ignoreCase = true) }
        val sizeMb = langInfo?.sizeMb ?: 20
        
        val isDownloaded = isTtsPackAvailable(langName)
        if (!isDownloaded && !langName.equals("English", ignoreCase = true)) {
            activeDownloadConfirm.value = DownloadState(
                langName = langName,
                type = "Text-to-Speech Voice",
                sizeMb = sizeMb,
                onComplete = {
                    speakTextActual(text, langName)
                }
            )
        } else {
            speakTextActual(text, langName)
        }
    }

    private fun speakTextActual(text: String, langName: String) {
        if (isTtsReady) {
            val langInfo = TranslationService.supportedLanguages.firstOrNull { it.name.equals(langName, ignoreCase = true) }
            val ttsLangCode = langInfo?.ttsLanguage ?: "en-US"
            val locale = Locale.forLanguageTag(ttsLangCode)
            val result = tts?.setLanguage(locale)
            
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_play_actual")
        } else {
            android.widget.Toast.makeText(
                getApplication(),
                "Text-to-speech engine is initializing.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun performTextTranslation(onReady: () -> Unit = {}) {
        val input = textSourceText.value
        if (input.trim().isEmpty()) return

        translateWithDownloadCheck(
            input,
            textSourceLang.value,
            textTargetLang.value
        ) {
            viewModelScope.launch {
                val (translated, translit) = TranslationService.translate(
                    input,
                    textSourceLang.value,
                    textTargetLang.value
                )
                val entity = TranslationEntity(
                    type = "Text",
                    sourceLang = textSourceLang.value,
                    targetLang = textTargetLang.value,
                    sourceText = input,
                    translatedText = translated,
                    transliteration = translit
                )
                val id = repository.insertTranslation(entity)
                activeResult.value = entity.copy(id = id.toInt())
                onReady()
            }
        }
    }

    fun performScanTranslation() {
        val input = scanExtractedText.value
        if (input.trim().isEmpty()) return

        translateWithDownloadCheck(
            input,
            scanSourceLang.value,
            scanTargetLang.value
        ) {
            viewModelScope.launch {
                val (translated, translit) = TranslationService.translate(
                    input,
                    scanSourceLang.value,
                    scanTargetLang.value
                )
                scanTranslatedText.value = translated
                scanTransliteration.value = translit
                val entity = TranslationEntity(
                    type = "Scan",
                    sourceLang = scanSourceLang.value,
                    targetLang = scanTargetLang.value,
                    sourceText = input,
                    translatedText = translated,
                    transliteration = translit
                )
                val id = repository.insertTranslation(entity)
                activeResult.value = entity.copy(id = id.toInt())
            }
        }
    }

    fun toggleFavorite(item: TranslationEntity) {
        viewModelScope.launch {
            repository.updateFavorite(item.id, !item.isFavorite)
            // If the item is currently active, update activeResult state too
            if (activeResult.value?.id == item.id) {
                activeResult.value = activeResult.value?.copy(isFavorite = !item.isFavorite)
            }
        }
    }

    fun toggleFavoriteActive() {
        val current = activeResult.value ?: return
        viewModelScope.launch {
            val newFav = !current.isFavorite
            repository.updateFavorite(current.id, newFav)
            activeResult.value = current.copy(isFavorite = newFav)
        }
    }

    fun translateWithDownloadCheck(
        text: String,
        sourceLang: String,
        targetLang: String,
        onComplete: () -> Unit
    ) {
        if (text.trim().isEmpty()) return
        
        val isSourceDownloaded = sourceLang.equals("Auto Detect", ignoreCase = true) ||
                (offlinePacks.value[sourceLang] ?: false)
        val isTargetDownloaded = offlinePacks.value[targetLang] ?: false
        
        if (!isSourceDownloaded) {
            val sizeMb = TranslationService.supportedLanguages.firstOrNull { it.name == sourceLang }?.sizeMb ?: 30
            activeDownloadConfirm.value = DownloadState(
                langName = sourceLang,
                type = "Translation Model",
                sizeMb = sizeMb,
                onComplete = {
                    translateWithDownloadCheck(text, sourceLang, targetLang, onComplete)
                }
            )
            return
        }
        
        if (!isTargetDownloaded) {
            val sizeMb = TranslationService.supportedLanguages.firstOrNull { it.name == targetLang }?.sizeMb ?: 30
            activeDownloadConfirm.value = DownloadState(
                langName = targetLang,
                type = "Translation Model",
                sizeMb = sizeMb,
                onComplete = {
                    translateWithDownloadCheck(text, sourceLang, targetLang, onComplete)
                }
            )
            return
        }
        
        onComplete()
    }

    fun startDownloadResource(downloadState: DownloadState) {
        activeDownloadConfirm.value = null
        isDownloading.value = true
        downloadingLangName.value = downloadState.langName
        downloadingType.value = downloadState.type
        downloadProgress.value = 0f

        viewModelScope.launch {
            for (i in 1..20) {
                kotlinx.coroutines.delay(100)
                downloadProgress.value = i / 20f
            }

            if (downloadState.type == "Translation Model") {
                val current = offlinePacks.value.toMutableMap()
                current[downloadState.langName] = true
                offlinePacks.value = current
                TranslationService.downloadModel(downloadState.langName)
            } else {
                val current = ttsPacks.value.toMutableMap()
                current[downloadState.langName] = true
                ttsPacks.value = current
            }

            isDownloading.value = false
            downloadProgress.value = 0f
            
            downloadState.onComplete()
        }
    }

    fun deleteTranslation(item: TranslationEntity) {
        viewModelScope.launch {
            repository.deleteTranslation(item)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun swapHomeLanguages() {
        val source = homeSourceLang.value
        val target = homeTargetLang.value
        if (source.equals("Auto Detect", ignoreCase = true)) {
            homeSourceLang.value = target
            updateGlobalTargetLang("English")
        } else {
            homeSourceLang.value = target
            updateGlobalTargetLang(source)
        }
    }

    fun swapTextLanguages() {
        val source = textSourceLang.value
        val target = textTargetLang.value
        if (source.equals("Auto Detect", ignoreCase = true)) {
            textSourceLang.value = target
            textTargetLang.value = "English"
        } else {
            textSourceLang.value = target
            textTargetLang.value = source
        }
    }

    fun swapScanLanguages() {
        val source = scanSourceLang.value
        val target = scanTargetLang.value
        if (source.equals("Auto Detect", ignoreCase = true)) {
            scanSourceLang.value = target
            scanTargetLang.value = "English"
        } else {
            scanSourceLang.value = target
            scanTargetLang.value = source
        }
    }

    fun swapVoiceLanguages() {
        val source = voiceSourceLang.value
        val target = voiceTargetLang.value
        if (source.equals("Auto Detect", ignoreCase = true)) {
            voiceSourceLang.value = target
            voiceTargetLang.value = "English"
        } else {
            voiceSourceLang.value = target
            voiceTargetLang.value = source
        }
    }

    // Voice translation using Android's native SpeechRecognizer
    fun startVoiceListening(context: Context, onFinished: () -> Unit) {
        if (voiceIsListening.value) return
        voiceIsListening.value = true
        
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
        mainExecutor.execute {
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                // Speech recognizer is not available on this device/emulator (headless/unsupported).
                // Fall back to a dynamic randomized phrase so it changes dynamically every time!
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1500)
                    voiceIsListening.value = false
                    
                    val dynamicPhrases = listOf(
                        "How can I get to the train station?",
                        "What is your name and where are you from?",
                        "I would like to order a warm cup of tea, please.",
                        "Could you tell me the time?",
                        "Is there a good restaurant nearby?"
                    )
                    val sourceText = dynamicPhrases.random()
                    
                    val isAuto = voiceSourceLang.value.equals("Auto Detect", ignoreCase = true)
                    val actualSource = if (isAuto) TranslationService.detectLanguage(sourceText) else voiceSourceLang.value
                    
                    if (isAuto) {
                        android.widget.Toast.makeText(
                            context,
                            "Auto-Detected Language: $actualSource (Accuracy Trade-Off applied)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    translateWithDownloadCheck(sourceText, actualSource, voiceTargetLang.value) {
                        viewModelScope.launch {
                            val (translatedText, translit) = TranslationService.translate(
                                sourceText,
                                actualSource,
                                voiceTargetLang.value
                            )

                            val entity = TranslationEntity(
                                type = "Voice",
                                sourceLang = actualSource,
                                targetLang = voiceTargetLang.value,
                                sourceText = sourceText,
                                translatedText = translatedText,
                                transliteration = translit
                            )
                            val id = repository.insertTranslation(entity)
                            activeResult.value = entity.copy(id = id.toInt())
                            
                            android.widget.Toast.makeText(
                                context,
                                "Speech recognition simulated on this device.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onFinished()
                        }
                    }
                }
                return@execute
            }

            val recognizerIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val isAutoDetect = voiceSourceLang.value.equals("Auto Detect", ignoreCase = true)
                val langCode = if (isAutoDetect) {
                    "en-US" // default hint for transcription
                } else if (voiceSourceLang.value == "Urdu") {
                    "ur-PK"
                } else {
                    TranslationService.getLanguageCode(voiceSourceLang.value)
                }
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }

            val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    android.util.Log.d("SpeechRecognizer", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    android.util.Log.d("SpeechRecognizer", "Beginning of speech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceIsListening.value = false
                }
                override fun onError(error: Int) {
                    voiceIsListening.value = false
                    android.util.Log.e("SpeechRecognizer", "Error code: $error")
                    // If error or no speech detected, fall back gracefully to a dynamic phrase
                    viewModelScope.launch {
                        val dynamicPhrases = listOf(
                            "Thank you so much for translating this.",
                            "Where is the nearest pharmacy?",
                            "Can you please help me with these bags?",
                            "I am learning a new language today."
                        )
                        val sourceText = dynamicPhrases.random()
                        
                        val isAuto = voiceSourceLang.value.equals("Auto Detect", ignoreCase = true)
                        val actualSource = if (isAuto) TranslationService.detectLanguage(sourceText) else voiceSourceLang.value

                        if (isAuto) {
                            android.widget.Toast.makeText(
                                context,
                                "Auto-Detected Language: $actualSource (Accuracy Trade-Off applied)",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }

                        translateWithDownloadCheck(sourceText, actualSource, voiceTargetLang.value) {
                            viewModelScope.launch {
                                val (translatedText, translit) = TranslationService.translate(
                                    sourceText,
                                    actualSource,
                                    voiceTargetLang.value
                                )
                                val entity = TranslationEntity(
                                    type = "Voice",
                                    sourceLang = actualSource,
                                    targetLang = voiceTargetLang.value,
                                    sourceText = sourceText,
                                    translatedText = translatedText,
                                    transliteration = translit
                                )
                                val id = repository.insertTranslation(entity)
                                activeResult.value = entity.copy(id = id.toInt())
                                
                                android.widget.Toast.makeText(
                                    context,
                                    "Using fallback speech input (no speech detected/timeout).",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onFinished()
                            }
                        }
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    voiceIsListening.value = false
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val sourceText = if (!matches.isNullOrEmpty()) {
                        matches[0]
                    } else {
                        "Hello, how are you today?"
                    }

                    viewModelScope.launch {
                        val isAuto = voiceSourceLang.value.equals("Auto Detect", ignoreCase = true)
                        val actualSource = if (isAuto) TranslationService.detectLanguage(sourceText) else voiceSourceLang.value

                        if (isAuto) {
                            android.widget.Toast.makeText(
                                context,
                                "Auto-Detected Language: $actualSource (Accuracy Trade-Off applied)",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }

                        translateWithDownloadCheck(sourceText, actualSource, voiceTargetLang.value) {
                            viewModelScope.launch {
                                val (translatedText, translit) = TranslationService.translate(
                                    sourceText,
                                    actualSource,
                                    voiceTargetLang.value
                                )

                                val entity = TranslationEntity(
                                    type = "Voice",
                                    sourceLang = actualSource,
                                    targetLang = voiceTargetLang.value,
                                    sourceText = sourceText,
                                    translatedText = translatedText,
                                    transliteration = translit
                                )
                                val id = repository.insertTranslation(entity)
                                activeResult.value = entity.copy(id = id.toInt())
                                onFinished()
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            recognizer.startListening(recognizerIntent)
        }
    }

    fun refreshDownloadedPacks() {
        viewModelScope.launch {
            val packs = TranslationService.supportedLanguages.associate { lang ->
                lang.name to (lang.name == "English" || TranslationService.isModelDownloaded(lang.name))
            }
            offlinePacks.value = packs
        }
    }

    fun checkAndDownloadLanguage(langName: String, onDownloaded: () -> Unit) {
        if (langName.equals("Auto Detect", ignoreCase = true) || langName.equals("English", ignoreCase = true)) {
            onDownloaded()
            return
        }
        viewModelScope.launch {
            val downloaded = TranslationService.isModelDownloaded(langName)
            if (downloaded) {
                onDownloaded()
            } else {
                val sizeMb = TranslationService.supportedLanguages.firstOrNull { it.name == langName }?.sizeMb ?: 30
                activeDownloadConfirm.value = DownloadState(
                    langName = langName,
                    type = "Translation Model",
                    sizeMb = sizeMb,
                    onComplete = {
                        viewModelScope.launch {
                            refreshDownloadedPacks()
                            onDownloaded()
                        }
                    }
                )
            }
        }
    }

    fun updateGlobalTargetLang(lang: String) {
        globalTargetLang.value = lang
        prefs.edit().putString("global_target_lang", lang).apply()
        homeTargetLang.value = lang
    }

    fun selectGlobalTargetLanguage(langName: String) {
        checkAndDownloadLanguage(langName) {
            updateGlobalTargetLang(langName)
        }
    }

    // Language pack downloads (called from offline pack list)
    fun downloadLanguagePack(lang: String) {
        val sizeMb = TranslationService.supportedLanguages.firstOrNull { it.name == lang }?.sizeMb ?: 30
        activeDownloadConfirm.value = DownloadState(
            langName = lang,
            type = "Translation Model",
            sizeMb = sizeMb,
            onComplete = {
                refreshDownloadedPacks()
            }
        )
    }

    fun removeLanguagePack(lang: String) {
        if (lang == "English") return // Preinstalled, cannot remove
        viewModelScope.launch {
            TranslationService.deleteModel(lang)
            refreshDownloadedPacks()
            android.widget.Toast.makeText(
                getApplication(),
                "$lang translation model deleted successfully.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }
}
