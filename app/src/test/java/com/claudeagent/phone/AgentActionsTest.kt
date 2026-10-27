package com.claudeagent.phone

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActionsTest {

    private fun input(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun parse(name: String, json: String = "{}") = AgentActions.parse(name, input(json))

    private fun assertInvalid(action: AgentAction, messagePart: String) {
        assertTrue("expected Invalid, got $action", action is AgentAction.Invalid)
        val message = (action as AgentAction.Invalid).message
        assertTrue("'$message' should mention '$messagePart'", message.contains(messagePart))
    }

    @Test
    fun tapMapsCoordinates() {
        assertEquals(AgentAction.Tap(540f, 1200f), parse("tap", """{"x":540,"y":1200}"""))
        assertEquals(AgentAction.Tap(540.5f, 12f), parse("tap", """{"x":540.5,"y":"12"}"""))
    }

    @Test
    fun tapWithMissingOrNonNumericCoordinateIsRejectedInsteadOfTappingTheCorner() {
        // Missing values used to default to 0, which tapped (0, 0).
        assertInvalid(parse("tap", """{"x":540}"""), "x, y")
        assertInvalid(parse("tap", """{"x":"left","y":10}"""), "x, y")
        assertInvalid(parse("tap", """{"x":{"v":1},"y":10}"""), "x, y")
        assertInvalid(parse("tap", """{"x":"NaN","y":10}"""), "x, y")
    }

    @Test
    fun longPressAndSwipeUseDefaultDurations() {
        assertEquals(AgentAction.LongPress(1f, 2f, 800L), parse("long_press", """{"x":1,"y":2}"""))
        assertEquals(AgentAction.LongPress(1f, 2f, 1500L), parse("long_press", """{"x":1,"y":2,"duration_ms":1500.0}"""))
        assertEquals(
            AgentAction.Swipe(1f, 2f, 3f, 4f, 300L),
            parse("swipe", """{"x1":1,"y1":2,"x2":3,"y2":4}"""),
        )
        assertEquals(
            AgentAction.Swipe(1f, 2f, 3f, 4f, 50L),
            parse("swipe", """{"x1":1,"y1":2,"x2":3,"y2":4,"duration_ms":50}"""),
        )
        assertInvalid(parse("swipe", """{"x1":1,"y1":2,"x2":3}"""), "y2")
    }

    @Test
    fun typeTextNeedsTheTextField() {
        assertEquals(AgentAction.TypeText("hello"), parse("type_text", """{"text":"hello"}"""))
        assertEquals(AgentAction.TypeText(""), parse("type_text", """{"text":""}"""))
        assertInvalid(parse("type_text"), "text")
        assertEquals(AgentAction.ClearText, parse("clear_text"))
    }

    @Test
    fun keyAcceptsOnlyTheDeclaredKeys() {
        for (key in listOf("back", "home", "recents", "ime_enter")) {
            assertEquals(AgentAction.Key(key), parse("key", """{"action":"$key"}"""))
        }
        assertInvalid(parse("key", """{"action":"volume_up"}"""), "back, home, recents, ime_enter")
        assertInvalid(parse("key"), "Unknown key")
    }

    @Test
    fun waitIsClampedToThreeSeconds() {
        assertEquals(AgentAction.Wait(1000L), parse("wait", """{"ms":1000}"""))
        assertEquals(AgentAction.Wait(3000L), parse("wait", """{"ms":60000}"""))
        assertEquals(AgentAction.Wait(0L), parse("wait", """{"ms":-5}"""))
        assertEquals(AgentAction.Wait(500L), parse("wait"))
    }

    @Test
    fun ocrTools() {
        assertEquals(AgentAction.ReadScreenText, parse("read_screen_text"))
        assertEquals(
            AgentAction.ReadTextAt(10, 20, 300, 40),
            parse("read_text_at", """{"x":10,"y":20,"width":300,"height":40}"""),
        )
        assertInvalid(parse("read_text_at", """{"x":10,"y":20,"width":300}"""), "height")
    }

    @Test
    fun finishReadsSuccessAndSummary() {
        assertEquals(AgentAction.Finish(true, "Done"), parse("finish", """{"success":true,"summary":"Done"}"""))
        assertEquals(AgentAction.Finish(true, ""), parse("finish", """{"success":"true"}"""))
        assertEquals(AgentAction.Finish(false, ""), parse("finish"))
        assertEquals(AgentAction.Finish(false, ""), parse("finish", """{"success":{"nested":true}}"""))
    }

    @Test
    fun unknownToolIsReportedBackToTheModel() {
        assertEquals(AgentAction.Invalid("Unknown tool: launch_rocket"), parse("launch_rocket"))
    }

    @Test
    fun everyDeclaredToolHasAMapping() {
        val declared = AgentTools.definitions.map { it.jsonObject.stringOrNull("name")!! }
        for (name in declared) {
            val action = parse(name)
            val unknown = action is AgentAction.Invalid && action.message.startsWith("Unknown tool")
            assertTrue("$name is declared to the model but not mapped", !unknown)
        }
    }

    @Test
    fun cropRegionClipsToTheScreenshot() {
        fun crop(x: Int, y: Int, w: Int, h: Int) = AgentActions.cropRegion(AgentAction.ReadTextAt(x, y, w, h), 1080, 2400)

        assertEquals(CropRect(10, 20, 300, 40), crop(10, 20, 300, 40))
        // Hanging off the right / bottom edge: keep the visible part.
        assertEquals(CropRect(1000, 2380, 80, 20), crop(1000, 2380, 200, 200))
        // Starting left of / above the screen: clip from 0 and keep the far edge where it was.
        assertEquals(CropRect(0, 0, 90, 30), crop(-10, -10, 100, 40))
        // Nothing on screen, or no area at all.
        assertNull(crop(1100, 20, 100, 100))
        assertNull(crop(10, 20, 0, 40))
        assertNull(crop(10, 20, -50, 40))
        assertNull(crop(Int.MAX_VALUE, 0, Int.MAX_VALUE, 10))
    }

    @Test
    fun narrationNeverThrowsOnOddInput() {
        assertEquals("Typing: hi", AgentActions.narration("type_text", input("""{"text":"hi"}""")))
        assertEquals("Typing.", AgentActions.narration("type_text", input("""{"text":["not","a","string"]}""")))
        assertEquals("Going back.", AgentActions.narration("key", input("""{"action":"back"}""")))
        assertEquals("Pressing a key.", AgentActions.narration("key", input("""{"action":null}""")))
        assertEquals("Working.", AgentActions.narration("something_else", input("{}")))
    }
}
