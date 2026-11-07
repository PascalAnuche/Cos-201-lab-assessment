import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the HTML files in the web folder and fills in their placeholders.
 * A placeholder is a name in double braces, for example {{totalUnits}}.
 */
public class TemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final Path folder;

    public TemplateEngine(Path folder) {
        this.folder = folder;
    }

    /**
     * Return the template with each placeholder replaced by its value.
     * Placeholders with no value become empty. Values are inserted as they
     * are, so text typed by the user must be passed through escape() first.
     */
    public String render(String templateName, Map<String, String> values) throws IOException {
        String template = Files.readString(folder.resolve(templateName), StandardCharsets.UTF_8);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder html = new StringBuilder();

        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(html, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(html);
        return html.toString();
    }

    /** Read a file from the web folder as bytes (used for style.css). */
    public byte[] readFile(String fileName) throws IOException {
        return Files.readAllBytes(folder.resolve(fileName));
    }

    /** Make text safe to put inside HTML, e.g. "<b>" becomes "&lt;b&gt;". */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  safe.append("&amp;");  break;
                case '<':  safe.append("&lt;");   break;
                case '>':  safe.append("&gt;");   break;
                case '"':  safe.append("&quot;"); break;
                case '\'': safe.append("&#39;");  break;
                default:   safe.append(c);
            }
        }
        return safe.toString();
    }
}
