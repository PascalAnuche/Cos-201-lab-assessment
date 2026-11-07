import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Student Course Management System - COS 201 Lab Assessment.
 *
 * Starts the program. The program runs a small web server on this computer
 * and opens its pages in the browser. The pages are HTML and CSS; all the
 * course logic is in the Java classes.
 *
 * Run from the project folder:  java -cp out Main
 * Add --no-browser to stop it opening the browser by itself.
 */
public class Main {

    public static void main(String[] args) {
        boolean openBrowser = true;
        for (String arg : args) {
            if (arg.equals("--no-browser")) {
                openBrowser = false;
            }
        }

        Path webFolder = Paths.get("web");
        if (!Files.isRegularFile(webFolder.resolve("layout.html"))) {
            System.err.println("Cannot find the web folder. Run the program from the project folder "
                    + "(the folder that contains src and web).");
            System.exit(1);
        }

        CourseManager manager = new CourseManager();
        DataFolder dataFolder = new DataFolder(Paths.get("data"));
        CourseWebServer server = new CourseWebServer(manager, dataFolder, new TemplateEngine(webFolder));

        int port;
        try {
            port = server.start();
        } catch (IOException e) {
            System.err.println("Could not start the program: " + e.getMessage());
            System.exit(1);
            return;
        }

        String address = "http://localhost:" + port + "/";
        System.out.println("==================================================");
        System.out.println("  Student Course Management System is running");
        System.out.println("==================================================");
        System.out.println("  Open this address in your browser: " + address);
        System.out.println("  Saved files are kept in: " + dataFolder.getDirectory());
        System.out.println("  To stop: choose 'Exit Program' in the menu, or press Ctrl+C here.");

        if (openBrowser) {
            openInBrowser(address);
        }
    }

    private static void openInBrowser(String address) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(address));
            }
        } catch (Exception e) {
            // The browser could not be opened automatically. The address is printed above.
            System.out.println("  (Could not open the browser by itself - please open the address above.)");
        }
    }
}
