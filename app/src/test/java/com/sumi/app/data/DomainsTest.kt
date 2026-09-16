package com.sumi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {

    @Test
    fun `every element has somewhere to start`() {
        Element.entries.forEach { element ->
            assertTrue(element.name, Domains.under(element).size >= 3)
        }
    }

    @Test
    fun `names are distinct, so a domain is known by its name`() {
        val names = Domains.common.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every domain says what it holds and starts with words`() {
        Domains.common.forEach { domain ->
            assertTrue(domain.name, domain.holds.isNotBlank())
            assertTrue(domain.name, domain.words.size >= 4)
            assertTrue(domain.name, domain.words.none { it.isBlank() })
        }
    }

    /** A domain cannot hold the same word twice: the database would refuse the second. */
    @Test
    fun `words inside one domain are distinct`() {
        Domains.common.forEach { domain ->
            val lowered = domain.words.map { it.lowercase() }
            assertEquals(domain.name, lowered.size, lowered.toSet().size)
        }
    }

    /** Words are what you did, not parts of life: no word repeats a domain's name. */
    @Test
    fun `no word is the name of a domain`() {
        val domainNames = Domains.common.map { it.name.lowercase() }.toSet()
        Domains.common.forEach { domain ->
            domain.words.forEach { word ->
                assertTrue("${domain.name}: $word", word.lowercase() !in domainNames)
            }
        }
    }

    @Test
    fun `what a domain holds is found whatever the spacing or case`() {
        assertNotNull(Domains.holdsFor("Health"))
        assertEquals(Domains.holdsFor("Health"), Domains.holdsFor("  health "))
        assertNull(Domains.holdsFor("Guitar"))
        assertTrue(Domains.wordsFor("Guitar").isEmpty())
        assertTrue(Domains.wordsFor("health").isNotEmpty())
    }
}
