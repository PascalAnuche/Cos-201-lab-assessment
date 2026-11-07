# Student Course Management System

COS 201 Lab Assessment. A program that helps a student record and manage the courses they are taking for the semester.

The interface is written in **HTML and CSS**, and all the program logic is in **Java**. When the program starts, it runs a small web server on your computer and opens the pages in your browser. It uses no JavaScript and no extra libraries, only the Java Development Kit.

## How to run

You need a **Java Development Kit (JDK), version 11 or newer**. To check, type `javac -version` in a terminal. If Java is missing, install it from <https://adoptium.net>.

**Windows:** double-click `run.bat`.

**macOS / Linux:** run `sh run.sh`.

**By hand** (from this folder):

```
javac -encoding UTF-8 -d out src/*.java
java -cp out Main
```

The browser opens at `http://localhost:8080/`. If it does not open by itself, type that address into your browser. To stop the program, choose **Exit Program** in the menu.

## The dashboard and the seven operations

The program opens on a **Dashboard**. It shows the total credit units, the number of courses, the departments, the average units per course, and a bar that splits your units by department (COS, MTH, GST and so on). It also has the course table, a quick search box, and the save status of your file.

The sidebar lists the seven operations from the brief. They are numbered below as they are in the brief:

| Menu option | What it does |
|---|---|
| 1. Add Course | Adds a course with a code, a title and credit units. Each field is checked, and any problems are shown next to the field. |
| 2. View All Courses | Shows a table of all the courses and the total units. |
| 3. Search Course by Code | Finds a course by its code. `cos201`, `COS 201` and `Cos-201` all find the same course. |
| 4. Compute Total Units | Shows the total units, the number of courses, the average, and the units of each course. |
| 5. Save to File | Saves the list as a CSV file in the `data` folder. It asks before replacing another file. |
| 6. Load from File | Loads a saved list. Bad lines are skipped and listed. It warns before replacing unsaved work. |
| 7. Exit Program | Stops the program. It warns first if there are unsaved changes. |

`data/sample-courses.csv` is a ready-made list you can load to try the program.

## Project structure

```
src/                        Java source code
  Main.java                 Starts the program and opens the browser
  Course.java               One course: validation and string processing
  CourseManager.java        The course list: add, recursive search, recursive total, save, load
  CourseException.java      Base class for course errors
  InvalidCourseException.java
  DuplicateCourseException.java
  DataFolder.java           Checks file names and lists saved files
  CourseWebServer.java      Receives requests and decides which menu option to run
  PageBuilder.java          Builds each page from the HTML templates
  TemplateEngine.java       Fills the {{placeholders}} in the HTML files
web/                        HTML templates and the stylesheet
  layout.html               The page frame: sidebar menu, file status, main area
  dashboard.html            The home screen
  add.html, list.html, search.html, total.html, save.html, load.html, exit.html, ...
  style.css                 All the styles (white theme)
data/                       Saved course lists (CSV files)
run.bat / run.sh            Compile and start the program
```

## Where each requirement is met

| Requirement | Where in the code |
|---|---|
| Classes and objects | `Course`, `CourseManager`, `CourseWebServer`, `PageBuilder`, `DataFolder`, `TemplateEngine` |
| Methods for each operation | `addCourse`, `getAllCourses`, `findByCode`, `getTotalUnits`, `saveToFile`, `loadFromFile` in `CourseManager` |
| Decision-making for menu selection | The `switch (view)` in `CourseWebServer.showView` picks the menu option. Any other value shows an error message. |
| Handling invalid input | `Course.cleanCode`, `cleanTitle` and `cleanUnits` check every value. Bad values throw an exception, and its message is shown next to the field. |
| String processing | Trimming, upper-casing and reformatting codes (`cos201` becomes `COS 201`), title case (`intro TO programming ii` becomes `Intro to Programming II`), reading the department and level from a code (`COS 201` is a 200-level COS course), and a CSV line parser (`Course.splitCsvLine`) |
| Loops | Building the menu, table rows and file lists, reading files line by line, parsing CSV and form data, and trying ports 8080 to 8089 |
| Recursion | `CourseManager.searchFrom` (recursive search) and `CourseManager.sumUnitsFrom` (recursive total) |
| Exceptions | The custom classes `CourseException`, `InvalidCourseException` and `DuplicateCourseException`. The program also handles `IOException`, `NumberFormatException`, `CharacterCodingException` and `BindException`. |
| File reading and writing | `CourseManager.saveToFile` uses a `BufferedWriter`, and `loadFromFile` uses a `BufferedReader` |
| GUI | A dashboard made of HTML pages styled with CSS, shown in the browser |

## Rules for course data

- **Course code:** 2 to 4 letters followed by 3 digits, for example `COS 201` or `GST 111`.
- **Title:** 1 to 60 characters, and must contain letters.
- **Units:** a whole number from 1 to 6.
- **No duplicates:** each code can appear only once in the list.

To change these limits, edit the constants at the top of `Course.java`.
