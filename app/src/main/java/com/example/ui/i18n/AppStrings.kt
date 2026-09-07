package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

interface AppStrings {
    // App Branding & General
    val appName: String
    val tagline: String
    val taglineHindi: String
    val appDescription: String
    val appVersion: String
    val disclaimer: String
    val confidentialityNotice: String

    // Top Bar & Controls
    val appTitle: String
    val newChat: String
    val openMenu: String
    val voiceResponsesOnToast: String
    val voiceResponsesMutedToast: String
    val muteVoice: String
    val enableVoice: String

    // Navigation & Sidebar
    val newChatButton: String
    val recentChats: String
    val noRecentChats: String
    val settings: String
    val logout: String
    val loggedIn: String
    val user: String
    val deleteChatTooltip: String

    // Chat Input Field
    val inputPlaceholder: String
    val imageInputPlaceholder: String
    val attachFileTooltip: String
    val voiceInputTooltip: String
    val activeVoiceInputTooltip: String
    val dictationMicTooltip: String
    val dictatingActiveTooltip: String
    val stopGenerationTooltip: String
    val sendMessageTooltip: String
    val aiImageMode: String
    val exitImageMode: String
    val removeAttachedImage: String

    // Live Voice Conversation
    val liveVoiceButtonTooltip: String
    val liveVoiceTitle: String
    val liveVoiceSubtitle: String
    val endVoiceConversation: String

    // Home / Welcome View
    val welcomeTitle: String
    val welcomeSubtitle: String
    val suggestion1Title: String
    val suggestion1Prompt: String
    val suggestion2Title: String
    val suggestion2Prompt: String
    val suggestion3Title: String
    val suggestion3Prompt: String
    val suggestion4Title: String
    val suggestion4Prompt: String

    // Voice Assistant Bar & Dialog
    val voiceStateListening: String
    val voiceStateThinking: String
    val voiceStateSpeaking: String
    val voiceStateNotice: String
    val voiceStateIdle: String
    val continuousOn: String
    val continuousOff: String
    val stopListening: String
    val stopSpeaking: String
    val closeVoiceMode: String
    val micPermissionRequired: String

    // Message Actions & Cards
    val copy: String
    val share: String
    val readAloud: String
    val stopReading: String
    val regenerate: String
    val generateAgain: String
    val editImage: String
    val createAnother: String
    val copiedToast: String
    val shareVia: String
    val generatingImage: String
    val imageServiceUnavailable: String
    val technicalError: String
    val unableToGetResponse: String
    val imageAnalysisTitle: String

    // Dialogs & Confirmations
    val deleteChatTitle: String
    fun deleteChatConfirmMessage(title: String): String
    val clearHistoryTitle: String
    val clearHistoryMessage: String
    val clearAll: String
    val delete: String
    val cancel: String
    val logoutTitle: String
    val logoutMessage: String

    // Settings Dialog
    val settingsTitle: String
    val languageSection: String
    val languageDescription: String
    val englishOption: String
    val hindiOption: String
    val aiModel: String
    val customApiKeyTitle: String
    val customApiKeySubtitle: String
    val customApiKeyPlaceholder: String
    val dataManagement: String
    val clearAllHistory: String
    val save: String
    val close: String

    // Auth & Login
    val continueWithGoogle: String
    val continueWithEmail: String
    val createAccount: String
    val signInWithEmail: String
    val fullName: String
    val emailAddress: String
    val password: String
    val signUp: String
    val signIn: String
    val alreadyHaveAccount: String
    val dontHaveAccount: String
    val dismissError: String
    val togglePassword: String
}

object EnglishStrings : AppStrings {
    override val appName = "SAMADHAN AI"
    override val tagline = "Not just answers — solutions."
    override val taglineHindi = "सिर्फ जवाब नहीं — समाधान।"
    override val appDescription = "Your AI Problem Solver"
    override val appVersion = "Version 1.0 • Powered by Google Gemini"
    override val disclaimer = "AI CAN MAKE MISTAKES. VERIFY IMPORTANT INFO."
    override val confidentialityNotice = "Secure AI • Private & Confidential"

    override val appTitle = "SAMADHAN AI"
    override val newChat = "New Chat"
    override val openMenu = "Open menu"
    override val voiceResponsesOnToast = "AI Voice responses ON"
    override val voiceResponsesMutedToast = "AI Voice responses Muted"
    override val muteVoice = "Mute AI Voice"
    override val enableVoice = "Enable AI Voice"

