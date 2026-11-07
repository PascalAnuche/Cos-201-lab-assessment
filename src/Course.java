import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One course the student is taking: a code, a title and credit units.
 *
 * Every value is cleaned and checked in the constructor, so a Course
 * object is always valid. Bad values cause an InvalidCourseException.
 */
public class Course {

    public static final int MIN_UNITS = 1;
    public static final int MAX_UNITS = 6;
    public static final int MAX_TITLE_LENGTH = 60;

    /**
     * 2-4 letters, then an optional space or hyphen, then 3 digits.
     * "COS 201", "cos201" and "Cos-201" all match.
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Z]{2,4})[\\s-]*(\\d{3})$");

    /** Short words that stay in lower case inside a title ("Introduction to ..."). */
    private static final Set<String> SMALL_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "as", "at", "by", "for", "in", "of", "on", "or", "the", "to", "with"));

    /** Roman numerals that stay in capitals ("Programming II", not "Ii"). */
    private static final Set<String> ROMAN_NUMERALS = new HashSet<>(Arrays.asList(
            "ii", "iii", "iv", "vi", "vii", "viii", "ix"));

    private final String code;
    private final String title;
    private final int units;

    public Course(String code, String title, String units) throws InvalidCourseException {
        this.code = cleanCode(code);
        this.title = cleanTitle(title);
        this.units = cleanUnits(units);
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public int getUnits() {
        return units;
    }

    /** The department letters of the code, e.g. "COS" from "COS 201". */
    public String getDepartment() {
        return code.substring(0, code.indexOf(' '));
    }

    /** The number part of the code, e.g. "201" from "COS 201". */
    public String getNumber() {
        return code.substring(code.indexOf(' ') + 1);
    }

    /** The level, taken from the first digit of the number: "COS 201" is a 200-level course. */
    public int getLevel() {
        return (getNumber().charAt(0) - '0') * 100;
    }

    // ------------------------------------------------------------------
    // Validation and string processing
    // ------------------------------------------------------------------

    /** Return the code in standard form, e.g. " cos201 " becomes "COS 201". */
    public static String cleanCode(String raw) throws InvalidCourseException {
        String text = (raw == null) ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new InvalidCourseException("code", "Please enter a course code.");
        }

        Matcher match = CODE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        if (!match.matches()) {
            throw new InvalidCourseException("code", "\"" + text + "\" is not a valid course code. "
                    + "Use 2-4 letters followed by 3 digits, e.g. COS 201.");
        }
        return match.group(1) + " " + match.group(2);
    }

    /** Return the title with extra spaces removed, in title case. */
    public static String cleanTitle(String raw) throws InvalidCourseException {
        String text = (raw == null) ? "" : raw.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            throw new InvalidCourseException("title", "Please enter the course title.");
        }
        if (text.length() > MAX_TITLE_LENGTH) {
            throw new InvalidCourseException("title", "The title must be at most " + MAX_TITLE_LENGTH
                    + " characters (yours has " + text.length() + ").");
        }
        if (!containsLetter(text)) {
            throw new InvalidCourseException("title", "The title must contain letters.");
        }
        return toTitleCase(text);
    }

    /** Return the units as a whole number from MIN_UNITS to MAX_UNITS. */
    public static int cleanUnits(String raw) throws InvalidCourseException {
        String text = (raw == null) ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new InvalidCourseException("units", "Please enter the number of credit units.");
        }

        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new InvalidCourseException("units", "\"" + text + "\" is not a whole number. "
                    + "Units must be from " + MIN_UNITS + " to " + MAX_UNITS + ".");
        }

        if (value < MIN_UNITS || value > MAX_UNITS) {
            throw new InvalidCourseException("units", "Units must be from " + MIN_UNITS + " to "
                    + MAX_UNITS + " (you entered " + value + ").");
        }
        return value;
    }

    /**
     * Put a title in title case: "intro TO  programming ii" becomes
     * "Intro to Programming II". A short word (2-4 letters) typed fully in
     * capitals inside a mixed-case title, such as "ICT", is kept as an acronym.
     */
    static String toTitleCase(String text) {
        String[] words = text.split(" ");
        boolean keepAcronyms = !text.equals(text.toUpperCase(Locale.ROOT));
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String lower = word.toLowerCase(Locale.ROOT);
            String fixed;

            if (ROMAN_NUMERALS.contains(lower)) {
                fixed = lower.toUpperCase(Locale.ROOT);
            } else if (keepAcronyms && isAcronym(word)) {
                fixed = word;
            } else if (i > 0 && SMALL_WORDS.contains(lower)) {
                fixed = lower;
            } else {
                fixed = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }

            if (i > 0) {
                result.append(' ');
            }
            result.append(fixed);
        }
        return result.toString();
    }

    private static boolean isAcronym(String word) {
        return word.length() >= 2 && word.length() <= 4
                && containsLetter(word)
                && word.equals(word.toUpperCase(Locale.ROOT));
    }

    private static boolean containsLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Saving to and reading from a line of a CSV file
    // ------------------------------------------------------------------

    /** Return the course as one line of a CSV file: code,title,units */
    public String toCsvLine() {
        return csvField(code) + "," + csvField(title) + "," + units;
    }

    /** Create a course from one line of a CSV file. */
    public static Course fromCsvLine(String line) throws InvalidCourseException {
        List<String> fields = splitCsvLine(line);
        if (fields.size() != 3) {
            throw new InvalidCourseException("line",
                    "expected 3 values (code, title, units) but found " + fields.size());
        }
        return new Course(fields.get(0), fields.get(1), fields.get(2));
    }

    /** Put a value in quotes if it contains a comma or a quote mark. */
    private static String csvField(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Split a CSV line into its values. Commas inside quotes do not split. */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (insideQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');        // "" inside quotes is one quote mark
                    i++;
                } else if (c == '"') {
                    insideQuotes = false;       // closing quote
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                insideQuotes = true;            // opening quote
            } else if (c == ',') {
                fields.add(current.toString()); // end of a value
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    @Override
    public String toString() {
        return code + " - " + title + " (" + units + (units == 1 ? " unit)" : " units)");
    }
}
