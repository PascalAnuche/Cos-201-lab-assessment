import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small web server that runs on this computer only. It sends the
 * program's HTML pages to the browser and carries out the menu option the
 * user picks. It uses the HTTP server that comes with the JDK, so no extra
 * libraries are needed.
 */
public class CourseWebServer {

    public static final int FIRST_PORT = 8080;
    private static final int PORTS_TO_TRY = 10;
    private static final int MAX_FORM_BYTES = 16 * 1024;

    private final CourseManager manager;
    private final DataFolder dataFolder;
    private final TemplateEngine templates;
    private final PageBuilder pages;

    private HttpServer server;
    private String flashMessage = "";   // a message to show once, on the next page

    public CourseWebServer(CourseManager manager, DataFolder dataFolder, TemplateEngine templates) {
        this.manager = manager;
        this.dataFolder = dataFolder;
        this.templates = templates;
        this.pages = new PageBuilder(manager, dataFolder, templates);
    }

    /** Start the server on the first free port from 8080 and return that port number. */
    public int start() throws IOException {
        BindException lastError = null;

        for (int port = FIRST_PORT; port < FIRST_PORT + PORTS_TO_TRY; port++) {
            try {
                // The loopback address means only this computer can open the pages.
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            } catch (BindException e) {
                lastError = e;   // another program is using this port: try the next one
                continue;
            }
            server.createContext("/", this::handle);
            server.start();
            return port;
        }

        throw new IOException("Ports " + FIRST_PORT + " to " + (FIRST_PORT + PORTS_TO_TRY - 1)
                + " are all in use. Close other programs and try again.", lastError);
    }

    // ------------------------------------------------------------------
    // Handling a request
    // ------------------------------------------------------------------

