package com.sumi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `every preset names all five elements`() {
        Presets.all.forEach { preset ->
            assertEquals(preset.title, Element.entries.toSet(), preset.names.keys)
            assertEquals(preset.title, Element.entries.toSet(), preset.shape.keys)
        }
    }

    @Test
    fun `named presets have five distinct names`() {
        Presets.all.filterNot { it.isBlank }.forEach { preset ->
            val names = preset.names.values
            assertTrue(preset.title, names.none { it.isBlank() })
            assertEquals(preset.title, 5, names.toSet().size)
        }
    }

    @Test
    fun `titles are distinct and the blank one is last`() {
        assertEquals(Presets.all.size, Presets.all.map { it.title }.toSet().size)
        assertTrue(Presets.all.last().isBlank)
        assertEquals(1, Presets.all.count { it.isBlank })
    }

    /** Every example shape has to fit the pentagon: nothing past the outer ring. */
    @Test
    fun `example shapes stay inside the pentagon and lean`() {
        Presets.all.filterNot { it.isBlank }.forEach { preset ->
            preset.shape.values.forEach { share ->
                assertTrue(preset.title, share in 0f..1f)
            }
            // One spoke reaches the edge, as on Balance, where lengths are drawn
            // relative to the longest one.
            assertEquals(preset.title, 1f, preset.shape.values.max(), 0.0001f)
            // And no example is a regular pentagon: an even life is not the claim.
            assertTrue(preset.title, preset.shape.values.min() < 0.9f)
        }
    }

    @Test
    fun `the blank preset draws nothing`() {
        val blank = Presets.all.last()
        assertTrue(blank.names.values.all { it.isBlank() })
        assertTrue(blank.shape.values.all { it == 0f })
    }

    @Test
    fun `a shape part way between two presets is part way on every spoke`() {
        val from = Presets.all[0]
        val to = Presets.all[1]
        val half = Presets.shapeBetween(from, to, 0.5f)
        Element.entries.forEach { element ->
            val expected = (from.shape.getValue(element) + to.shape.getValue(element)) / 2f
            assertEquals(element.name, expected, half.getValue(element), 0.0001f)
        }
    }

    @Test
    fun `a fraction outside the wheel is clamped to the ends`() {
        val from = Presets.all[0]
        val to = Presets.all[1]
        assertEquals(from.shape, Presets.shapeBetween(from, to, -2f))
        assertEquals(to.shape, Presets.shapeBetween(from, to, 4f))
    }

    /** Elements can be moved between goals, so names follow the element, not the row. */
    @Test
    fun `names land by element even when the slots are shuffled`() {
        val shuffled = listOf(
            Goal(slot = 0, name = "", element = Element.VOID),
            Goal(slot = 1, name = "", element = Element.FIRE),
            Goal(slot = 2, name = "", element = Element.EARTH),
            Goal(slot = 3, name = "", element = Element.WATER),
            Goal(slot = 4, name = "", element = Element.WIND)
        )
        val preset = Presets.all.first()
        val names = Presets.namesForSlots(preset, shuffled)

        assertEquals(preset.names.getValue(Element.VOID), names[0])
        assertEquals(preset.names.getValue(Element.FIRE), names[1])
        assertEquals(preset.names.getValue(Element.EARTH), names[2])
        assertEquals(preset.names.getValue(Element.WATER), names[3])
        assertEquals(preset.names.getValue(Element.WIND), names[4])
    }
}
