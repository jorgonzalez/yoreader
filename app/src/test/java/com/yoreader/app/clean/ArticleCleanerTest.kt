package com.yoreader.app.clean

import com.yoreader.app.fetch.Html
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleCleanerTest {

    private val body = List(6) { i ->
        "<p>Párrafo número ${i + 1} del artículo. El Gobierno anunció hoy nuevas medidas para " +
            "reforzar la seguridad en la frontera, según explicaron fuentes oficiales a la prensa.</p>"
    }.joinToString("\n")

    private val page = """
        <!DOCTYPE html>
        <html lang="es"><head><meta charset="utf-8"><title>Noticia</title>
        <script>var tracking = "no leer esto";</script></head>
        <body>
          <nav><a href="/">Portada</a> <a href="/deportes">Deportes</a> <a href="/opinion">Opinión</a></nav>
          <div class="cookie-banner">Aceptar cookies</div>
          <main><article>
            <h1>Titular de la noticia</h1>
            <figure><img src="/img/foto.jpg"><figcaption>Pie de foto</figcaption></figure>
            $body
          </article></main>
          <aside class="related"><h3>Te puede interesar</h3><ul><li><a href="/x">Otra noticia</a></li></ul></aside>
          <footer>© Periódico. Aviso legal.</footer>
        </body></html>
    """.trimIndent()

    @Test
    fun keepsTheArticleAndDropsClutter() {
        val result = ArticleCleaner.extract(page.toByteArray(), "https://example.es/noticia")
        assertTrue(result is CleanResult.Success)
        result as CleanResult.Success
        assertTrue("Párrafo número 1" in result.text)
        assertTrue("Párrafo número 6" in result.text)
        for (clutter in listOf("Portada", "Aceptar cookies", "Te puede interesar", "Aviso legal", "no leer esto")) {
            assertFalse("should drop '$clutter'", clutter in result.text)
        }
        // Paragraphs are kept apart for reading aloud.
        assertTrue(result.text.split("\n\n").size >= 6)
        // Relative image links are made absolute so the reader can load them.
        assertTrue("https://example.es/img/foto.jpg" in result.html)
    }

    @Test
    fun teaserPagesAreRejected() {
        val result = ArticleCleaner.extract(
            "<html><body><p>Suscríbete para seguir leyendo.</p></body></html>".toByteArray(),
            "https://example.es/x",
        )
        assertTrue(result is CleanResult.Failure)
    }

    @Test
    fun paragraphsFromHtml() {
        assertEquals(
            listOf("Título", "Uno & dos", "Tres", "cuatro"),
            Html.toParagraphs(
                "<h2>Título</h2><p>Uno &amp; dos</p><script>x()</script>" +
                    "<figure><figcaption>pie</figcaption></figure><p>Tres<br>cuatro</p>"
            ),
        )
    }

    @Test
    fun paragraphsSkipHtmlInAttributesAndHiddenText() {
        // As on cnnespanol.cnn.com: a video player with HTML inside its attributes.
        assertEquals(
            listOf("Visible", "Also visible"),
            Html.toParagraphs(
                "<div data-description=\"<p>Secret description</p>\" data-x='a > b'>" +
                    "<p>Visible</p></div><p hidden>Hidden</p>" +
                    "<span aria-hidden=\"true\">icon</span><div style=\"display: none\">no</div>" +
                    "<p>Also visible</p>"
            ),
        )
    }
}
