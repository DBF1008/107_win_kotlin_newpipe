package org.schabi.newpipe.streams;

import org.junit.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.schabi.newpipe.streams.io.SharpStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SrtFromTtmlWriter}.
 *
 * Tests focus on {@code extractText()} and its handling of TTML <p> elements.
 * Note:
 * - Uses reflection to call the private {@code extractText()} method.
 * - Update {@code EXTRACT_TEXT_METHOD} if renamed.
 *
 * ---
 * NOTE ABOUT ENTITIES VS UNICODE ESCAPES
 *
 * - In short:
 *   * UNICODE ESCAPES → used in Java source (e.g. SrtFromTtmlWriter.java)
 *   * ENTITIES → used in TTML strings (this test file)
 *
 * - TTML is an XML-based format. Real TTML subtitles often encode special
 *   characters as XML entities (named or numeric), e.g.:
 *       &amp;    → '&' (\u0026)
 *       &lt;     → '<' (\u003C)
 *       &#x9;    → tab (\u0009)
 *       &#xA;    → line feed (\u000A)
 *       &#xD;    → carriage return (\u000D)
 *
 * - Java source code uses **Unicode escapes** (e.g. "\u00A0") which are resolved
 *   at compile time, so they do not represent real XML entities.
 *
 * - Purpose of these tests:
 *   We simulate *real TTML input* as NewPipe receives it — i.e., strings that
 *   still contain encoded XML entities (&#x9;, &#xA;, &#xD;, etc.).
 *   The production code (`decodeXmlEntities()`) must convert these into their
 *   actual Unicode characters before normalization.
 */
public class SrtFromTtmlWriterTest {
    private static final String TTML_WRAPPER_START = "<tt><body><div>";
    private static final String TTML_WRAPPER_END = "</div></body></tt>";
    private static final String EXTRACT_TEXT_METHOD = "extractText";
    // Please keep the same definition from `SrtFromTtmlWriter` class.
    private static final String NEW_LINE = "\r\n";

    /*
     * TTML example for simple paragraph <p> without nested tags.
     * <p begin="00:00:01.000" end="00:00:03.000" style="s2">Hello World!</p>
     */
    private static final String SIMPLE_TTML = "<p begin=\"00:00:01.000\" end=\"00:00:03.000\" "
            + "style=\"s2\">Hello World!</p>";
    /**
     * TTML example with nested tags with <br>.
     * <p begin="00:00:01.000" end="00:00:03.000"><span style="s4">Hello</span><br>World!</p>
     */
    private static final String NESTED_TTML = "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
            + "<span style=\"s4\">Hello</span><br>World!</p>";

    /**
     * TTML example with HTML entities.
     * &lt; → <, &gt; → >, &amp; → &, &quot; → ", &apos; → '
     * &#39; → '
     * &#xA0; → ' '
     */
    private static final String ENTITY_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&lt;tag&gt; &amp; &quot;text&quot;&apos;&apos;&#39;&#39;"
            + "&#xA0;&#xA0;"
            + "</p>";
    /**
     * TTML example with special characters:
     * - Spaces appear at the beginning and end of the text.
     * - Spaces are also present within the text (not just at the edges).
     * - The text includes various HTML entities such as &nbsp;,
     *   &amp;, &lt;, &gt;, etc.
     * &nbsp; → non-breaking space (Unicode: '\u00A0', Entity: '&#xA0;')
     */
    private static final String SPECIAL_TTML = "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">"
            + "   ～~-Hello&nbsp;&nbsp;&amp;&amp;&lt;&lt;&gt;&gt;World!!   "
            + "</p>";

    /**
     * TTML example with characters: tab.
     * &#x9; → \t
     * They are separated by '+' for clarity.
     */
    private static final String TAB_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x9;&#x9;+&#x9;&#x9;+&#x9;&#x9;"
            + "</p>";

    /**
     * TTML example with line endings.
     * &#xD; → \r
     */
    private static final String LINE_ENDING_0_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#xD;&#xD;+&#xD;&#xD;+&#xD;&#xD;"
            + "</p>";
    // &#xA; → \n
    private static final String LINE_ENDING_1_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#xA;&#xA;+&#xA;&#xA;+&#xA;&#xA;"
            + "</p>";
    private static final String LINE_ENDING_2_TTML =
            "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">"
            + "&#xD;&#xA;+&#xD;&#xA;+&#xD;&#xA;"
            + "</p>";

    /**
     * TTML example with control characters.
     * For example:
     * &#x0001; → \u0001
     * &#x001F; → \u001F
     *
     * These control characters, if included as raw Unicode(e.g. '\u0001'),
     * are either invalid in XML or rendered as '?' when processed.
     * To avoid issues, they should be encoded(e.g. '&#x0001;') in TTML file.
     *
     * - Reference:
     *   Unicode Basic Latin (https://unicode.org/charts/PDF/U0000.pdf),
     *   ASCII Control (https://en.wikipedia.org/wiki/ASCII#Control_characters).
     *   and the defination of these characters can be known.
     */
    private static final String CONTROL_CHAR_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x0001;+&#x0008;+&#x000B;+&#x000C;+&#x000E;+&#x001F;"
            + "</p>";



    private static final String EMPTY_TTML = "<p begin=\"00:00:01.000\" "
            + "end=\"00:00:03.000\">"
            + ""
            + "</p>";

    /**
     * TTML example with Unicode space characters.
     * These characters are encoded using character references
     * (&#xXXXX;).
     *
     * Includes:
     * (&#x202F;) '\u202F' → Narrow no-break space
     * (&#x205F;) '\u205F' → Medium mathematical space
     * (&#x3000;) '\u3000' → Ideographic space
     * '\u2000' ~ '\u200A' are whitespace characters:
     * (&#x2000;) '\u2000' → En quad
     * (&#x2002;) '\u2002' → En space
     * (&#x200A;) '\u200A' → Hair space
     *
     * Each character is separated by '+' for clarity.
     */
    private static final String UNICODE_SPACE_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x202F;+&#x205F;+&#x3000;+&#x2000;+&#x2002;+&#x200A;"
            + "</p>";

    /**
     * TTML example with non-spacing (invisible) characters.
     * These are encoded using character references (&#xXXXX;).
     *
     * Includes:
     * (&#x200B;)'\u200B' → Zero-width space (ZWSP)
     * (&#x200E;)'\u200E' → Left-to-right mark (LRM)
     * (&#x200F;)'\u200F' → Right-to-left mark (RLM)
     *
     * They don't display any characters to the human eye.
     * '+' is used between them for clarity in test output.
     */
    private static final String NON_SPACING_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x200B;+&#x200E;+&#x200F;"
            + "</p>";

    /**
     * Parses TTML string into a JSoup Document and selects the first <p> element.
     *
     * @param ttmlContent TTML content (e.g., <p>...</p>)
     * @return the first <p> element
     * @throws Exception if parsing or reflection fails
     */
    private Element parseTtmlParagraph(final String ttmlContent) throws Exception {
        final String ttml = TTML_WRAPPER_START + ttmlContent + TTML_WRAPPER_END;
        final Document doc = Jsoup.parse(
                new ByteArrayInputStream(ttml.getBytes(StandardCharsets.UTF_8)),
                "UTF-8", "", Parser.xmlParser());
        return doc.select("body > div > p").first();
    }

    /**
     * Invokes private extractText method via reflection.
     *
     * @param writer SrtFromTtmlWriter instance
     * @param paragraph <p> element to extract text from
     * @param text StringBuilder to store extracted text
     * @throws Exception if reflection fails
     */
    private void invokeExtractText(final SrtFromTtmlWriter writer, final Element paragraph,
                                  final StringBuilder text) throws Exception {
        final Method method = writer.getClass()
                .getDeclaredMethod(EXTRACT_TEXT_METHOD, Node.class, StringBuilder.class);
        method.setAccessible(true);
        method.invoke(writer, paragraph, text);
    }

    private String extractTextFromTtml(final String ttmlInput) throws Exception {
        final Element paragraph = parseTtmlParagraph(ttmlInput);
        final StringBuilder text = new StringBuilder();
        final SrtFromTtmlWriter writer = new SrtFromTtmlWriter(null, false);
        invokeExtractText(writer, paragraph, text);

        final String actualText = text.toString();
        return actualText;
    }

    @Test
    public void testExtractTextSimpleParagraph() throws Exception {
        final String expected = "Hello World!";
        final String actual = extractTextFromTtml(SIMPLE_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextNestedTags() throws Exception {
        final String expected = "Hello\r\nWorld!";
        final String actual = extractTextFromTtml(NESTED_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithEntity() throws Exception {
        final String expected = "<tag> & \"text\"''''  ";
        final String actual = extractTextFromTtml(ENTITY_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithSpecialCharacters() throws Exception {
        final String expected = "   ～~-Hello  &&<<>>World!!   ";
        final String actual = extractTextFromTtml(SPECIAL_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithTab() throws Exception {
        final String expected = "  +  +  ";
        final String actual = extractTextFromTtml(TAB_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding0() throws Exception {
        final String expected = NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_0_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding1() throws Exception {
        final String expected = NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_1_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding2() throws Exception {
        final String expected = NEW_LINE + "+"
                                + NEW_LINE + "+"
                                + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_2_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithControlCharacters() throws Exception {
        final String expected = "+++++";
        final String actual = extractTextFromTtml(CONTROL_CHAR_TTML);
        assertEquals(expected, actual);
    }

    /**
    * Test case to ensure that extractText() does not throw an exception
    * when there are no text in the TTML paragraph (i.e., the paragraph
    * is empty).
    *
    * Note:
    *   In the NewPipe, *.srt files will contain empty text lines by default.
    */
    @Test
    public void testExtractTextWithEmpty() throws Exception {
        final String expected = "";
        final String actual = extractTextFromTtml(EMPTY_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithUnicodeSpaces() throws Exception {
        final String expected = " + + + + + ";
        final String actual = extractTextFromTtml(UNICODE_SPACE_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithNonSpacingCharacters() throws Exception {
        final String expected = "++";
        final String actual = extractTextFromTtml(NON_SPACING_TTML);
        assertEquals(expected, actual);
    }

    // ================================================================
    // End-to-end tests for build()
    // ================================================================

    /**
     * Minimal in-memory {@link SharpStream} for unit-testing
     * {@link SrtFromTtmlWriter#build(SharpStream)}.
     *
     * <p>The input side (read) is backed by a {@link ByteArrayInputStream},
     * and the output side (write) is backed by a
     * {@link ByteArrayOutputStream}.</p>
     */
    private static class InMemorySharpStream extends SharpStream {
        private final ByteArrayInputStream input;
        private final ByteArrayOutputStream output;
        private boolean closed = false;

        InMemorySharpStream(final byte[] inputData) {
            this.input = new ByteArrayInputStream(inputData);
            this.output = new ByteArrayOutputStream();
        }

        InMemorySharpStream() {
            this(new byte[0]);
        }

        String getOutputString() {
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public int read(final byte[] buffer) throws IOException {
            return input.read(buffer);
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int count)
                throws IOException {
            return input.read(buffer, offset, count);
        }

        @Override
        public long skip(final long amount) throws IOException {
            return input.skip(amount);
        }

        @Override
        public long available() {
            return input.available();
        }

        @Override
        public void rewind() {
            // not needed for these tests
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean canRewind() {
            return false;
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public void write(final byte value) throws IOException {
            output.write(value);
        }

        @Override
        public void write(final byte[] buffer) throws IOException {
            output.write(buffer);
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int count)
                throws IOException {
            output.write(buffer, offset, count);
        }
    }

    /**
     * Runs the full TTML → SRT conversion and returns the SRT output.
     *
     * @param ttml the TTML document (must include {@code <tt><body><div>...})
     * @param ignoreEmptyFrames whether to skip empty paragraphs
     * @return the generated SRT string
     */
    private String runBuild(final String ttml, final boolean ignoreEmptyFrames)
            throws IOException {
        final byte[] ttmlBytes = ttml.getBytes(StandardCharsets.UTF_8);
        final InMemorySharpStream inputStream = new InMemorySharpStream(ttmlBytes);
        final InMemorySharpStream outputStream = new InMemorySharpStream();
        final SrtFromTtmlWriter writer = new SrtFromTtmlWriter(
                outputStream, ignoreEmptyFrames);
        writer.build(inputStream);
        return outputStream.getOutputString();
    }

    /**
     * Build a complete TTML document from one or more {@code <p>} elements.
     */
    private static String wrapTtml(final String... paragraphs) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<tt><body><div>");
        for (final String p : paragraphs) {
            sb.append(p);
        }
        sb.append("</div></body></tt>");
        return sb.toString();
    }

    // ---- Test: paragraph containing only &#xA; is skipped ----

    @Test
    public void testBuildIgnoresNewlineEntityOnlyParagraph() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">&#xA;</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">Hello</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "Hello\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: paragraph containing only <br/> is skipped ----

    @Test
    public void testBuildIgnoresBrOnlyParagraph() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\"><br/></p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">World</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "World\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: paragraph containing only &#xD; (CR) is skipped ----

    @Test
    public void testBuildIgnoresCrEntityOnlyParagraph() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">&#xD;</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">Text</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "Text\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: correct numbering after skipping multiple empty frames ----

    @Test
    public void testBuildCorrectNumberingAfterSkippingEmptyFrames()
            throws IOException {
        final String ttml = wrapTtml(
                // empty: only &#xA;
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">&#xA;</p>",
                // non-empty
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">First</p>",
                // empty: only <br/>
                "<p begin=\"00:00:05.000\" end=\"00:00:06.000\"><br/></p>",
                // non-empty
                "<p begin=\"00:00:07.000\" end=\"00:00:08.000\">Second</p>",
                // empty: only &#xD;
                "<p begin=\"00:00:09.000\" end=\"00:00:10.000\">&#xD;</p>",
                // non-empty
                "<p begin=\"00:00:11.000\" end=\"00:00:12.000\">Third</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "First\r\n"
                + "\r\n"
                + "2\r\n"
                + "00:00:07,000 --> 00:00:08,000\r\n"
                + "Second\r\n"
                + "\r\n"
                + "3\r\n"
                + "00:00:11,000 --> 00:00:12,000\r\n"
                + "Third\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: nested tags with mixed <br/> and &#xA; collapse ----

    @Test
    public void testBuildNestedTagsWithMixedNewlinesCollapse()
            throws IOException {
        // <span>Line1<br/>&#xA;Line2</span>
        // <br> produces \r\n, &#xA; produces \r\n → two consecutive
        // newlines should collapse into one.
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "<span>Line1<br/>&#xA;Line2</span>"
                + "</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "Line1\r\n"
                + "Line2\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: deeply nested tags with redundant newlines ----

    @Test
    public void testBuildDeeplyNestedTagsWithRedundantNewlines()
            throws IOException {
        // <span>A<br/><span><br/>&#xA;</span>B</span>
        // Produces: A\r\n\r\n\r\nB → collapses to A\r\nB
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "<span>A<br/><span><br/>&#xA;</span>B</span>"
                + "</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "A\r\n"
                + "B\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: leading/trailing whitespace is trimmed ----

    @Test
    public void testBuildTrimsLeadingTrailingWhitespace() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "  Hello World  "
                + "</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "Hello World\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: leading/trailing <br/> tags are trimmed ----

    @Test
    public void testBuildTrimsLeadingTrailingBrTags() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "<br/>Hello<br/>"
                + "</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "Hello\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: empty frames preserved when ignoreEmptyFrames=false ----

    @Test
    public void testBuildPreservesEmptyFramesWhenNotIgnoring()
            throws IOException {
        final String ttml = wrapTtml(
                // This paragraph has only &#xA; → effectively empty
                // after trim. With ignoreEmptyFrames=false it must
                // still appear in the output (with empty text).
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">&#xA;</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">Hello</p>"
        );

        final String actual = runBuild(ttml, false);

        // Frame 1 is empty (text is ""), frame 2 is "Hello".
        // Both should appear with correct numbering.
        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:02,000\r\n"
                + "\r\n"
                + "\r\n"
                + "2\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "Hello\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: empty document (no paragraphs) produces no output ----

    @Test
    public void testBuildEmptyDocumentProducesNoOutput() throws IOException {
        final String ttml = "<tt><body><div></div></body></tt>";

        final String actual = runBuild(ttml, true);

        assertEquals("", actual);
    }

    // ---- Test: all-empty paragraphs produce no output ----

    @Test
    public void testBuildAllEmptyParagraphsProduceNoOutput() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">&#xA;</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\"><br/></p>",
                "<p begin=\"00:00:05.000\" end=\"00:00:06.000\">&#xD;</p>"
        );

        final String actual = runBuild(ttml, true);

        assertEquals("", actual);
    }

    // ---- Test: whitespace-only paragraph is treated as empty ----

    @Test
    public void testBuildWhitespaceOnlyParagraphIsSkipped() throws IOException {
        // Paragraph with only spaces and &#xA0; (non-breaking space)
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">"
                + " &#xA0; &#xA0; "
                + "</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">Content</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:03,000 --> 00:00:04,000\r\n"
                + "Content\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: normal multi-line paragraph is unaffected ----

    @Test
    public void testBuildNormalMultiLineParagraph() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "Line1<br/>Line2"
                + "</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "Line1\r\n"
                + "Line2\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: multiple consecutive empty frames skipped, numbering intact ----

    @Test
    public void testBuildConsecutiveEmptyFramesSkipped() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">A</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:04.000\">&#xA;</p>",
                "<p begin=\"00:00:05.000\" end=\"00:00:06.000\">&#xA;</p>",
                "<p begin=\"00:00:07.000\" end=\"00:00:08.000\">&#xA;</p>",
                "<p begin=\"00:00:09.000\" end=\"00:00:10.000\">B</p>"
        );

        final String actual = runBuild(ttml, true);

        // Only A (index 1) and B (index 2) should appear.
        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:02,000\r\n"
                + "A\r\n"
                + "\r\n"
                + "2\r\n"
                + "00:00:09,000 --> 00:00:10,000\r\n"
                + "B\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }

    // ---- Test: timestamp dots replaced with commas ----

    @Test
    public void testBuildTimestampDotToComma() throws IOException {
        final String ttml = wrapTtml(
                "<p begin=\"00:01:23.456\" end=\"00:01:25.789\">Hi</p>"
        );

        final String actual = runBuild(ttml, true);

        assertTrue("Timestamps should use commas",
                actual.contains("00:01:23,456 --> 00:01:25,789"));
    }

    // ---- Test: auto-generated subtitle pattern (common YouTube case) ----

    @Test
    public void testBuildAutoGeneratedSubtitlePattern() throws IOException {
        // Simulates a YouTube auto-generated TTML pattern where
        // empty frames with only &#xA; appear between real content.
        final String ttml = wrapTtml(
                "<p begin=\"00:00:00.000\" end=\"00:00:01.000\">&#xA;</p>",
                "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
                + "Welcome to the video"
                + "</p>",
                "<p begin=\"00:00:03.000\" end=\"00:00:03.500\">&#xA;</p>",
                "<p begin=\"00:00:03.500\" end=\"00:00:05.000\">"
                + "Today we will learn about SRT"
                + "</p>",
                "<p begin=\"00:00:05.000\" end=\"00:00:05.500\">&#xA;</p>"
        );

        final String actual = runBuild(ttml, true);

        final String expected =
                "1\r\n"
                + "00:00:01,000 --> 00:00:03,000\r\n"
                + "Welcome to the video\r\n"
                + "\r\n"
                + "2\r\n"
                + "00:00:03,500 --> 00:00:05,000\r\n"
                + "Today we will learn about SRT\r\n"
                + "\r\n";

        assertEquals(expected, actual);
    }
}