    override val newChatButton = "New Chat"
    override val recentChats = "Recent Chats"
    override val noRecentChats = "No past chats yet.\nAsk anything to get started!"
    override val settings = "Settings"
    override val logout = "Log out"
    override val loggedIn = "Logged in"
    override val user = "User"
    override val deleteChatTooltip = "Delete chat"

    override val inputPlaceholder = "Ask anything..."
    override val imageInputPlaceholder = "Describe image to generate / edit..."
    override val attachFileTooltip = "Attach file or image"
    override val voiceInputTooltip = "Voice input"
    override val activeVoiceInputTooltip = "Active voice input"
    override val dictationMicTooltip = "Voice dictation (Speak to write)"
    override val dictatingActiveTooltip = "Dictating... Tap to finish"
    override val stopGenerationTooltip = "Stop"
    override val sendMessageTooltip = "Send"
    override val aiImageMode = "AI Image Mode"
    override val exitImageMode = "Exit Image Mode"
    override val removeAttachedImage = "Remove attached image"

    override val liveVoiceButtonTooltip = "Live AI Voice Conversation"
    override val liveVoiceTitle = "Live AI Voice Conversation"
    override val liveVoiceSubtitle = "Speak naturally with Samadhan AI"
    override val endVoiceConversation = "End Conversation"

    override val welcomeTitle = "How can I help you today?"
    override val welcomeSubtitle = "Ask any question or share a problem to solve."
    override val suggestion1Title = "Problem Solving"
    override val suggestion1Prompt = "Help me find a step-by-step solution to a complex problem"
    override val suggestion2Title = "Creative Writing"
    override val suggestion2Prompt = "Draft an inspiring story, article, or thoughtful essay"
    override val suggestion3Title = "Draft & Email"
    override val suggestion3Prompt = "Draft a formal, polite, and effective email"
    override val suggestion4Title = "Coding & Tech"
    override val suggestion4Prompt = "Provide clean code examples and explanation in Kotlin"

    override val voiceStateListening = "Listening..."
    override val voiceStateThinking = "Thinking..."
    override val voiceStateSpeaking = "Speaking..."
    override val voiceStateNotice = "Voice Notice"
    override val voiceStateIdle = "Voice Mode"
    override val continuousOn = "Continuous: ON"
    override val continuousOff = "Continuous: OFF"
    override val stopListening = "Stop"
    override val stopSpeaking = "Stop"
    override val closeVoiceMode = "Close voice mode"
    override val micPermissionRequired = "Microphone permission is required for voice assistant"

    override val copy = "Copy"
    override val share = "Share"
    override val readAloud = "Read aloud"
    override val stopReading = "Stop"
    override val regenerate = "Regenerate response"
    override val generateAgain = "Generate again"
    override val editImage = "Edit image"
    override val createAnother = "Create another"
    override val copiedToast = "Copied to clipboard"
    override val shareVia = "Share response via"
    override val generatingImage = "Generating image..."
    override val imageServiceUnavailable = "Image service is temporarily unavailable."
    override val technicalError = "Sorry, a technical error occurred."
    override val unableToGetResponse = "Unable to get response. Please try again."
    override val imageAnalysisTitle = "Image Analysis"

    override val deleteChatTitle = "Delete chat?"
    override fun deleteChatConfirmMessage(title: String) = "Are you sure you want to delete \"$title\"?"
    override val clearHistoryTitle = "Clear all history?"
    override val clearHistoryMessage = "Are you sure you want to delete all past chat sessions? This action cannot be undone."
    override val clearAll = "Clear All"
    override val delete = "Delete"
    override val cancel = "Cancel"
    override val logoutTitle = "Log out?"
    override val logoutMessage = "Are you sure you want to log out of Samadhan AI?"

    override val settingsTitle = "Settings"
    override val languageSection = "App Language (भाषा)"
    override val languageDescription = "Choose the display language for the application interface."
    override val englishOption = "English"
    override val hindiOption = "हिंदी (Hindi)"
    override val aiModel = "AI Model"
    override val customApiKeyTitle = "Gemini API Key (Optional Override)"
    override val customApiKeySubtitle = "Defaults to the injected environment key from AI Studio secrets."
    override val customApiKeyPlaceholder = "Leave blank to use default key"
    override val dataManagement = "Data Management"
    override val clearAllHistory = "Clear all chat history"
    override val save = "Save"
    override val close = "Close"

