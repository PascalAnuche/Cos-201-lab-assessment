import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores the student's courses and performs the operations on them:
 * adding, searching, totalling units, saving and loading.
 */
public class CourseManager {

    public static final String CSV_HEADER = "code,title,units";

    private final List<Course> courses = new ArrayList<>();
    private boolean unsavedChanges = false;
    private Path currentFile = null;   // the file last saved to or loaded from

    // ------------------------------------------------------------------
    // Adding and reading courses
    // ------------------------------------------------------------------

    /** Add a course. Throws DuplicateCourseException if its code is already listed. */
    public void addCourse(Course course) throws DuplicateCourseException {
        if (searchFrom(course.getCode(), 0) != null) {
            throw new DuplicateCourseException(course.getCode() + " is already in your list.");
        }
        courses.add(course);
        unsavedChanges = true;
    }

    /** Return the courses in the order they were added. The list cannot be changed. */
    public List<Course> getAllCourses() {
        return Collections.unmodifiableList(courses);
    }

    public int getCourseCount() {
        return courses.size();
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    // ------------------------------------------------------------------
    // Recursive search
    // ------------------------------------------------------------------

    /**
     * Find a course by its code. The code is cleaned first, so "cos201"
     * finds "COS 201". Returns null if there is no such course.
     */
    public Course findByCode(String code) throws InvalidCourseException {
        return searchFrom(Course.cleanCode(code), 0);
    }

    /** Recursively look for a course with this code, starting at position index. */
    private Course searchFrom(String code, int index) {
        if (index >= courses.size()) {
            return null;                          // base case: reached the end, not found
        }
        Course current = courses.get(index);
        if (current.getCode().equals(code)) {
            return current;                       // base case: found it
        }
        return searchFrom(code, index + 1);       // recursive case: check the rest
    }

    // ------------------------------------------------------------------
    // Recursive total
    // ------------------------------------------------------------------

    /** Return the total credit units of all the courses. */
    public int getTotalUnits() {
        return sumUnitsFrom(0);
    }

    /** Recursively add up the units of the courses from position index to the end. */
    private int sumUnitsFrom(int index) {
        if (index >= courses.size()) {
            return 0;                                              // base case: no courses left
        }
        return courses.get(index).getUnits() + sumUnitsFrom(index + 1);  // this one + the rest
    }

    /**
     * The total units for each department, largest first.
     * Example: COS 201 (3), COS 203 (3), MTH 201 (3) gives {COS=6, MTH=3}.
     */
    public Map<String, Integer> getUnitsByDepartment() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Course course : courses) {
            String department = course.getDepartment();
            totals.put(department, totals.getOrDefault(department, 0) + course.getUnits());
        }

        // Sort so the department with the most units comes first.
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(totals.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    /** Write all the courses to a CSV file. */
    public void saveToFile(Path file) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (Course course : courses) {
                writer.write(course.toCsvLine());
                writer.newLine();
            }
        }

        unsavedChanges = false;
        currentFile = file.toAbsolutePath().normalize();
    }

    /**
     * Replace the current list with the courses stored in a CSV file.
     * Lines that are not valid are skipped and reported in the result.
     *
     * The current list is only replaced after the whole file has been read,
     * so a failed load never loses the courses already in memory.
     *
     * @throws IOException     if the file cannot be opened or read
     * @throws CourseException if the file is not a course file
     */
    public LoadResult loadFromFile(Path file) throws IOException, CourseException {
        String name = file.getFileName().toString();
        List<Course> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> codesSeen = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("﻿")) {
                    line = line.substring(1);     // remove the mark Excel adds to UTF-8 files
                }
                if (line.trim().isEmpty()) {
                    continue;                     // ignore blank lines
                }
                if (lineNumber == 1 && line.replaceAll("\\s", "").equalsIgnoreCase(CSV_HEADER)) {
                    continue;                     // ignore the header line
                }

                try {
                    Course course = Course.fromCsvLine(line);
                    if (!codesSeen.add(course.getCode())) {
                        throw new DuplicateCourseException(course.getCode() + " appears more than once");
                    }
                    loaded.add(course);
                } catch (CourseException e) {
                    skipped.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        } catch (CharacterCodingException e) {
            throw new CourseException("\"" + name + "\" is not a text file, so it cannot be loaded.");
        }

        if (loaded.isEmpty() && !skipped.isEmpty()) {
            throw new CourseException("\"" + name + "\" has no valid courses, so your list was not changed. "
                    + "First problem - " + skipped.get(0));
        }

        courses.clear();
        courses.addAll(loaded);
        unsavedChanges = false;
        currentFile = file.toAbsolutePath().normalize();
        return new LoadResult(loaded.size(), skipped);
    }

    /** What happened when a file was loaded. */
    public static class LoadResult {

        private final int loadedCount;
        private final List<String> skippedLines;

        public LoadResult(int loadedCount, List<String> skippedLines) {
            this.loadedCount = loadedCount;
            this.skippedLines = skippedLines;
        }

        public int getLoadedCount() {
            return loadedCount;
        }

        public List<String> getSkippedLines() {
            return skippedLines;
        }
    }
}
