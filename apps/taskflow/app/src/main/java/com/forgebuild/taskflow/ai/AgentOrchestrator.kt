package com.forgebuild.taskflow.ai

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.settings.GeminiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, MODEL, ACTION }
}

/** Runs the multi-turn Gemini function-calling loop and records the chat transcript. */
class AgentOrchestrator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskFlow:AIRequest")

    companion object {
        @Volatile
        private var instance: AgentOrchestrator? = null

        fun get(context: Context): AgentOrchestrator =
            instance ?: synchronized(this) {
                instance ?: AgentOrchestrator(context.applicationContext).also { instance = it }
            }
    }

    fun submit(userText: String): Job = appScope.launch {
        send(userText)
    }

    fun submitAudio(mimeType: String, base64Audio: String): Job = appScope.launch {
        sendAudio(mimeType, base64Audio)
    }
    private val repo = com.forgebuild.taskflow.data.TaskRepository.get(context)
    private val client = GeminiClient(GeminiKeyStore.get(context))
    private val tools = TaskAgentTools(repo)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private suspend fun buildSystemInstruction(): String {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.getDefault())
        val tz = TimeZone.getDefault()
        val tzName = tz.getDisplayName(tz.inDaylightTime(Date(now)), TimeZone.LONG)
        val tzId = tz.id
        val remainingToday = repo.getRemainingMinutesToday()
        val allocatedToday = repo.getAllocatedMinutesToday()

        return buildString {
            append("You are TaskFlow's built-in intelligent task assistant. ")
            append("CURRENT DEVICE CLOCK & TIME ZONE: ")
            append(fmt.format(Date(now)))
            append(", Time zone: ").append(tzName).append(" (").append(tzId).append("). ")
            append("Today's schedule: ").append(allocatedToday).append(" minutes allocated, ")
            append(remainingToday).append(" minutes remaining unallocated before midnight. ")
            append("You manage the user's to-do list by calling the provided tools. ")
            append("FIXED TIMES: A task must only get a fixed_time when the user explicitly specifies a time or clearly implies one (e.g. \"remind me at 3pm\", \"tomorrow at 9\"). NEVER auto-assign or invent a fixed time — when no time is requested, create the task with NO fixed_time, exactly as if the user added it manually without one. ")
            append("TASK TIMERS: Every task has a countdown timer (based on its duration) that the user can play/start, pause, resume, and extend at any moment — including mid-countdown and after it has finished. Use start_timer, pause_timer and extend_timer (amount + unit minutes/hours) whenever the user asks. When a timer finishes, the app plays a ding and offers extend or complete. ")
            append("MANDATORY DURATION: Every task MUST have a duration in minutes (e.g. 15, 30, 45, 60). If the user does not specify a duration, you MUST estimate and assign a sensible duration yourself. Never leave duration blank. ")
            append("REMAINING TIME: If a task's duration exceeds today's remaining unallocated time (${remainingToday}m), suggest scheduling it for tomorrow or a future date, or adjust duration. ")
            append("OPEN-ENDED INSTRUCTIONS: Understand vague requests like 'Set this to only happen on Tuesdays' (weekly, tue), or 'scatter three tasks across the week' (create 3 tasks on different days with reasonable durations). ")
            append("RECURRING TASKS may have an optional end date ('repeat every day until 2026-12-31'); set recurrence_end when the user gives an end condition, otherwise leave it unset (indefinite). ")
            append("VOICE INPUT: The user may send a raw audio voice note. Listen to the audio directly, transcribe it yourself, and act on the spoken request exactly as if it were typed. ")
            append("Use list_tasks or get_day_status whenever needed. ")
            append("After completing actions, reply with one concise natural-language sentence summarizing exactly what you did.")
        }
    }

    private fun historyContents(): MutableList<JsonObject> {
        val contents = mutableListOf<JsonObject>()
        _messages.value.takeLast(12).filter { it.role != ChatMessage.Role.ACTION }.forEach { m ->
            contents.add(GeminiClient.content(if (m.role == ChatMessage.Role.USER) "user" else "model",
                listOf(GeminiClient.textPart(m.text))))
        }
        return contents
    }

    /** Send one typed user instruction; runs the function-calling loop to completion. */
    suspend fun send(userText: String) {
        if (_busy.value) return
        _busy.value = true
        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.USER, userText)
        runLoop(historyContents())
    }

    /**
     * Send a RAW recorded voice note straight to Gemini (no on-device speech-to-text):
     * Gemini natively transcribes and understands the audio clip itself.
     */
    suspend fun sendAudio(mimeType: String, base64Audio: String) {
        if (_busy.value) return
        _busy.value = true
        val contents = historyContents()
        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.USER, "Voice note (audio)")
        contents.add(GeminiClient.content("user", listOf(
            GeminiClient.inlineDataPart(mimeType, base64Audio),
            GeminiClient.textPart("This is a voice note the user just recorded. Listen to the audio, transcribe it yourself, then act on the spoken request (create/update/complete/list tasks as asked) exactly as if it were typed text.")
        )))
        runLoop(contents)
    }

    private suspend fun runLoop(contents: MutableList<JsonObject>) {
        val actions = mutableListOf<String>()
        runCatching { wakeLock?.acquire(180_000L) }
        try {
            val systemInstruction = buildSystemInstruction()
            var rounds = 0
            while (rounds < 10) {
                rounds++
                val resp = client.generate(JsonArray(contents), tools.apiTools(), systemInstruction)
                val candidate = resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: break
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: break

                // Gemini REST v1beta responds with camelCase "functionCall" parts.
                val fnCalls = parts.mapNotNull { p ->
                    p.jsonObject["functionCall"]?.jsonObject?.let { fc ->
                        fc["name"]?.jsonPrimitive?.content to fc["args"]?.jsonObject
                    }
                }

                if (fnCalls.isEmpty()) {
                    // Final text response from model
                    val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("\n").trim()
                    if (text.isNotEmpty()) {
                        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL, text)
                        if (actions.isNotEmpty()) {
                            NotificationHub.agentConfirmation(appContext, text)
                        }
                    }
                    break
                }

                // Add model response containing function calls to contents history
                contents.add(candidate["content"]!!.jsonObject)

                // Execute each function call and add function responses
                val responseParts = mutableListOf<JsonObject>()
                for ((name, args) in fnCalls) {
                    if (name == null || args == null) continue
                    val result = tools.execute(name, args)
                    actions.add(result)
                    _messages.value = _messages.value + ChatMessage(ChatMessage.Role.ACTION, result)
                    responseParts.add(GeminiClient.functionResponsePart(name, result))
                }
                contents.add(GeminiClient.content("user", responseParts))
            }
        } catch (e: GeminiClient.NoKeysException) {
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "No Gemini API key configured. Open Settings to add your key.")
        } catch (e: GeminiClient.HighUsageExhaustedException) {
            // Distinct message for high load without false API key error notification
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Gemini servers are experiencing temporary high usage right now. The request was retried with backoff. Please try again in a few moments.")
        } catch (e: GeminiClient.InvalidApiKeyException) {
            NotificationHub.apiKeysFailed(appContext)
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Configured Gemini API key is invalid or rejected. Check Settings.")
        } catch (e: GeminiClient.AllKeysFailedException) {
            NotificationHub.apiKeysFailed(appContext)
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Configured Gemini API keys failed. Check Settings.")
        } catch (e: Exception) {
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Error: ${e.message ?: "Could not complete request."}")
        } finally {
            _busy.value = false
            runCatching {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }
}