    override val continueWithGoogle = "Continue with Google"
    override val continueWithEmail = "Continue with Email"
    override val createAccount = "Create Account"
    override val signInWithEmail = "Sign In with Email"
    override val fullName = "Full Name"
    override val emailAddress = "Email address"
    override val password = "Password"
    override val signUp = "Sign Up"
    override val signIn = "Sign In"
    override val alreadyHaveAccount = "Already have an account?"
    override val dontHaveAccount = "Don't have an account?"
    override val dismissError = "Dismiss error"
    override val togglePassword = "Toggle password visibility"
}

object HindiStrings : AppStrings {
    override val appName = "समाधान AI"
    override val tagline = "सिर्फ जवाब नहीं — समाधान।"
    override val taglineHindi = "सिर्फ जवाब नहीं — समाधान।"
    override val appDescription = "आपका AI समाधानकर्ता"
    override val appVersion = "संस्करण 1.0 • Google Gemini द्वारा संचालित"
    override val disclaimer = "AI से गलतियाँ हो सकती हैं। महत्वपूर्ण जानकारी की पुष्टि करें।"
    override val confidentialityNotice = "सुरक्षित AI • निजी एवं गोपनीय"

    override val appTitle = "SAMADHAN AI"
    override val newChat = "नई चैट"
    override val openMenu = "मेनू खोलें"
    override val voiceResponsesOnToast = "AI वॉइस चालू"
    override val voiceResponsesMutedToast = "AI वॉइस बंद"
    override val muteVoice = "AI वॉइस बंद करें"
    override val enableVoice = "AI वॉइस चालू करें"

    override val newChatButton = "नई चैट"
    override val recentChats = "हाल की बातचीत"
    override val noRecentChats = "कोई पिछला चैट नहीं है।\nनया सवाल पूछकर शुरू करें!"
    override val settings = "सेटिंग्स"
    override val logout = "लॉग आउट"
    override val loggedIn = "लॉग इन हैं"
    override val user = "उपयोगकर्ता"
    override val deleteChatTooltip = "बातचीत हटाएं"

    override val inputPlaceholder = "कुछ भी पूछें..."
    override val imageInputPlaceholder = "छवि बनाने/संपादित करने के लिए विवरण दें..."
    override val attachFileTooltip = "फ़ाइल या छवि जोड़ें"
    override val voiceInputTooltip = "आवाज़ इनपुट"
    override val activeVoiceInputTooltip = "सक्रिय आवाज़ इनपुट"
    override val dictationMicTooltip = "वॉइस डिक्टेशन (बोलकर लिखें)"
    override val dictatingActiveTooltip = "डिक्टेशन जारी है... समाप्त करने के लिए टैप करें"
    override val stopGenerationTooltip = "रोकें"
    override val sendMessageTooltip = "भेजें"
    override val aiImageMode = "AI छवि मोड"
    override val exitImageMode = "छवि मोड से बाहर निकलें"
    override val removeAttachedImage = "संलग्न छवि हटाएं"

    override val liveVoiceButtonTooltip = "लाइव AI वॉइस बातचीत"
    override val liveVoiceTitle = "लाइव AI वॉइस बातचीत"
    override val liveVoiceSubtitle = "समाधान AI के साथ स्वाभाविक रूप से बात करें"
    override val endVoiceConversation = "बातचीत समाप्त करें"

    override val welcomeTitle = "मैं आपकी कैसे मदद कर सकता हूँ?"
    override val welcomeSubtitle = "कोई भी सवाल पूछें या अपनी समस्या बताएं।"
    override val suggestion1Title = "समस्या समाधान"
    override val suggestion1Prompt = "किसी जटिल समस्या का चरणबद्ध समाधान खोजें"
    override val suggestion2Title = "रचनात्मक लेखन"
    override val suggestion2Prompt = "एक प्रेरक कहानी या विचारशील निबंध का प्रारूप तैयार करें"
    override val suggestion3Title = "ईमेल का मसौदा"
    override val suggestion3Prompt = "एक औपचारिक और प्रभावी ईमेल का मसौदा तैयार करें"
    override val suggestion4Title = "कोडिंग सहायता"
    override val suggestion4Prompt = "Kotlin और Jetpack Compose में कोड उदाहरण दें"

