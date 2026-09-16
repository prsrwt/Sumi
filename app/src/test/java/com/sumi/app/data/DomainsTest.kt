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
            assertTrue(element.name, Domains.under(element).isNotEmpty())
        }
    }

    @Test
    fun `names are distinct, so a domain is known by its name`() {
        val names = Domains.common.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every domain says what it holds`() {
        Domains.common.forEach { assertTrue(it.name, it.holds.isNotBlank()) }
    }

    @Test
    fun `what a domain holds is found whatever the spacing or case`() {
        assertNotNull(Domains.holdsFor("Health"))
        assertEquals(Domains.holdsFor("Health"), Domains.holdsFor("  health "))
        assertNull(Domains.holdsFor("Guitar"))
    }
}
