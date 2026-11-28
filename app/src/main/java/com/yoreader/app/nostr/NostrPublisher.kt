package com.yoreader.app.nostr

import com.yoreader.app.YoReaderApp

/**
 * Publishes a link to the user's relays as a web bookmark (and, if [alsoNote], a plain
 * note) with [comment]. The first time, or once the relays changed, also publishes the
 * relay list so followers know where to read. Returns a short summary to show, or null
 * if there's no Nostr key. Used for feed articles and for any page opened in the app.
 */
suspend fun YoReaderApp.publishLinkOnNostr(
    link: String,
    title: String,
    comment: String,
    alsoNote: Boolean,
): String? {
    val key = nostrKeys.key.value ?: return null
    val results = nostr.publish(NostrShare.bookmark(key, link, title, comment))
    if (alsoNote) nostr.publish(NostrShare.note(key, link, title, comment))
    val relays = settings.nostrRelays.value
    val listed = relays.joinToString("\n")
    if (settings.nostrPublishedRelays != listed) {
        val sent = nostr.publish(NostrShare.relayList(key, relays))
        if (sent.any { it.second == NostrRelay.PublishResult.Accepted }) settings.nostrPublishedRelays = listed
    }
    return NostrShare.summary(results)
}