    override val voiceStateListening = "सुन रहा है..."
    override val voiceStateThinking = "सोच रहा है..."
    override val voiceStateSpeaking = "बोल रहा है..."
    override val voiceStateNotice = "वॉइस सूचना"
    override val voiceStateIdle = "वॉइस मोड"
    override val continuousOn = "सतत: चालू"
    override val continuousOff = "सतत: बंद"
    override val stopListening = "रोकें"
    override val stopSpeaking = "रोकें"
    override val closeVoiceMode = "वॉइस मोड बंद करें"
    override val micPermissionRequired = "वॉइस सहायक के लिए माइक्रोफ़ोन अनुमति आवश्यक है"

    override val copy = "कॉपी करें"
    override val share = "शेयर करें"
    override val readAloud = "सुनें"
    override val stopReading = "रोकें"
    override val regenerate = "पुनः उत्पन्न करें"
    override val generateAgain = "फिर से बनाएं"
    override val editImage = "छवि संपादित करें"
    override val createAnother = "एक और बनाएं"
    override val copiedToast = "क्लिपबोर्ड पर कॉपी किया गया"
    override val shareVia = "के माध्यम से साझा करें"
    override val generatingImage = "छवि बनाई जा रही है..."
    override val imageServiceUnavailable = "छवि सेवा अस्थायी रूप से अनुपलब्ध है।"
    override val technicalError = "माफ़ कीजिए, कोई तकनीकी समस्या आई।"
    override val unableToGetResponse = "जवाब प्राप्त करने में असमर्थ। कृपया पुनः प्रयास करें।"
    override val imageAnalysisTitle = "छवि विश्लेषण"

    override val deleteChatTitle = "बातचीत हटाएं?"
    override fun deleteChatConfirmMessage(title: String) = "क्या आप वाकई \"$title\" को हटाना चाहते हैं?"
    override val clearHistoryTitle = "सभी इतिहास हटाएं?"
    override val clearHistoryMessage = "क्या आप वाकई अपने सभी पिछले चैट सत्रों को हटाना चाहते हैं? यह क्रिया वापस नहीं ली जा सकती।"
    override val clearAll = "सभी हटाएं"
    override val delete = "हटाएं"
    override val cancel = "रद्द करें"
    override val logoutTitle = "लॉग आउट करें?"
    override val logoutMessage = "क्या आप Samadhan AI से लॉग आउट करना चाहते हैं?"

    override val settingsTitle = "सेटिंग्स"
    override val languageSection = "ऐप की भाषा (App Language)"
    override val languageDescription = "एप्लिकेशन इंटरफ़ेस के लिए प्रदर्शन भाषा चुनें।"
    override val englishOption = "English"
    override val hindiOption = "हिंदी (Hindi)"
    override val aiModel = "AI मॉडल"
    override val customApiKeyTitle = "Gemini API कुंजी (वैकल्पिक)"
    override val customApiKeySubtitle = "AI Studio सीक्रेट्स से डिफ़ॉल्ट कुंजी का उपयोग करता है।"
    override val customApiKeyPlaceholder = "डिफ़ॉल्ट कुंजी का उपयोग करने के लिए खाली छोड़ें"
    override val dataManagement = "डेटा प्रबंधन"
    override val clearAllHistory = "सभी चैट इतिहास मिटाएं"
    override val save = "सहेजें"
    override val close = "बंद करें"

    override val continueWithGoogle = "Google से आगे बढ़ें"
    override val continueWithEmail = "ईमेल से आगे बढ़ें"
    override val createAccount = "खाता बनाएं"
    override val signInWithEmail = "ईमेल से साइन इन करें"
    override val fullName = "पूरा नाम"
    override val emailAddress = "ईमेल पता"
    override val password = "पासवर्ड"
    override val signUp = "साइन अप करें"
    override val signIn = "साइन इन करें"
    override val alreadyHaveAccount = "पहले से खाता है?"
    override val dontHaveAccount = "खाता नहीं है?"
    override val dismissError = "त्रुटि हटाएं"
    override val togglePassword = "पासवर्ड दृश्यता बदलें"
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

@Composable
@ReadOnlyComposable
fun appStrings(): AppStrings = LocalAppStrings.current
