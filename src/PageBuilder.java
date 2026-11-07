import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds the HTML for every screen of the program, using the templates in
 * the web folder and the current course data.
 */
public class PageBuilder {

    /** The seven operations from the brief: view name, label and Heroicons icon. */
    private static final String[][] OPERATIONS = {
        {"add",    "Add Course",            "plus"},
        {"list",   "View All Courses",      "book-open"},
        {"search", "Search Course by Code", "magnifying-glass"},
        {"total",  "Compute Total Units",   "calculator"},
        {"save",   "Save to File",          "arrow-down-tray"},
        {"load",   "Load from File",        "folder-open"},
        {"exit",   "Exit Program",          "arrow-right-start-on-rectangle"},
    };

    /** Number of blue shades used to tell departments apart in the composition bar. */
    private static final int DEPARTMENT_SHADES = 6;

    /** Templates mark where messages go with this comment; otherwise they go first. */
    private static final String MESSAGE_SLOT = "<!-- message -->";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final CourseManager manager;
    private final DataFolder dataFolder;
    private final TemplateEngine templates;

    public PageBuilder(CourseManager manager, DataFolder dataFolder, TemplateEngine templates) {
        this.manager = manager;
        this.dataFolder = dataFolder;
        this.templates = templates;
    }

    // ------------------------------------------------------------------
    // The page frame: sidebar with menu and file status, and the main area
    // ------------------------------------------------------------------

    public String layout(String activeView, String pageTitle, String message, String content) throws IOException {
        // Put the message under the page heading when the page marks a place for it.
        String page = content.contains(MESSAGE_SLOT) ? content.replace(MESSAGE_SLOT, message) : message + content;

        Map<String, String> values = new HashMap<>();
        values.put("pageTitle", escape(pageTitle));
        values.put("menu", buildMenu(activeView));
        values.put("fileStatus", buildFileStatus());
        values.put("content", page);
        return templates.render("layout.html", values);
    }

    private String buildMenu(String activeView) {
        StringBuilder html = new StringBuilder();
        html.append(menuLink("dashboard", "Dashboard", "squares-2x2", activeView));
        html.append("<p class=\"nav-heading\">Operations</p>\n");
        for (String[] operation : OPERATIONS) {
            html.append(menuLink(operation[0], operation[1], operation[2], activeView));
        }
        return html.toString();
    }

    private static String menuLink(String view, String label, String iconName, String activeView) {
        boolean active = view.equals(activeView);
        String href = view.equals("dashboard") ? "/" : "/?view=" + view;
        return "<a class=\"nav-item" + (active ? " active" : "") + "\" href=\"" + href + "\""
                + (active ? " aria-current=\"page\"" : "") + ">"
                + icon(iconName)
                + "<span class=\"nav-label\">" + label + "</span>"
                + "</a>\n";
    }

    /** The box at the bottom of the sidebar: saved or not, and the running totals. */
    private String buildFileStatus() {
        int count = manager.getCourseCount();
        int total = manager.getTotalUnits();
        Path file = manager.getCurrentFile();

        String state;
        String label;
        if (manager.hasUnsavedChanges()) {
            state = "unsaved";
            label = "Unsaved changes";
        } else if (file != null) {
            state = "saved";
            label = "Saved in " + file.getFileName();
        } else {
            state = "empty";
            label = "Nothing recorded yet";
        }

        return "<div class=\"file-status is-" + state + "\">"
                + "<p class=\"file-status-label\"><span class=\"dot\"></span>" + escape(label) + "</p>"
                + "<p class=\"file-status-meta\">" + count + " " + plural(count, "course")
                + " &middot; " + total + " " + plural(total, "unit") + "</p></div>";
    }

    // ------------------------------------------------------------------
    // Dashboard
    // ------------------------------------------------------------------

