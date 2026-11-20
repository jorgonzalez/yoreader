package com.yoreader.app.ui.articles

import com.yoreader.app.data.ArticleListRow
import org.junit.Assert.assertEquals
import org.junit.Test

class WithOpenedTest {
    private fun row(id: Long, publishedAt: Long?, isRead: Boolean = false) =
        ArticleListRow(id, 1, "Feed", "Title $id", null, publishedAt, isRead, false)

    @Test
    fun noOpenedArticlesKeepsTheRows() {
        val rows = listOf(row(1, 300), row(2, 200))
        assertEquals(rows.map { it to false }, withOpened(rows, emptyMap()))
    }

    @Test
    fun anOpenedArticleGoneFromTheQueryStaysAtItsPlaceShownAsRead() {
        val rows = listOf(row(1, 300), row(3, 100), row(4, null))
        val opened = mapOf(2L to row(2, 200), 5L to row(5, null))
        val result = withOpened(rows, opened)
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), result.map { it.first.id })
        assertEquals(listOf(false, true, false, true, false), result.map { it.second })
        assertEquals(true, result[1].first.isRead)
    }

    @Test
    fun anOpenedArticleStillInTheQueryIsNotDoubled() {
        val rows = listOf(row(1, 300), row(2, 200))
        val result = withOpened(rows, mapOf(2L to row(2, 200)))
        assertEquals(rows.map { it to false }, result)
    }
}
