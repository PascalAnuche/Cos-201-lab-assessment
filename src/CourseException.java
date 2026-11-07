/**
 * Base class for the problems this program reports about course data and
 * course files. Catching CourseException catches every kind of course error.
 */
public class CourseException extends Exception {

    public CourseException(String message) {
        super(message);
    }
}