    public String dashboardPanel() throws IOException {
        int count = manager.getCourseCount();
        int total = manager.getTotalUnits();
        Map<String, Integer> departments = manager.getUnitsByDepartment();

        Map<String, String> values = countValues();
        values.put("departmentCount", String.valueOf(departments.size()));
        values.put("departmentList", departments.isEmpty() ? "none yet" : escape(String.join(", ", departments.keySet())));
        values.put("levelSummary", levelSummary());
        values.put("average", average(count, total));
        values.put("composition", buildComposition(departments, total));
        values.put("courseTable", count == 0
                ? emptyNote("No courses yet.", "/?view=add", "Add your first course")
                : buildCourseTable(false));
        values.put("fileFacts", buildFileFacts());
        return templates.render("dashboard.html", values);
    }

    /**
     * The signature element: one bar split by department, with a legend.
     * Each segment's width is its share of the total units.
     */
    private String buildComposition(Map<String, Integer> departments, int total) {
        if (departments.isEmpty()) {
            return "<p class=\"empty-line\">Add a course to see how your units split across departments.</p>";
        }

        StringBuilder bar = new StringBuilder();
        StringBuilder legend = new StringBuilder();
        StringBuilder label = new StringBuilder();
        int shade = 1;

        for (Map.Entry<String, Integer> entry : departments.entrySet()) {
            String department = escape(entry.getKey());
            int units = entry.getValue();
            int percent = Math.round(units * 100f / total);

            bar.append("<span class=\"shade-").append(shade).append("\" style=\"flex-grow:")
               .append(units).append("\"></span>");
            legend.append("<li><span class=\"swatch shade-").append(shade).append("\"></span>")
                  .append("<span class=\"legend-dept\">").append(department).append("</span>")
                  .append("<span class=\"legend-units\">").append(units).append(" ").append(plural(units, "unit"))
                  .append("</span><span class=\"legend-share\">").append(percent).append("%</span></li>");
            label.append(label.length() == 0 ? "" : ", ").append(department).append(" ")
                 .append(units).append(" ").append(plural(units, "unit"));

            shade = (shade % DEPARTMENT_SHADES) + 1;   // 1, 2, ... 6, then back to 1
        }

        return "<div class=\"composition-bar\" role=\"img\" aria-label=\"Units by department: " + label + "\">"
                + bar + "</div><ul class=\"composition-legend\">" + legend + "</ul>";
    }

    private String buildFileFacts() {
        Path file = manager.getCurrentFile();
        String status;
        if (manager.hasUnsavedChanges()) {
            status = "<span class=\"tag tag-pending\">Unsaved changes</span>";
        } else if (file != null) {
            status = "<span class=\"tag tag-approved\">Up to date</span>";
        } else {
            status = "<span class=\"tag\">Not saved</span>";
        }

        return "<dl class=\"facts\">"
                + "<div><dt>File</dt><dd>" + (file == null ? "None yet" : escape(file.getFileName().toString())) + "</dd></div>"
                + "<div><dt>Status</dt><dd>" + status + "</dd></div>"
                + "<div><dt>Folder</dt><dd><span class=\"code\" title=\""
                + escape(dataFolder.getDirectory().toString()) + "\">"
                + escape(dataFolder.getDirectory().getFileName().toString()) + "</span></dd></div>"
                + "</dl>";
    }

    // ------------------------------------------------------------------
    // 1. Add Course
    // ------------------------------------------------------------------

    /** The add form. The values and errors are shown again after a failed attempt. */
    public String addPanel(String code, String title, String units, Map<String, String> errors) throws IOException {
        Map<String, String> values = countValues();
        values.put("minUnits", String.valueOf(Course.MIN_UNITS));
        values.put("maxUnits", String.valueOf(Course.MAX_UNITS));
        values.put("maxTitle", String.valueOf(Course.MAX_TITLE_LENGTH));
        values.put("miniList", buildMiniList());
        putField(values, "code", code, errors.get("code"));
        putField(values, "title", title, errors.get("title"));
        putField(values, "units", units, errors.get("units"));

        // Put the cursor in the first field that has a problem, or in the first field.
        String focus = "code";
        if (!errors.containsKey("code") && errors.containsKey("title")) {
            focus = "title";
        } else if (!errors.containsKey("code") && !errors.containsKey("title") && errors.containsKey("units")) {
            focus = "units";
        }
        values.put(focus + "Focus", "autofocus");

        return templates.render("add.html", values);
    }

