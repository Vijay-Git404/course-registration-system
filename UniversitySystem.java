import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

abstract class Course {
    int id, capacity, facultyId;
    String name;

    Course(int id, String name, int capacity, int facultyId) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.facultyId = facultyId;
    }

    abstract String getType();
}

class CoreCourse extends Course {
    CoreCourse(int id, String name, int capacity, int facultyId) {
        super(id, name, capacity, facultyId);
    }

    String getType() {
        return "Core";
    }
}

class ElectiveCourse extends Course {
    ElectiveCourse(int id, String name, int capacity, int facultyId) {
        super(id, name, capacity, facultyId);
    }

    String getType() {
        return "Elective";
    }
}

class Student {
    int id;
    String name, department;

    Student(int id, String name, String department) {
        this.id = id;
        this.name = name;
        this.department = department;
    }
}

class Faculty {
    int id;
    String name;

    Faculty(int id, String name) {
        this.id = id;
        this.name = name;
    }
}

class RegistrationException extends Exception {
    RegistrationException(String message) {
        super(message);
    }
}

public class UniversitySystem {

    static Scanner sc = new Scanner(System.in);

    static Map<Integer, Student> students = new ConcurrentHashMap<>();
    static Map<Integer, Course> courses = new ConcurrentHashMap<>();
    static Map<Integer, Faculty> faculties = new ConcurrentHashMap<>();

