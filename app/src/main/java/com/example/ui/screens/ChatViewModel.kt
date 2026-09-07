package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.auth.UserProfile
import com.example.data.local.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationEntity
import com.example.data.remote.GeminiRepository
import com.example.ui.i18n.AppLanguage
import com.example.voice.VoiceAssistantState
import com.example.voice.VoiceLanguage
import com.example.voice.VoiceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.voice.VoiceProviderType
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.chatDao()
    private val repository = GeminiRepository(application)
    private val authRepository = AuthRepository(application)
    private val sharedPrefs = application.getSharedPreferences("samadhan_ai_prefs", Context.MODE_PRIVATE)

    // Voice & Conversational Assistant Manager
    val voiceManager = VoiceManager(application)
    val unifiedVoiceService = voiceManager.unifiedVoiceService
    val voiceState: StateFlow<VoiceAssistantState> = voiceManager.voiceState
    val isSpeaking: StateFlow<Boolean> = unifiedVoiceService.isSpeaking
    val activeVoiceProvider: StateFlow<VoiceProviderType> = unifiedVoiceService.activeProvider
    val activeProviderLabel: StateFlow<String> = unifiedVoiceService.activeProviderLabel
    val lastFallbackReason: StateFlow<String?> = unifiedVoiceService.lastFallbackReason
    val rmsDb: StateFlow<Float> = voiceManager.rmsDb
    val liveSpokenText: StateFlow<String> = voiceManager.liveSpokenText
    val voiceErrorMessage: StateFlow<String?> = voiceManager.errorMessage
    val selectedVoiceLanguage: StateFlow<VoiceLanguage> = voiceManager.selectedLanguage

    // Continuous conversational voice mode (back-and-forth conversation)
    private val _isContinuousVoiceMode = MutableStateFlow(false)
    val isContinuousVoiceMode: StateFlow<Boolean> = _isContinuousVoiceMode.asStateFlow()

    // AI voice responses enabled / disabled toggle
    private val _isTtsEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("is_tts_enabled", true)
    )
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    // User Profile
    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Conversations from DB
    val conversations: StateFlow<List<ConversationEntity>> = dao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active conversation ID
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    // Messages for the active conversation
    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    // Input state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    // Image Generation Mode
    private val _isImageMode = MutableStateFlow(false)
    val isImageMode: StateFlow<Boolean> = _isImageMode.asStateFlow()

    // Dictation state
    val isDictating: StateFlow<Boolean> = voiceManager.isDictating

    // Live AI Voice Conversation state
    private val _isLiveVoiceDialogOpen = MutableStateFlow(false)
    val isLiveVoiceDialogOpen: StateFlow<Boolean> = _isLiveVoiceDialogOpen.asStateFlow()

    private val _lastAiResponse = MutableStateFlow("")
    val lastAiResponse: StateFlow<String> = _lastAiResponse.asStateFlow()

    // Generation state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Settings
    private val _selectedModel: MutableStateFlow<String> = run {
        val saved = sharedPrefs.getString("selected_model", GeminiRepository.DEFAULT_MODEL)
        val sanitized = if (saved == null || saved.contains("2.5") || saved.isBlank()) {
            sharedPrefs.edit().putString("selected_model", GeminiRepository.DEFAULT_MODEL).apply()
            GeminiRepository.DEFAULT_MODEL
        } else {
            saved.removePrefix("models/")
        }
        MutableStateFlow(sanitized)
    }
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _customApiKey = MutableStateFlow(
        sharedPrefs.getString("custom_api_key", "") ?: ""
    )
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Interface Language (English / Hindi)
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(sharedPrefs.getString("app_language", "en"))
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        sharedPrefs.edit().putString("app_language", language.code).apply()
    }

    private var messageCollectionJob: Job? = null
    private var generationJob: Job? = null

    init {
        val initialCustomKey = sharedPrefs.getString("custom_api_key", null)
        if (!initialCustomKey.isNullOrBlank()) {
            voiceManager.setCustomApiKey(initialCustomKey)
        }

        // Setup default voice callbacks without artificial waiting delays
        voiceManager.setCallbacks(
            onSpeechResult = { recognizedText ->
                sendMessage(customPrompt = recognizedText, fromVoice = true)
            },
            onTtsComplete = {
                if (_isContinuousVoiceMode.value && !_isGenerating.value) {
                    startVoiceListening(continuous = true)
                }
            }
        )

        // Observe conversation changes
        viewModelScope.launch {
            _activeConversationId.collectLatest { convId ->
                messageCollectionJob?.cancel()
                if (convId != null) {
                    messageCollectionJob = viewModelScope.launch {
                        dao.getMessagesForConversation(convId).collectLatest { msgs ->
                            if (!_isGenerating.value) {
                                _messages.value = msgs
                            }
                        }
                    }
                } else {
                    _messages.value = emptyList()
                }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun setImageMode(enabled: Boolean) {
        _isImageMode.value = enabled
    }

    fun setSelectedModel(model: String) {
        val sanitized = if (model.contains("2.5") || model.isBlank()) {
            GeminiRepository.DEFAULT_MODEL
        } else {
            model.trim().removePrefix("models/")
        }
        _selectedModel.value = sanitized
        sharedPrefs.edit().putString("selected_model", sanitized).apply()
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        sharedPrefs.edit().putString("custom_api_key", key).apply()
        voiceManager.setCustomApiKey(key)
    }

    // Voice Dictation (Feature 1: Inside Chat Input Field)
    fun startDictation() {
        voiceManager.stopSpeaking()
        val previousText = _inputText.value
        voiceManager.startDictation { recognizedSpeech, isFinal ->
            // Place recognized speech in input field. Do NOT automatically send the message.
            if (previousText.isBlank()) {
                _inputText.value = recognizedSpeech
            } else {
                _inputText.value = "$previousText $recognizedSpeech"
            }
        }
    }

    fun stopDictation() {
        voiceManager.stopListening()
    }

    // Live AI Voice Conversation (Feature 2: Dedicated Real-time Mode)
    fun openLiveVoiceConversation() {
        _isLiveVoiceDialogOpen.value = true
        _isContinuousVoiceMode.value = true
        _lastAiResponse.value = ""
        startVoiceListening(continuous = true)
    }

    fun closeLiveVoiceConversation() {
        _isLiveVoiceDialogOpen.value = false
        stopVoiceConversation()
    }

    // Voice conversation controls
    fun toggleTtsEnabled() {
        val newValue = !_isTtsEnabled.value
        _isTtsEnabled.value = newValue
        sharedPrefs.edit().putBoolean("is_tts_enabled", newValue).apply()
        if (!newValue) {
            voiceManager.stopSpeaking()
        }
    }

    fun setVoiceLanguage(language: VoiceLanguage) {
        voiceManager.setLanguage(language)
    }

    fun startVoiceListening(continuous: Boolean = false) {
        _isContinuousVoiceMode.value = continuous
        voiceManager.startLiveConversation { recognizedText ->
            sendMessage(customPrompt = recognizedText, fromVoice = true)
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun toggleContinuousVoiceMode() {
        if (_isContinuousVoiceMode.value) {
            stopVoiceConversation()
        } else {
            startVoiceListening(continuous = true)
        }
    }

    fun stopVoiceConversation() {
        _isContinuousVoiceMode.value = false
        voiceManager.setIdleState()
    }

    fun stopSpeaking() {
        unifiedVoiceService.stopSpeaking()
        voiceManager.stopSpeaking()
    }

    fun speakMessage(text: String) {
        unifiedVoiceService.speak(text)
    }

    fun signOut() {
        stopVoiceConversation()
        authRepository.signOut()
    }

    fun startNewChat() {
        stopVoiceConversation()
        generationJob?.cancel()
        _isGenerating.value = false
        _activeConversationId.value = null
        _messages.value = emptyList()
        _inputText.value = ""
        _selectedImageUri.value = null
        _isImageMode.value = false
    }

    fun selectConversation(conversationId: String) {
        stopVoiceConversation()
        generationJob?.cancel()
        _isGenerating.value = false
        _activeConversationId.value = conversationId
        _inputText.value = ""
        _selectedImageUri.value = null
        _isImageMode.value = false
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                startNewChat()
            }
            dao.deleteConversationById(conversationId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            startNewChat()
            dao.clearAllMessages()
            dao.clearAllConversations()
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        voiceManager.setIdleState()
    }

    fun generateAgain(message: ChatMessageEntity) {
        val prompt = message.imagePrompt ?: message.content
        if (prompt.isNotBlank()) {
            generateImageMessage(prompt = prompt, referenceImageUri = null)
        }
    }

    fun prepareImageEdit(message: ChatMessageEntity) {
        if (message.imageUri != null) {
            _selectedImageUri.value = Uri.parse(message.imageUri)
            _isImageMode.value = true
            _inputText.value = "Make the sky blue"
        }
    }

    fun createAnotherImage() {
        _isImageMode.value = true
        _selectedImageUri.value = null
        _inputText.value = ""
    }

    // AI Image Mode
    fun generateImageMessage(prompt: String, referenceImageUri: Uri? = null) {
        if (prompt.isBlank()) return

        generationJob?.cancel()
        generationJob = null
        voiceManager.stopSpeaking()

        _inputText.value = ""
        _selectedImageUri.value = null

        viewModelScope.launch {
            var convId = _activeConversationId.value
            val isNewChat = convId == null

            if (isNewChat) {
                convId = UUID.randomUUID().toString()
                val title = if (prompt.length > 35) "🎨 " + prompt.take(30) + "..." else "🎨 $prompt"
                val newConv = ConversationEntity(
                    id = convId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertConversation(newConv)
                _activeConversationId.value = convId
            } else {
                val existing = dao.getConversationById(convId!!)
                if (existing != null) {
                    dao.updateConversation(existing.copy(updatedAt = System.currentTimeMillis()))
                }
            }

            // Create User Message
            val userMsgId = UUID.randomUUID().toString()
            val userMessage = ChatMessageEntity(
                id = userMsgId,
                conversationId = convId!!,
                role = "user",
                content = prompt,
                imageUri = referenceImageUri?.toString(),
                isGeneratedImage = false,
                timestamp = System.currentTimeMillis()
            )

            // Create placeholder Assistant Message
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMessage = ChatMessageEntity(
                id = assistantMsgId,
                conversationId = convId,
                role = "model",
                content = "छवि बनाई जा रही है... (Generating image...)",
                isGeneratedImage = true,
                imagePrompt = prompt,
                timestamp = System.currentTimeMillis() + 1,
                isStreaming = true
            )

            // Update in-memory state immediately for instant rendering
            _messages.value = _messages.value + listOf(userMessage, initialAssistantMessage)

            launch(kotlinx.coroutines.Dispatchers.IO) {
                dao.insertMessage(userMessage)
                dao.insertMessage(initialAssistantMessage)
            }

            _isGenerating.value = true

            generationJob = viewModelScope.launch {
                try {
                    val result = repository.generateImage(
                        prompt = prompt,
                        referenceImageUri = referenceImageUri?.toString(),
                        customApiKey = null
                    )

                    if (result.isSuccess) {
                        val (generatedUri, textNotice) = result.getOrThrow()
                        val completedMessage = initialAssistantMessage.copy(
                            content = textNotice,
                            imageUri = generatedUri,
                            isGeneratedImage = true,
                            imagePrompt = prompt,
                            isStreaming = false,
                            isError = false
                        )
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == assistantMsgId) completedMessage else msg
                        }
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            dao.updateMessage(completedMessage)
                        }
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message
                            ?: "Image service is temporarily unavailable."
                        val errorMessage = initialAssistantMessage.copy(
                            content = errorMsg,
                            isGeneratedImage = false,
                            isStreaming = false,
                            isError = true
                        )
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == assistantMsgId) errorMessage else msg
                        }
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            dao.updateMessage(errorMessage)
                        }
                    }
                } catch (e: Exception) {
                    val fallbackMessage = initialAssistantMessage.copy(
                        content = "Image service is temporarily unavailable.",
                        isGeneratedImage = false,
                        isStreaming = false,
                        isError = true
                    )
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) fallbackMessage else msg
                    }
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.updateMessage(fallbackMessage)
                    }
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun sendMessage(
        customPrompt: String? = null,
        customImageUri: Uri? = null,
        fromVoice: Boolean = false
    ) {
        val messageText = (customPrompt ?: _inputText.value).trim()
        val imageUri = customImageUri ?: _selectedImageUri.value

        if (messageText.isBlank() && imageUri == null) return

        // 10. Cancel previous unfinished AI requests when user sends a new message
        generationJob?.cancel()
        generationJob = null

        // Stop TTS speaking immediately
        voiceManager.stopSpeaking()

        // Clear inputs immediately
        _inputText.value = ""
        _selectedImageUri.value = null

        if (_isImageMode.value || GeminiRepository.isImagePrompt(messageText)) {
            generateImageMessage(messageText, imageUri)
            return
        }

        viewModelScope.launch {
            // Determine active conversation or create new one
            var convId = _activeConversationId.value
            val isNewChat = convId == null

            if (isNewChat) {
                convId = UUID.randomUUID().toString()
                val title = if (messageText.isNotBlank()) {
                    if (messageText.length > 35) messageText.take(32) + "..." else messageText
                } else {
                    "छवि विश्लेषण (Image)"
                }
                val newConv = ConversationEntity(
                    id = convId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertConversation(newConv)
                _activeConversationId.value = convId
            } else {
                val existing = dao.getConversationById(convId!!)
                if (existing != null) {
                    dao.updateConversation(existing.copy(updatedAt = System.currentTimeMillis()))
                }
            }

            // Create User Message
            val userMsgId = UUID.randomUUID().toString()
            val userMessage = ChatMessageEntity(
                id = userMsgId,
                conversationId = convId!!,
                role = "user",
                content = messageText,
                imageUri = imageUri?.toString(),
                timestamp = System.currentTimeMillis()
            )

            // Create placeholder Assistant Message for streaming
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMessage = ChatMessageEntity(
                id = assistantMsgId,
                conversationId = convId,
                role = "model",
                content = "",
                timestamp = System.currentTimeMillis() + 1,
                isStreaming = true
            )

            // Extract context from current in-memory message history before appending
            val contextMessages = _messages.value.filter { it.content.isNotBlank() && !it.isError && !it.isStreaming }

            // Immediately update in-memory state for instant UI rendering without waiting for SQLite disk write
            _messages.value = _messages.value.filter { it.id != assistantMsgId && it.id != userMsgId } + listOf(userMessage, initialAssistantMessage)

            // Persist to Room in background
            launch(kotlinx.coroutines.Dispatchers.IO) {
                dao.insertMessage(userMessage)
                dao.insertMessage(initialAssistantMessage)
            }

            _isGenerating.value = true
            if (fromVoice || _isContinuousVoiceMode.value) {
                voiceManager.setThinkingState()
            }

            generationJob = viewModelScope.launch {
                try {
                    var finalContent = ""
                    repository.generateStreamResponse(
                        messages = contextMessages,
                        latestUserMessage = messageText,
                        attachedImageUri = imageUri?.toString(),
                        modelName = _selectedModel.value,
                        customApiKey = _customApiKey.value
                    ).collect { chunk ->
                        finalContent = chunk
                        _lastAiResponse.value = chunk

                        // Real-time in-memory update for 60fps streaming without SQLite write bottleneck
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(content = chunk, isStreaming = true)
                            } else {
                                msg
                            }
                        }
                    }

                    val cleanResponse = finalContent.ifEmpty { "जवाब प्राप्त करने में असमर्थ। कृपया पुनः प्रयास करें।" }
                    _lastAiResponse.value = cleanResponse

                    val completedMessage = initialAssistantMessage.copy(
                        content = cleanResponse,
                        isStreaming = false
                    )

                    // Finalize in memory
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) completedMessage else msg
                    }

                    // Save finalized response to database
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.updateMessage(completedMessage)
                    }

                    // Speak response immediately if TTS is enabled or if triggered from voice
                    if ((fromVoice || _isContinuousVoiceMode.value || _isTtsEnabled.value) && _isTtsEnabled.value) {
                        unifiedVoiceService.speak(cleanResponse, onDone = {
                            if (_isContinuousVoiceMode.value) {
                                viewModelScope.launch {
                                    if (_isContinuousVoiceMode.value && !_isGenerating.value) {
                                        startVoiceListening(continuous = true)
                                    }
                                }
                            }
                        })
                    } else if (_isContinuousVoiceMode.value) {
                        viewModelScope.launch {
                            if (_isContinuousVoiceMode.value && !_isGenerating.value) {
                                startVoiceListening(continuous = true)
                            }
                        }
                    } else {
                        voiceManager.setIdleState()
                    }

                } catch (e: Exception) {
                    val errorText = "त्रुटि: ${e.localizedMessage ?: "Unknown error"}"
                    val errorMessage = initialAssistantMessage.copy(
                        content = errorText,
                        isStreaming = false,
                        isError = true
                    )
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) errorMessage else msg
                    }
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.updateMessage(errorMessage)
                    }
                    if (fromVoice || _isContinuousVoiceMode.value) {
                        if (_isTtsEnabled.value) {
                            unifiedVoiceService.speak("माफ़ कीजिए, कोई तकनीकी समस्या आई।")
                        } else {
                            voiceManager.setIdleState()
                        }
                    }
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun regenerateResponse(targetMessage: ChatMessageEntity) {
        generationJob?.cancel()
        generationJob = null
        voiceManager.stopSpeaking()

        val convId = targetMessage.conversationId

        viewModelScope.launch {
            val allMessages = _messages.value
            val targetIndex = allMessages.indexOfFirst { it.id == targetMessage.id }
            if (targetIndex < 0) return@launch

            // Find the preceding user message
            val previousUserMessage = allMessages.take(targetIndex).lastOrNull { it.role == "user" }
            val userPromptText = previousUserMessage?.content ?: return@launch
            val userImageUri = previousUserMessage.imageUri

            // Context is messages before that user message
            val userIndex = allMessages.indexOfFirst { it.id == previousUserMessage.id }
            val contextMessages = if (userIndex >= 0) allMessages.take(userIndex) else emptyList()

            val streamingMessage = targetMessage.copy(
                content = "",
                isStreaming = true,
                isError = false,
                isGeneratedImage = false
            )

            // Update in-memory state immediately
            _messages.value = _messages.value.map { if (it.id == targetMessage.id) streamingMessage else it }
            _isGenerating.value = true

            generationJob = viewModelScope.launch {
                try {
                    var finalContent = ""
                    repository.generateStreamResponse(
                        messages = contextMessages,
                        latestUserMessage = userPromptText,
                        attachedImageUri = userImageUri,
                        modelName = _selectedModel.value,
                        customApiKey = _customApiKey.value
                    ).collect { chunk ->
                        finalContent = chunk
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == targetMessage.id) {
                                msg.copy(content = chunk, isStreaming = true, isError = false)
                            } else {
                                msg
                            }
                        }
                    }

                    val cleanResponse = finalContent.ifEmpty { "जवाब प्राप्त करने में असमर्थ। कृपया पुनः प्रयास करें।" }
                    val completed = targetMessage.copy(
                        content = cleanResponse,
                        isStreaming = false,
                        isError = false
                    )

                    _messages.value = _messages.value.map { if (it.id == targetMessage.id) completed else it }

                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.updateMessage(completed)
                    }

                    if (_isTtsEnabled.value) {
                        unifiedVoiceService.speak(cleanResponse)
                    }
                } catch (e: Exception) {
                    val errorCompleted = targetMessage.copy(
                        content = "त्रुटि: ${e.localizedMessage ?: "Unknown error"}",
                        isStreaming = false,
                        isError = true
                    )
                    _messages.value = _messages.value.map { if (it.id == targetMessage.id) errorCompleted else it }
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.updateMessage(errorCompleted)
                    }
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}

