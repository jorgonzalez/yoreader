package com.yoreader.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yoreader.app.ui.articles.ArticleListItem
import com.yoreader.app.ui.articles.ArticleRow
import com.yoreader.app.ui.components.EmptyState

/** Search box plus results grouped as Sources, Saved and Articles. */
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults?,
    onQueryChange: (String) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onOpenFeed: (Long) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search articles, saved and sources") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                results == null -> EmptyState(
                    "Search the titles and text of your articles, your saved items, and your sources.",
                )
                results.isEmpty -> EmptyState("No matches for \"${results.query}\".")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (results.sources.isNotEmpty()) {
                        header("Sources")
                        items(results.sources, key = { "feed-${it.id}" }) { feed ->
                            ListItem(
                                headlineContent = { Text(feed.title) },
                                supportingContent = {
                                    Text(feed.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                                modifier = Modifier.clickable { onOpenFeed(feed.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                    articleSection("Saved", results.saved, onOpenArticle)
                    articleSection("Articles", results.articles, onOpenArticle)
                }
            }
        }
    }
}

private fun LazyListScope.header(text: String) {
    item(key = "header-$text") {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.articleSection(
    title: String,
    items: List<ArticleListItem>,
    onOpenArticle: (Long) -> Unit,
) {
    if (items.isEmpty()) return
    header("$title (${items.size})")
    items(items, key = { "article-${it.row.id}" }) { item ->
        ArticleRow(item, onClick = { onOpenArticle(item.row.id) })
        HorizontalDivider()
    }
}
