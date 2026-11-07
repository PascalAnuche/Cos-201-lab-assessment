import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The folder where course lists are saved. File names typed by the user
 * are checked here, so the program can only read and write files inside
 * this one folder.
 */
public class DataFolder {

    public static final String DEFAULT_FILE_NAME = "courses.csv";
    private static final int MAX_NAME_LENGTH = 50;

    /** Letters, digits, spaces, - and _, with an optional .csv or .txt ending. */
    private static final Pattern SAFE_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _-]*(\\.(?i:csv|txt))?");

    private final Path directory;

    public DataFolder(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path getDirectory() {
        return directory;
    }

    /**
     * Tidy a file name typed by the user. An empty name gives the default,
     * surrounding quotes are removed, and ".csv" is added if there is no ending.
     * Example: "  my courses " becomes "my courses.csv".
     */
    public static String cleanFileName(String raw) throws CourseException {
        String name = (raw == null) ? "" : raw.trim();
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        if (name.isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new CourseException("The file name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new CourseException("\"" + name + "\" is not an allowed file name. Use letters, numbers, "
                    + "spaces, - and _ only, for example my-courses.csv.");
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
            name = name + ".csv";
        }
        return name;
    }

    /** The full path of a file in this folder. Pass a name from cleanFileName. */
    public Path resolve(String cleanName) {
        return directory.resolve(cleanName);
    }

    /** The .csv and .txt files in this folder, sorted by name. */
    public List<Path> listCourseFiles() {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{csv,txt,CSV,TXT}")) {
            for (Path file : stream) {
                // Only list files the user is allowed to open by name.
                if (Files.isRegularFile(file) && SAFE_NAME.matcher(file.getFileName().toString()).matches()) {
                    files.add(file);
                }
            }
        } catch (IOException e) {
            // If the folder cannot be read, show an empty list instead of failing.
            return files;
        }

        files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return files;
    }
}