    private void putField(Map<String, String> values, String name, String value, String error) {
        values.put(name + "Value", escape(value));
        if (error != null) {
            values.put(name + "State", "has-error");
            values.put(name + "Invalid", "aria-invalid=\"true\"");
            values.put(name + "Error", "<p class=\"field-error\" id=\"" + name + "-error\">"
                    + icon("exclamation-triangle") + "<span>" + escape(error) + "</span></p>");
        }
    }

    /** A short list of the courses already recorded, shown beside the add form. */
    private String buildMiniList() {
        List<Course> courses = manager.getAllCourses();
        if (courses.isEmpty()) {
            return "<p class=\"empty-line\">Nothing added yet. Courses appear here as you add them.</p>";
        }

        StringBuilder html = new StringBuilder("<ul class=\"mini-list\">");
        for (Course course : courses) {
            html.append("<li>").append(codeChip(course))
                .append("<span class=\"mini-title\">").append(escape(course.getTitle())).append("</span>")
                .append("<span class=\"mini-units\">").append(course.getUnits()).append("</span></li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    // ------------------------------------------------------------------
    // 2. View All Courses
    // ------------------------------------------------------------------

    public String listPanel() throws IOException {
        if (manager.getCourseCount() == 0) {
            return templates.render("list-empty.html", new HashMap<>());
        }
        Map<String, String> values = countValues();
        values.put("table", buildCourseTable(true));
        return templates.render("list.html", values);
    }

    /** The course table. The full version adds row numbers and a total row. */
    private String buildCourseTable(boolean full) {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table>\n<thead><tr>");
        if (full) {
            html.append("<th class=\"col-num\" scope=\"col\">No.</th>");
        }
        html.append("<th scope=\"col\">Code</th><th scope=\"col\">Title</th>")
            .append("<th class=\"col-level\" scope=\"col\">Level</th>")
            .append("<th class=\"col-units\" scope=\"col\">Units</th></tr></thead>\n<tbody>\n");

        int number = 1;
        for (Course course : manager.getAllCourses()) {
            html.append("<tr>");
            if (full) {
                html.append("<td class=\"col-num\">").append(number).append("</td>");
            }
            html.append("<td>").append(codeChip(course)).append("</td>")
                .append("<td>").append(escape(course.getTitle())).append("</td>")
                .append("<td class=\"col-level\">").append(levelText(course)).append("</td>")
                .append("<td class=\"col-units\">").append(course.getUnits()).append("</td>")
                .append("</tr>\n");
            number++;
        }
        html.append("</tbody>\n");

        if (full) {
            html.append("<tfoot><tr><td colspan=\"4\">Total units</td><td class=\"col-units\">")
                .append(manager.getTotalUnits()).append("</td></tr></tfoot>\n");
        }
        html.append("</table></div>");
        return html.toString();
    }

    // ------------------------------------------------------------------
    // 3. Search Course by Code
    // ------------------------------------------------------------------

    /** The search form. rawCode is null when the user has not searched yet. */
    public String searchPanel(String rawCode) throws IOException {
        Map<String, String> values = new HashMap<>();
        values.put("searchValue", escape(rawCode == null ? "" : rawCode));
        values.put("result", rawCode == null
                ? "<p class=\"empty-line\">Results appear here.</p>"
                : searchResult(rawCode.trim()));
        return templates.render("search.html", values);
    }

    private String searchResult(String text) {
        if (text.isEmpty()) {
            return alert("info", "Type a course code, then press Search.");
        }

        String code;
        Course course;
        try {
            code = Course.cleanCode(text);
            course = manager.findByCode(code);   // recursive search
        } catch (InvalidCourseException e) {
            return alert("error", e.getMessage());
        }

        if (course == null) {
            return "<div class=\"result\">" + icon("x-circle", "result-icon")
                    + "<div><p class=\"result-title\">" + escape(code) + " is not in your list</p>"
                    + "<p class=\"result-text\">Check the code, or add the course with Add Course.</p></div></div>";
        }

        return "<div class=\"result result-found\">" + icon("check-circle", "result-icon")
                + "<div class=\"result-body\"><p class=\"result-title\">Found " + escape(course.getCode()) + "</p>"
                + "<dl class=\"facts\">"
                + "<div><dt>Title</dt><dd>" + escape(course.getTitle()) + "</dd></div>"
                + "<div><dt>Department</dt><dd>" + escape(course.getDepartment()) + "</dd></div>"
                + "<div><dt>Level</dt><dd>" + levelText(course) + "</dd></div>"
                + "<div><dt>Units</dt><dd>" + course.getUnits() + "</dd></div>"
                + "</dl></div></div>";
    }

    // ------------------------------------------------------------------
    // 4. Compute Total Units
    // ------------------------------------------------------------------

    public String totalPanel() throws IOException {
        int count = manager.getCourseCount();
        int total = manager.getTotalUnits();   // recursive total
        Map<String, Integer> departments = manager.getUnitsByDepartment();

        Map<String, String> values = countValues();
        values.put("average", average(count, total));
        values.put("departmentCount", String.valueOf(departments.size()));
        values.put("departmentList", departments.isEmpty() ? "none yet" : escape(String.join(", ", departments.keySet())));
        values.put("maxUnits", String.valueOf(Course.MAX_UNITS));
        values.put("composition", buildComposition(departments, total));

        if (count == 0) {
            values.put("summary", "No courses recorded yet, so the total is 0 units.");
            values.put("breakdown", "<p class=\"empty-line\">Add a course to see its units here.</p>");
        } else {
            values.put("summary", count + " " + plural(count, "course") + " worth " + total + " "
                    + plural(total, "unit") + " in total.");
            values.put("breakdown", buildBreakdown());
        }
        return templates.render("total.html", values);
    }

    /** One row per course, with a bar showing its units out of the most a course can have. */
    private String buildBreakdown() {
        StringBuilder html = new StringBuilder("<ul class=\"breakdown\">\n");
        for (Course course : manager.getAllCourses()) {
            int percent = Math.round(course.getUnits() * 100f / Course.MAX_UNITS);
            html.append("<li>")
                .append(codeChip(course))
                .append("<span class=\"breakdown-title\">").append(escape(course.getTitle())).append("</span>")
                .append("<span class=\"meter\" aria-hidden=\"true\"><span style=\"width:").append(percent)
                .append("%\"></span></span>")
                .append("<span class=\"breakdown-units\">").append(course.getUnits()).append("</span>")
                .append("</li>\n");
        }
        html.append("</ul>");
        return html.toString();
    }

    // ------------------------------------------------------------------
    // 5. Save to File and 6. Load from File
    // ------------------------------------------------------------------

    /** The file name to suggest: the file last used, or the default name. */
    public String defaultFileName() {
        Path file = manager.getCurrentFile();
        return (file == null) ? DataFolder.DEFAULT_FILE_NAME : file.getFileName().toString();
    }

    public String savePanel(String fileName) throws IOException {
        int count = manager.getCourseCount();
        Map<String, String> values = countValues();
        values.put("fileName", escape(fileName));
        values.put("folder", escape(dataFolder.getDirectory().toString()));
        values.put("fileList", buildFileList(false));
        values.put("notice", count == 0 ? alert("info", "Add at least one course before saving.") : "");
        values.put("disabled", count == 0 ? "disabled" : "");
        return templates.render("save.html", values);
    }

    public String loadPanel(String fileName) throws IOException {
        Map<String, String> values = new HashMap<>();
        values.put("fileName", escape(fileName));
        values.put("folder", escape(dataFolder.getDirectory().toString()));
        values.put("fileList", buildFileList(true));

        StringBuilder options = new StringBuilder();
        for (Path file : dataFolder.listCourseFiles()) {
            options.append("<option value=\"").append(escape(file.getFileName().toString())).append("\"></option>");
        }
        values.put("fileOptions", options.toString());

        values.put("notice", manager.hasUnsavedChanges()
                ? alert("warning", "You have unsaved changes. Loading a file replaces your current list, "
                        + "so save first if you want to keep it.")
                : "");
        return templates.render("load.html", values);
    }

    /** The saved files, each with its date and size, and optionally a Load button. */
    private String buildFileList(boolean withLoadButtons) {
        List<Path> files = dataFolder.listCourseFiles();
        if (files.isEmpty()) {
            return "<p class=\"empty-line\">No saved files yet.</p>";
        }

        StringBuilder html = new StringBuilder("<ul class=\"file-list\">\n");
        for (Path file : files) {
            String name = escape(file.getFileName().toString());
            boolean current = file.equals(manager.getCurrentFile());

            html.append("<li>").append(icon("document-text", "file-icon"))
                .append("<span class=\"file-info\"><span class=\"file-name\">").append(name)
                .append(current ? " <span class=\"tag tag-accent\">In use</span>" : "")
                .append("</span><span class=\"file-meta\">").append(escape(describeFile(file))).append("</span></span>");

            if (withLoadButtons) {
                html.append("<form method=\"post\" action=\"/load\">")
                    .append("<input type=\"hidden\" name=\"file\" value=\"").append(name).append("\">")
                    .append("<button class=\"button secondary small\" type=\"submit\" aria-label=\"Load ")
                    .append(name).append("\">Load</button></form>");
            }
            html.append("</li>\n");
        }
        html.append("</ul>");
        return html.toString();
    }

    private static String describeFile(Path file) {
        try {
            long bytes = Files.size(file);
            String modified = DATE_FORMAT.format(Files.getLastModifiedTime(file).toInstant());
            return modified + " · " + bytes + " " + plural((int) bytes, "byte");
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Confirmation, 7. Exit Program and the goodbye page
    // ------------------------------------------------------------------

    /**
     * A page that asks the user to confirm an action. The hidden values are
     * sent again with the form when the user confirms.
     */
    public String confirmPanel(String heading, String text, String action, Map<String, String> hidden,
                               String confirmLabel, String cancelHref) throws IOException {
        StringBuilder fields = new StringBuilder();
        for (Map.Entry<String, String> entry : hidden.entrySet()) {
            fields.append("<input type=\"hidden\" name=\"").append(escape(entry.getKey()))
                  .append("\" value=\"").append(escape(entry.getValue())).append("\">");
        }

        Map<String, String> values = new HashMap<>();
        values.put("heading", escape(heading));
        values.put("text", escape(text));
        values.put("action", escape(action));
        values.put("hiddenFields", fields.toString());
        values.put("confirmLabel", escape(confirmLabel));
        values.put("cancelHref", escape(cancelHref));
        return templates.render("confirm.html", values);
    }

    public String exitPanel() throws IOException {
        if (manager.hasUnsavedChanges()) {
            return templates.render("exit-unsaved.html", countValues());
        }

        Map<String, String> values = new HashMap<>();
        int count = manager.getCourseCount();
        Path file = manager.getCurrentFile();
        if (count == 0) {
            values.put("status", alert("info", "You have not recorded any courses, so there is nothing to save."));
        } else {
            values.put("status", alert("success", "Your " + count + " " + plural(count, "course")
                    + (count == 1 ? " is" : " are") + " saved in "
                    + (file == null ? "a file" : file.getFileName().toString()) + "."));
        }
        return templates.render("exit.html", values);
    }

    public String goodbyePage(boolean discardedChanges) throws IOException {
        Path file = manager.getCurrentFile();
        String summary;
        if (discardedChanges) {
            summary = "Changes you made since your last save were not kept.";
        } else if (file != null) {
            summary = "Your courses are saved in " + file.getFileName() + ".";
        } else {
            summary = "No courses were recorded.";
        }

        Map<String, String> values = new HashMap<>();
        values.put("summary", escape(summary));
        return templates.render("goodbye.html", values);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** Values most pages need: the course count, the total and the matching words. */
    private Map<String, String> countValues() {
        int count = manager.getCourseCount();
        int total = manager.getTotalUnits();
        Map<String, String> values = new HashMap<>();
        values.put("courseCount", String.valueOf(count));
        values.put("courseWord", plural(count, "course"));
        values.put("totalUnits", String.valueOf(total));
        values.put("unitWord", plural(total, "unit"));
        return values;
    }

    /** A course code with the department letters in bold, e.g. <b>COS</b> 201. */
    private static String codeChip(Course course) {
        return "<span class=\"code\"><span class=\"code-dept\">" + escape(course.getDepartment())
                + "</span> " + escape(course.getNumber()) + "</span>";
    }

    /** The levels in the list, e.g. "200 level" or "100, 200 level". */
    private String levelSummary() {
        TreeSet<Integer> levels = new TreeSet<>();   // keeps the levels sorted, without repeats
        for (Course course : manager.getAllCourses()) {
            if (course.getLevel() > 0) {
                levels.add(course.getLevel());
            }
        }
        if (levels.isEmpty()) {
            return "none yet";
        }

        StringBuilder text = new StringBuilder();
        for (int level : levels) {
            text.append(text.length() == 0 ? "" : ", ").append(level);
        }
        return text.append(" level").toString();
    }

    private static String levelText(Course course) {
        int level = course.getLevel();
        return level == 0 ? "&ndash;" : String.valueOf(level);
    }

    private static String average(int count, int total) {
        return count == 0 ? "0" : String.format(Locale.ROOT, "%.1f", (double) total / count);
    }

    /** A one-line empty state with a link to the next step. */
    private static String emptyNote(String text, String href, String linkText) {
        return "<div class=\"empty-block\"><p>" + escape(text) + "</p><a class=\"button primary small\" href=\""
                + href + "\">" + icon("plus") + escape(linkText) + "</a></div>";
    }

    /** A coloured message box. kind is "success", "error", "warning" or "info". */
    public static String alert(String kind, String text) {
        return alert(kind, text, null);
    }

    /** A message box with a list of details under the main text. */
    public static String alert(String kind, String text, List<String> details) {
        String iconName;
        switch (kind) {
            case "success": iconName = "check-circle";         break;
            case "error":   iconName = "x-circle";             break;
            case "warning": iconName = "exclamation-triangle"; break;
            default:        iconName = "information-circle";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"alert alert-").append(kind).append("\" role=\"")
            .append(kind.equals("error") ? "alert" : "status").append("\">")
            .append(icon(iconName))
            .append("<div><p>").append(escape(text)).append("</p>");
        if (details != null && !details.isEmpty()) {
            html.append("<ul>");
            for (String detail : details) {
                html.append("<li>").append(escape(detail)).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div></div>");
        return html.toString();
    }

    /** An icon from the Heroicons sprite in layout.html. */
    public static String icon(String name) {
        return icon(name, "");
    }

    public static String icon(String name, String extraClass) {
        String classes = extraClass.isEmpty() ? "icon" : "icon " + extraClass;
        return "<svg class=\"" + classes + "\" aria-hidden=\"true\"><use href=\"#i-" + name + "\"></use></svg>";
    }

    /** "1 course", "2 courses". */
    public static String plural(int count, String word) {
        return (count == 1) ? word : word + "s";
    }

    private static String escape(String text) {
        return TemplateEngine.escape(text);
    }
}
