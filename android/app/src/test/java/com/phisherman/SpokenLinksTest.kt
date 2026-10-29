package com.phisherman

import com.phisherman.voice.SpokenLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web addresses read out on a call, as Live Caption writes them. */
class SpokenLinksTest {
    private fun urls(t: String) = SpokenLinks.find(t).map { it.url }

    @Test
    fun spelledAndSpokenAddresses() {
        assertEquals(listOf("https://sbi-rewards.com/claim"), urls("Please type s b i dash rewards dot com slash claim."))
        assertEquals(listOf("https://www.hdfc-kyc.in"), urls("go to w w w dot hdfc hyphen kyc dot in now"))
        assertEquals(listOf("https://sbi.co.in"), urls("our site is sbi dot co dot in"))
        assertEquals(listOf("https://paytm-refund.xyz/login"), urls("Open paytm-refund.xyz/login and enter details"))
        assertEquals(listOf("https://amazon.in"), urls("Order it on amazon dot in, it's cheaper."))
    }

    @Test
    fun twoWordNamesAlsoTriedJoined() {
        val c = SpokenLinks.find("open sbi rewards dot com").single()
        assertEquals("https://rewards.com", c.url)
        assertEquals(listOf("https://sbirewards.com", "https://sbi-rewards.com"), c.alternatives)
        assertTrue(SpokenLinks.find("go to amazon dot in").single().alternatives.isEmpty())  // "to" is an ordinary word
    }

    @Test
    fun ordinarySpeechHasNoLinks() {
        assertTrue(urls("I reached home. In the evening we will go out. Dot your i's.").isEmpty())
        assertTrue(urls("Call me at 9 8 7 6 5 4 3 2 1 0 tomorrow").isEmpty())
        assertTrue(urls("The meeting is at 5. Com on, be on time").isEmpty())
    }
}
