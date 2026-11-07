/**
 * Thrown when a course code, title or unit value is not valid.
 * It remembers which field was wrong, so the page can show the message
 * next to that field.
 */
public class InvalidCourseException extends CourseException {

    private final String field;

    public InvalidCourseException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The field that was wrong: "code", "title", "units" or "line". */
    public String getField() {
        return field;
    }
}
