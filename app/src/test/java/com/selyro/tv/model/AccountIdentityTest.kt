package com.selyro.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountIdentityTest {
    @Test
    fun newAccountsReceiveStableUniqueIds() {
        val first = PlaylistAccount("One", "http://one.example")
        val second = PlaylistAccount("Two", "http://two.example")

        assertTrue(first.id.isNotBlank())
        assertTrue(second.id.isNotBlank())
        assertNotEquals(first.id, second.id)
        assertEquals(first.id, first.copy(name = "Renamed").id)
    }

    @Test
    fun explicitIdSurvivesCredentialAndServerEdits() {
        val original = PlaylistAccount(
            name = "Provider",
            server = "http://old.example",
            username = "old-user",
            password = "old-pass",
            type = SourceType.XTREAM,
            id = "account-123"
        )

        val edited = original.copy(
            server = "https://new.example",
            username = "new-user",
            password = "new-pass"
        )

        assertEquals("account-123", edited.id)
    }
}
