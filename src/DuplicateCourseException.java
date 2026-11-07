/**
 * Thrown when the student tries to add a course whose code is already
 * in the list.
 */
public class DuplicateCourseException extends CourseException {

    public DuplicateCourseException(String message) {
        super(message);
    }
}
