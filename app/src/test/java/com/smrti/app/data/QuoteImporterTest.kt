package com.smrti.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteImporterTest {

    @Test fun plainStrings() {
        val quotes = QuoteImporter.parse("""["one", "  two  ", ""]""")
        assertEquals(listOf("one", "two"), quotes.map { it.text })
        assertTrue(quotes.all { it.category == "Quote" })
    }

    @Test fun objectsWithFlexibleFieldNames() {
        val quotes = QuoteImporter.parse(
            """[{"quote":"Hi","by":"Me","type":"motivation"},{"content":"Yo","speaker":"You"}]"""
        )
        assertEquals("Hi", quotes[0].text)
        assertEquals("Me", quotes[0].author)
        assertEquals("Motivation", quotes[0].category) // first letter is capitalised
        assertEquals("You", quotes[1].author)
    }

    @Test fun wrapperObject() {
        assertEquals(2, QuoteImporter.parse("""{"quotes":[{"text":"a"},{"text":"b"}]}""").size)
    }

    @Test fun itemsWithoutTextAreSkipped() {
        assertEquals(1, QuoteImporter.parse("""[{"author":"nobody"},{"text":"kept"}]""").size)
    }

    @Test fun byteOrderMarkIsIgnored() {
        assertEquals(1, QuoteImporter.parse("\uFEFF[\"x\"]").size)
    }
}
