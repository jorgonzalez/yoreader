package com.yoreader.app.ui.articles

import com.yoreader.app.data.ArticleListRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BelongsInTest {
    private fun row(read: Boolean = false, saved: Boolean = false) =
        ArticleListRow(1, 1, "Feed", "Title", null, 100, read, saved)

    @Test
    fun latestListsUnreadArticlesThatAreNotSaved() {
        assertTrue(belongsIn(ArticleScope.Latest, true, row()))
        assertFalse(belongsIn(ArticleScope.Latest, true, row(read = true)))
        assertFalse(belongsIn(ArticleScope.Latest, true, row(saved = true)))
    }

    @Test
    fun savedListsSavedArticlesReadOrNot() {
        assertTrue(belongsIn(ArticleScope.Saved, true, row(read = true, saved = true)))
        assertFalse(belongsIn(ArticleScope.Saved, true, row()))
    }

    @Test
    fun otherListsDropReadArticlesOnlyWhenShowingUnread() {
        val feed = ArticleScope.Feed(1)
        assertTrue(belongsIn(feed, true, row(saved = true)))
        assertFalse(belongsIn(feed, true, row(read = true)))
        assertTrue(belongsIn(feed, false, row(read = true)))
    }
}
