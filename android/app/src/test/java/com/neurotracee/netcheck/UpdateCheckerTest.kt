package com.neurotracee.netcheck

import com.neurotracee.netcheck.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检查更新的纯逻辑测试：不联网，只验版本号归一化与比较。
 * 远端返回什么由两个仓库的 Releases 决定，这里只保证「本机比远端新 / 旧 / 相同」判断不会错。
 */
class UpdateCheckerTest {

    @Test
    fun normalizeStripsPrefixAndPadsToFour() {
        assertEquals(listOf(1, 1, 0, 1), UpdateChecker.normalize("v1.1.0.1"))
        assertEquals(listOf(1, 1, 0, 1), UpdateChecker.normalize("V1.1.0.1"))
        assertEquals(listOf(1, 1, 0, 0), UpdateChecker.normalize("1.1"))
        assertEquals(listOf(0, 0, 0, 0), UpdateChecker.normalize(""))
    }

    @Test
    fun higherRevisionIsNewer() {
        assertTrue(UpdateChecker.isNewer("v1.1.0.2", "1.1.0.1"))
        assertTrue(UpdateChecker.isNewer("1.1.1.0", "1.1.0.9"))
        assertTrue(UpdateChecker.isNewer("1.2.0.0", "1.1.0.1"))
        assertTrue(UpdateChecker.isNewer("2.0.0.0", "1.1.0.1"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("v1.1.0.1", "1.1.0.1"))
        assertFalse(UpdateChecker.isNewer("1.1.0.0", "1.1.0.1"))
        assertFalse(UpdateChecker.isNewer("1.0.0.0", "1.1.0.1"))
    }

    @Test
    fun garbageTagDoesNotCrashOrClaimNewer() {
        // 远端 tag 不是数字（例如手工打了 v-next 之类）时，归一化为 0，不能误报「有新版本」
        assertFalse(UpdateChecker.isNewer("v-next", "1.1.0.1"))
        assertFalse(UpdateChecker.isNewer("latest", "1.1.0.1"))
    }
}