    static String url = "jdbc:mysql://localhost:3306/university";
    static String user = "root";
    static String password = "password"; // Replace with your actual password

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // Add student
    static void addStudent() throws SQLException {
        System.out.print("Enter Student ID: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter Student Name: ");
        String name = sc.nextLine();

        System.out.print("Enter Department: ");
        String dept = sc.nextLine();

        Student s = new Student(id, name, dept);

        String sql = "INSERT INTO Student VALUES (?, ?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, s.id);
            ps.setString(2, s.name);
            ps.setString(3, s.department);

            ps.executeUpdate();
            students.put(id, s);

            System.out.println("Student added successfully!");
        }
    }

    // Add faculty
    static void addFaculty() throws SQLException {
        System.out.print("Enter Faculty ID: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter Faculty Name: ");
        String name = sc.nextLine();

        Faculty f = new Faculty(id, name);

        String sql = "INSERT INTO Faculty VALUES (?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, f.id);
            ps.setString(2, f.name);

            ps.executeUpdate();
            faculties.put(id, f);

            System.out.println("Faculty added successfully!");
        }
    }

    // Add course
    static void addCourse() throws SQLException {
        System.out.print("Enter Course ID: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter Course Name: ");
        String name = sc.nextLine();

        System.out.print("Enter Course Type (Core/Elective): ");
        String type = sc.nextLine();

        System.out.print("Enter Capacity: ");
        int capacity = sc.nextInt();

        System.out.print("Enter Faculty ID: ");
        int facultyId = sc.nextInt();

        if (capacity <= 0) {
            System.out.println("Capacity must be positive!");
            return;
        }

        Course c;

        if (type.equalsIgnoreCase("Core")) {
            c = new CoreCourse(id, name, capacity, facultyId);
        } else if (type.equalsIgnoreCase("Elective")) {
            c = new ElectiveCourse(id, name, capacity, facultyId);
        } else {
            System.out.println("Invalid course type!");
            return;
        }

        String sql = "INSERT INTO Course VALUES (?, ?, ?, ?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, c.id);
            ps.setString(2, c.name);
            ps.setString(3, c.getType());
            ps.setInt(4, c.capacity);
            ps.setInt(5, c.facultyId);

            ps.executeUpdate();
            courses.put(id, c);

            System.out.println("Course added successfully!");
        }
    }

    // Register student
    static void registerStudent() throws SQLException {
        System.out.print("Enter Student ID: ");
        int studentId = sc.nextInt();

        System.out.print("Enter Course ID: ");
        int courseId = sc.nextInt();

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                // Lock course row to prevent over-enrollment.
                String lockSql =
                    "SELECT capacity FROM Course WHERE course_id = ? FOR UPDATE";

                int capacity;

                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, courseId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new RegistrationException("Course not found!");
                        }
                        capacity = rs.getInt("capacity");
                    }
                }

                // Confirm student exists.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT student_id FROM Student WHERE student_id = ?")) {
                    ps.setInt(1, studentId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new RegistrationException("Student not found!");
                        }
                    }
                }

                // Check capacity.
                String countSql =
                    "SELECT COUNT(*) FROM Enrollment WHERE course_id = ?";

                try (PreparedStatement ps = con.prepareStatement(countSql)) {
                    ps.setInt(1, courseId);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();

                        if (rs.getInt(1) >= capacity) {
                            throw new RegistrationException("Course is full!");
                        }
                    }
                }

                // Insert registration.
                String insertSql =
                    "INSERT INTO Enrollment (student_id, course_id) VALUES (?, ?)";

                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, studentId);
                    ps.setInt(2, courseId);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Registration successful!");

            } catch (Exception e) {
                con.rollback();

                if (e instanceof RegistrationException ||
                    e instanceof SQLException) {
                    System.out.println("Registration failed: " + e.getMessage());
                } else {
                    throw new SQLException(e);
                }
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // Enter grade
    static void enterGrade() throws SQLException {
        System.out.print("Enter Student ID: ");
        int studentId = sc.nextInt();

        System.out.print("Enter Course ID: ");
        int courseId = sc.nextInt();

        System.out.print("Enter Grade (0-100): ");
        double grade = sc.nextDouble();

        if (grade < 0 || grade > 100) {
            System.out.println("Invalid grade!");
            return;
        }

        String sql =
            "UPDATE Enrollment SET grade = ? WHERE student_id = ? AND course_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, grade);
            ps.setInt(2, studentId);
            ps.setInt(3, courseId);

            if (ps.executeUpdate() > 0) {
                System.out.println("Grade saved successfully!");
            } else {
                System.out.println("Registration not found!");
            }
        }
    }

    // Display students and registered courses using JOIN
    static void displayRegistrations() throws SQLException {
        String sql = """
            SELECT s.student_id, s.name, c.course_name, c.course_type, e.grade
            FROM Student s
            JOIN Enrollment e ON s.student_id = e.student_id
            JOIN Course c ON e.course_id = c.course_id
            ORDER BY s.student_id
            """;

        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- Registration Details ---");

            while (rs.next()) {
                System.out.printf(
                    "ID: %d | Student: %s | Course: %s | Type: %s | Grade: %s%n",
                    rs.getInt("student_id"),
                    rs.getString("name"),
                    rs.getString("course_name"),
                    rs.getString("course_type"),
                    rs.getObject("grade")
                );
            }
        }
    }

    // Calculate average grade using Streams
    static void averageGrade() throws SQLException {
        List<Double> grades = new ArrayList<>();

        String sql = "SELECT grade FROM Enrollment WHERE grade IS NOT NULL";

        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                grades.add(rs.getDouble("grade"));
            }
        }

        double avg = grades.stream()
                           .mapToDouble(Double::doubleValue)
                           .average()
                           .orElse(0.0);

        System.out.println("Average Grade: " + avg);
    }

    // Find topper using Streams
    static void findTopper() throws SQLException {
        Map<String, Double> gradeMap = new HashMap<>();

        String sql = """
            SELECT s.name, e.grade
            FROM Student s
            JOIN Enrollment e ON s.student_id = e.student_id
            WHERE e.grade IS NOT NULL
            """;

        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                gradeMap.merge(
                    rs.getString("name"),
                    rs.getDouble("grade"),
                    Math::max
                );
            }
        }

        gradeMap.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresentOrElse(
                e -> System.out.println(
                    "Topper: " + e.getKey() + " | Grade: " + e.getValue()),
                () -> System.out.println("No grades available.")
            );
    }

    public static void main(String[] args) {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection con = getConnection()) {
                System.out.println("MySQL Connected Successfully!");
            }

            while (true) {
                System.out.println("\n===== UNIVERSITY COURSE REGISTRATION =====");
                System.out.println("1. Add Student");
                System.out.println("2. Add Faculty");
                System.out.println("3. Add Course");
                System.out.println("4. Register Student");
                System.out.println("5. Enter Grade");
                System.out.println("6. Display Registrations");
                System.out.println("7. Average Grade");
                System.out.println("8. Find Topper");
                System.out.println("9. Exit");
                System.out.print("Enter Choice: ");

                int choice = sc.nextInt();

                try {
                    switch (choice) {
                        case 1 -> addStudent();
                        case 2 -> addFaculty();
                        case 3 -> addCourse();
                        case 4 -> registerStudent();
                        case 5 -> enterGrade();
                        case 6 -> displayRegistrations();
                        case 7 -> averageGrade();
                        case 8 -> findTopper();
                        case 9 -> {
                            System.out.println("Program terminated.");
                            return;
                        }
                        default -> System.out.println("Invalid choice!");
                    }
                } catch (SQLException e) {
                    System.out.println("Database error: " + e.getMessage());
                } catch (InputMismatchException e) {
                    System.out.println("Invalid input. Enter the correct data type.");
                    sc.nextLine();
                }
            }

        } catch (Exception e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }
}