    private void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (Exception e) {
            // Something unexpected went wrong. Show an error page instead of stopping the program.
            System.err.println("Error while handling " + exchange.getRequestURI() + ": " + e);
            sendErrorPage(exchange, e);
        } finally {
            exchange.close();
        }
    }

    /** Decide what to do from the request's method (GET or POST) and path. */
    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equals("GET")) {
            switch (path) {
                case "/":
                    showView(exchange);
                    return;
                case "/style.css":
                    sendFile(exchange, "style.css", "text/css; charset=utf-8");
                    return;
                case "/favicon.svg":
                    sendFile(exchange, "favicon.svg", "image/svg+xml");
                    return;
                default:
                    break;
            }
        } else if (method.equals("POST")) {
            Map<String, String> form = readForm(exchange);
            switch (path) {
                case "/add":
                    handleAdd(exchange, form);
                    return;
                case "/save":
                    handleSave(exchange, form);
                    return;
                case "/load":
                    handleLoad(exchange, form);
                    return;
                case "/exit":
                    handleExit(exchange, form);
                    return;
                default:
                    break;
            }
        }

        String message = PageBuilder.alert("error", "That page does not exist. Please use the menu.");
        sendPage(exchange, 404, "", "Page not found", message, "");
    }

    /** Show the screen for the menu option in the address, e.g. /?view=search. */
    private void showView(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
        String view = query.getOrDefault("view", "dashboard").trim().toLowerCase(Locale.ROOT);
        String message = takeFlashMessage();
        String title;
        String content;

        switch (view) {
            case "dashboard":
                title = "Dashboard";
                content = pages.dashboardPanel();
                break;
            case "add":
                title = "Add Course";
                content = pages.addPanel("", "", "", new HashMap<>());
                break;
            case "list":
                title = "All Courses";
                content = pages.listPanel();
                break;
            case "search":
                title = "Search Course by Code";
                content = pages.searchPanel(query.get("code"));
                break;
            case "total":
                title = "Total Units";
                content = pages.totalPanel();
                break;
            case "save":
                title = "Save to File";
                content = pages.savePanel(pages.defaultFileName());
                break;
            case "load":
                title = "Load from File";
                content = pages.loadPanel(pages.defaultFileName());
                break;
            case "exit":
                title = "Exit Program";
                content = pages.exitPanel();
                break;
            default:
                // Not one of the menu options: say so, and show the dashboard.
                message = PageBuilder.alert("error", "\"" + view + "\" is not a menu option. "
                        + "Please choose one of the options in the menu.") + message;
                view = "dashboard";
                title = "Dashboard";
                content = pages.dashboardPanel();
        }
        sendPage(exchange, 200, view, title, message, content);
    }

    // ------------------------------------------------------------------
    // 1. Add Course
    // ------------------------------------------------------------------

    private void handleAdd(HttpExchange exchange, Map<String, String> form) throws IOException {
        String code = form.getOrDefault("code", "");
        String title = form.getOrDefault("title", "");
        String units = form.getOrDefault("units", "");
        Map<String, String> errors = new HashMap<>();

        // Check every field, so the user sees all the problems at once.
        try {
            String cleanCode = Course.cleanCode(code);
            if (manager.findByCode(cleanCode) != null) {
                errors.put("code", cleanCode + " is already in your list.");
            }
        } catch (InvalidCourseException e) {
            errors.put("code", e.getMessage());
        }
        try {
            Course.cleanTitle(title);
        } catch (InvalidCourseException e) {
            errors.put("title", e.getMessage());
        }
        try {
            Course.cleanUnits(units);
        } catch (InvalidCourseException e) {
            errors.put("units", e.getMessage());
        }

        if (errors.isEmpty()) {
            try {
                Course course = new Course(code, title, units);
                manager.addCourse(course);
                flashMessage = PageBuilder.alert("success", "Added " + course + ".");
                redirect(exchange, "/?view=add");
                return;
            } catch (CourseException e) {
                errors.put("code", e.getMessage());
            }
        }

        String message = PageBuilder.alert("error", errors.size() == 1
                ? "The course was not added. Please fix the problem below."
                : "The course was not added. Please fix the " + errors.size() + " problems below.");
        sendPage(exchange, 200, "add", "Add Course", message, pages.addPanel(code, title, units, errors));
    }

    // ------------------------------------------------------------------
    // 5. Save to File
    // ------------------------------------------------------------------

    private void handleSave(HttpExchange exchange, Map<String, String> form) throws IOException {
        String rawName = form.getOrDefault("file", "");
        int count = manager.getCourseCount();

        if (count == 0) {
            flashMessage = PageBuilder.alert("error", "There are no courses to save yet. Add a course first.");
            redirect(exchange, "/?view=save");
            return;
        }

        String fileName;
        try {
            fileName = DataFolder.cleanFileName(rawName);
        } catch (CourseException e) {
            sendPage(exchange, 200, "save", "Save to File", PageBuilder.alert("error", e.getMessage()),
                    pages.savePanel(rawName));
            return;
        }

        // Ask before replacing a file that this list did not come from.
        Path file = dataFolder.resolve(fileName);
        boolean replacing = Files.exists(file) && !file.equals(manager.getCurrentFile());
        if (replacing && !"yes".equals(form.get("replace"))) {
            Map<String, String> hidden = new LinkedHashMap<>();
            hidden.put("file", fileName);
            hidden.put("replace", "yes");
            String content = pages.confirmPanel("Replace " + fileName + "?",
                    "A file called " + fileName + " is already in the data folder. Saving will replace "
                            + "what is in it with your current " + count + " " + PageBuilder.plural(count, "course") + ".",
                    "/save", hidden, "Replace file", "/?view=save");
            sendPage(exchange, 200, "save", "Replace file?", "", content);
            return;
        }

        try {
            manager.saveToFile(file);
        } catch (IOException e) {
            String message = PageBuilder.alert("error", "Could not save to " + fileName + ": " + describe(e));
            sendPage(exchange, 200, "save", "Save to File", message, pages.savePanel(fileName));
            return;
        }

        flashMessage = PageBuilder.alert("success", "Saved " + count + " " + PageBuilder.plural(count, "course")
                + " to " + file + ".");
        redirect(exchange, "/?view=save");
    }

    // ------------------------------------------------------------------
    // 6. Load from File
    // ------------------------------------------------------------------

    private void handleLoad(HttpExchange exchange, Map<String, String> form) throws IOException {
        String rawName = form.getOrDefault("file", "");

        String fileName;
        try {
            fileName = DataFolder.cleanFileName(rawName);
        } catch (CourseException e) {
            sendPage(exchange, 200, "load", "Load from File", PageBuilder.alert("error", e.getMessage()),
                    pages.loadPanel(rawName));
            return;
        }

        Path file = dataFolder.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            String message = PageBuilder.alert("error", "There is no file called " + fileName
                    + " in the data folder. Pick one of the saved files below, or save your courses first with Save to File.");
            sendPage(exchange, 200, "load", "Load from File", message, pages.loadPanel(fileName));
            return;
        }

        // Loading replaces the current list, so ask first if that would lose work.
        if (manager.hasUnsavedChanges() && !"yes".equals(form.get("confirm"))) {
            int count = manager.getCourseCount();
            Map<String, String> hidden = new LinkedHashMap<>();
            hidden.put("file", fileName);
            hidden.put("confirm", "yes");
            String content = pages.confirmPanel("Replace your current list?",
                    "You have unsaved changes to your " + count + " " + PageBuilder.plural(count, "course")
                            + ". Loading " + fileName + " will replace them, and the changes will be lost.",
                    "/load", hidden, "Load and replace", "/?view=load");
            sendPage(exchange, 200, "load", "Replace your list?", "", content);
            return;
        }

        CourseManager.LoadResult result;
        try {
            result = manager.loadFromFile(file);
        } catch (CourseException e) {
            sendPage(exchange, 200, "load", "Load from File", PageBuilder.alert("error", e.getMessage()),
                    pages.loadPanel(fileName));
            return;
        } catch (IOException e) {
            String message = PageBuilder.alert("error", "Could not read " + fileName + ": " + describe(e));
            sendPage(exchange, 200, "load", "Load from File", message, pages.loadPanel(fileName));
            return;
        }

        int loaded = result.getLoadedCount();
        List<String> skipped = result.getSkippedLines();
        String text = "Loaded " + loaded + " " + PageBuilder.plural(loaded, "course") + " from " + fileName + ".";
        if (skipped.isEmpty()) {
            flashMessage = PageBuilder.alert("success", text);
        } else {
            flashMessage = PageBuilder.alert("warning", text + " " + skipped.size() + " "
                    + PageBuilder.plural(skipped.size(), "line") + " skipped because the data was not valid:", skipped);
        }
        redirect(exchange, "/");
    }

    // ------------------------------------------------------------------
    // 7. Exit Program
    // ------------------------------------------------------------------

    private void handleExit(HttpExchange exchange, Map<String, String> form) throws IOException {
        boolean discard = "discard".equals(form.get("action"));
        if (manager.hasUnsavedChanges() && !discard) {
            redirect(exchange, "/?view=exit");   // show the unsaved-changes warning first
            return;
        }

        sendHtml(exchange, 200, pages.goodbyePage(discard));

        // Stop the server in another thread, so this page is sent completely first.
        Thread stopper = new Thread(() -> {
            server.stop(1);
            System.out.println("The program has stopped. Goodbye!");
        });
        stopper.start();
    }

    // ------------------------------------------------------------------
    // Reading requests and sending responses
    // ------------------------------------------------------------------

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES);
        return parseForm(new String(body, StandardCharsets.UTF_8));
    }

    /** Split "code=COS+201&units=3" into its names and values. */
    private static Map<String, String> parseForm(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }

        for (String pair : encoded.split("&")) {
            int equals = pair.indexOf('=');
            String name = (equals >= 0) ? pair.substring(0, equals) : pair;
            String value = (equals >= 0) ? pair.substring(equals + 1) : "";
            try {
                values.put(URLDecoder.decode(name, StandardCharsets.UTF_8),
                           URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                // A badly encoded pair (for example a stray "%"): ignore it.
            }
        }
        return values;
    }

    private String takeFlashMessage() {
        String message = flashMessage;
        flashMessage = "";
        return message;
    }

    private void sendPage(HttpExchange exchange, int status, String view, String title,
                          String message, String content) throws IOException {
        sendHtml(exchange, status, pages.layout(view, title, message, content));
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendFile(HttpExchange exchange, String fileName, String contentType) throws IOException {
        byte[] bytes = templates.readFile(fileName);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Send the browser to another address after a form is handled (Post/Redirect/Get). */
    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private void sendErrorPage(HttpExchange exchange, Exception error) {
        String text = "Something went wrong: " + error.getMessage();
        try {
            sendPage(exchange, 500, "", "Error", PageBuilder.alert("error", text), "");
        } catch (Exception pageError) {
            try {
                sendHtml(exchange, 500, "<!DOCTYPE html><title>Error</title><p>" + TemplateEngine.escape(text) + "</p>");
            } catch (IOException ignored) {
                // The response was already started, so nothing more can be sent.
            }
        }
    }

    /** A short, readable reason for a file error. */
    private static String describe(IOException e) {
        if (e instanceof AccessDeniedException) {
            return "access was denied. The file may be read-only, or open in another program such as Excel.";
        }
        if (e instanceof NoSuchFileException) {
            return "the file or folder does not exist.";
        }
        if (e instanceof FileSystemException && ((FileSystemException) e).getReason() != null) {
            return ((FileSystemException) e).getReason();
        }
        return e.getMessage();
    }
}
