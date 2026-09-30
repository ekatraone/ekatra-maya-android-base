package org.ekatra.alfred.data

import org.ekatra.alfred.persona.ActivePersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelTierTest {

    @Test
    fun `every preferred model exists in the catalog`() {
        ActivePersona.modelPreference.forEach { assertNotNull(it, AvailableModels.getModelById(it)) }
    }

    @Test
    fun `high-RAM phones get the top preference`() {
        assertEquals(ActivePersona.modelPreference.first(), AvailableModels.getDefaultModel(8).id)
    }

    @Test
    fun `mid-RAM phones skip models that do not fit`() {
        val pick = AvailableModels.getDefaultModel(4)
        assert(pick.minRamGB <= 4) { "picked ${pick.id} needing ${pick.minRamGB}GB" }
        assertEquals("lfm2.5-1.2b-instruct", pick.id)
    }

    @Test
    fun `tiny phones fall back to the floor model`() {
        assertEquals(ActivePersona.modelPreference.last(), AvailableModels.getDefaultModel(1).id)
    }
}
