package com.example

import com.example.data.remote.N8nAgentService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class N8nAgentServiceTest {

    private val service = N8nAgentService()

    @Test
    fun testParseOutputField() {
        val json = """{"output": "Hello from SAMADHAN AI!"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Hello from SAMADHAN AI!", result)
    }

    @Test
    fun testParseTextField() {
        val json = """{"text": "Sample text response"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Sample text response", result)
    }

    @Test
    fun testParseMessageField() {
        val json = """{"message": "Answer in message key"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Answer in message key", result)
    }

    @Test
    fun testParseResponseField() {
        val json = """{"response": "Answer in response key"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Answer in response key", result)
    }

    @Test
    fun testParseAnswerField() {
        val json = """{"answer": "Answer in answer key"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Answer in answer key", result)
    }

    @Test
    fun testParseContentField() {
        val json = """{"content": "Answer in content key"}"""
        val result = service.parseN8nResponse(json)
        assertEquals("Answer in content key", result)
    }

    @Test
    fun testParseArrayOutput() {
        val json = """[{"output": "Answer from n8n node array"}]"""
        val result = service.parseN8nResponse(json)
        assertEquals("Answer from n8n node array", result)
    }

    @Test
    fun testParseNestedJsonOutput() {
        val json = """[{"json": {"response": "Nested JSON response"}}]"""
        val result = service.parseN8nResponse(json)
        assertEquals("Nested JSON response", result)
    }

    @Test
    fun testParsePlainText() {
        val text = "Direct plain text response"
        val result = service.parseN8nResponse(text)
        assertEquals("Direct plain text response", result)
    }

    @Test
    fun testLiveN8nWebhookCall() = runBlocking {
        // Live verification of n8n webhook integration
        val result = service.sendChatMessage("नमस्ते, आप कौन हैं?")
        assertTrue("Live webhook call should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val answer = result.getOrNull()
        assertNotNull("Response should not be null", answer)
        assertTrue("Response should contain text: $answer", answer!!.isNotBlank())
        println("Live n8n Response: $answer")
    }
}
