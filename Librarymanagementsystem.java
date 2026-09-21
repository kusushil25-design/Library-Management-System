/*
 * ============================================================
 *  LIBRARY MANAGEMENT SYSTEM  (Console Application, Java)
 * ============================================================
 *  Features:
 *    - Librarian login
 *    - Book management  (add, view, update, delete, sort)
 *    - Member management (add, view, update, delete, activate/deactivate)
 *    - Issue / Return / Renew books with due dates and fines
 *    - Searching (books and members)
 *    - Issue history and reports
 *    - Data saved permanently in text files (folder: data/)
 *
 *  How to run:
 *      javac Librarymanagementsystem.java
 *      java Librarymanagementsystem
 *
 *  Default login:   username = admin     password = admin123
 * ============================================================
 */

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/* ============================================================
 *  SETTINGS  (change these numbers to change library rules)
 * ============================================================ */
class Settings {
    static final int LOAN_DAYS = 14;          // days a book can be kept
    static final int RENEW_DAYS = 7;          // extra days when renewed
    static final int MAX_RENEWALS = 2;        // how many times one issue can be renewed
    static final int MAX_BOOKS_PER_MEMBER = 3;// max books a member can hold at once
    static final double FINE_PER_DAY = 5.0;   // fine for each late day
    static final String ADMIN_USER = "admin";
    static final String ADMIN_PASS = "admin123";
}

/* ============================================================
 *  INPUT HELPER
 *  Reads and validates everything typed by the user.
 * ============================================================ */
class InputHelper {
    private static final Scanner sc = new Scanner(System.in);

    // Read a full line of text (can be empty)
    static String readLine(String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            // input ended (e.g. Ctrl+D) - stop the program safely
            System.out.println("\nInput closed. Exiting...");
            System.exit(0);
        }
        return sc.nextLine().trim();
    }

    // Read text that must not be empty. The '|' character is replaced
    // because we use it as a separator inside the data files.
    static String readText(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (s.isEmpty()) {
                System.out.println("  ! This field cannot be empty. Try again.");
            } else {
                return s.replace("|", "/");
            }
        }
    }

    // Read text but allow blank (used in update: blank = keep old value)
    static String readOptionalText(String prompt) {
        return readLine(prompt).replace("|", "/");
    }

    // Read an integer
    static int readInt(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("  ! Please enter a valid whole number.");
            }
        }
    }

    // Read an integer between min and max (inclusive)
    static int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int n = readInt(prompt);
            if (n >= min && n <= max) {
                return n;
            }
            System.out.println("  ! Please enter a number from " + min + " to " + max + ".");
        }
    }

    // Read a positive integer (1 or more)
    static int readPositiveInt(String prompt) {
        while (true) {
            int n = readInt(prompt);
            if (n > 0) {
                return n;
            }
            System.out.println("  ! Number must be greater than 0.");
        }
    }

    // Read a phone number (digits only, 7 to 15 digits)
    static String readPhone(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (s.matches("\\d{7,15}")) {
                return s;
            }
            System.out.println("  ! Phone must contain only digits (7 to 15 digits).");
        }
    }

    // Read an email address (very simple check)
    static String readEmail(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (s.contains("@") && s.contains(".") && !s.contains(" ")
                    && !s.contains("|")) {
                return s;
            }
            System.out.println("  ! Please enter a valid email (example: name@mail.com).");
        }
    }

    // Read Yes / No
    static boolean readYesNo(String prompt) {
        while (true) {
            String s = readLine(prompt + " (y/n): ").toLowerCase();
            if (s.equals("y") || s.equals("yes")) {
                return true;
            }
            if (s.equals("n") || s.equals("no")) {
                return false;
            }
            System.out.println("  ! Please type y or n.");
        }
    }

    // Wait for Enter key
    static void pause() {
        readLine("\nPress Enter to continue...");
    }
}

/* ============================================================
 *  FILE HELPER
 *  Basic reading and writing of text files.
 * ============================================================ */
class FileHelper {
    static final String FOLDER = "data";

    // Create the data folder if it does not exist
    static void createFolder() {
        File folder = new File(FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    // Read every line of a file into a list
    static ArrayList<String> readLines(String fileName) {
        ArrayList<String> lines = new ArrayList<String>();
        File file = new File(FOLDER, fileName);
        if (!file.exists()) {
            return lines; // first run: no file yet
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading " + fileName + ": " + e.getMessage());
        }
        return lines;
    }

    // Write a list of lines into a file (old content is replaced)
    static void writeLines(String fileName, ArrayList<String> lines) {
        File file = new File(FOLDER, fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error writing " + fileName + ": " + e.getMessage());
        }
    }
}

/* ============================================================
 *  BOOK CLASS
 * ============================================================ */
class Book {
    private int id;
    private String title;
    private String author;
    private String category;
    private int totalCopies;
    private int availableCopies;

    public Book(int id, String title, String author, String category, int totalCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies; // new book: all copies are available
    }

    // Getters
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public int getIssuedCopies() { return totalCopies - availableCopies; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setCategory(String category) { this.category = category; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    // Is at least one copy on the shelf?
    public boolean isAvailable() {
        return availableCopies > 0;
    }

    // Take one copy off the shelf (book issued)
    public void decreaseAvailable() {
        if (availableCopies > 0) {
            availableCopies--;
        }
    }

    // Put one copy back on the shelf (book returned)
    public void increaseAvailable() {
        if (availableCopies < totalCopies) {
            availableCopies++;
        }
    }

    // Convert to one line for saving in file
    public String toFileString() {
        return id + "|" + title + "|" + author + "|" + category + "|"
                + totalCopies + "|" + availableCopies;
    }

    // Create a Book from one line of the file (returns null if line is bad)
    public static Book fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 6) {
            return null;
        }
        try {
            Book b = new Book(Integer.parseInt(p[0]), p[1], p[2], p[3],
                    Integer.parseInt(p[4]));
            b.setAvailableCopies(Integer.parseInt(p[5]));
            return b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Print table header
    public static void printHeader() {
        System.out.println("-------------------------------------------------------------------------------------");
        System.out.printf("%-5s %-28s %-20s %-12s %-6s %-5s%n",
                "ID", "Title", "Author", "Category", "Total", "Avail");
        System.out.println("-------------------------------------------------------------------------------------");
    }

    // Print one row of the table
    public void printRow() {
        System.out.printf("%-5d %-28s %-20s %-12s %-6d %-5d%n",
                id, cut(title, 28), cut(author, 20), cut(category, 12),
                totalCopies, availableCopies);
    }

    // Print full details
    public void printDetails() {
        System.out.println("  Book ID          : " + id);
        System.out.println("  Title            : " + title);
        System.out.println("  Author           : " + author);
        System.out.println("  Category         : " + category);
        System.out.println("  Total copies     : " + totalCopies);
        System.out.println("  Available copies : " + availableCopies);
        System.out.println("  Issued copies    : " + getIssuedCopies());
    }

    // Shorten long text so the table stays neat
    private static String cut(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 2) + "..";
    }
}

/* ============================================================
 *  MEMBER CLASS
 * ============================================================ */
class Member {
    private int id;
    private String name;
    private String phone;
    private String email;
    private LocalDate joinDate;
    private boolean active; // inactive members cannot borrow books

    public Member(int id, String name, String phone, String email, LocalDate joinDate) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.joinDate = joinDate;
        this.active = true;
    }

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public LocalDate getJoinDate() { return joinDate; }
    public boolean isActive() { return active; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setActive(boolean active) { this.active = active; }

    public String toFileString() {
        return id + "|" + name + "|" + phone + "|" + email + "|" + joinDate + "|" + active;
    }

    public static Member fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 6) {
            return null;
        }
        try {
            Member m = new Member(Integer.parseInt(p[0]), p[1], p[2], p[3],
                    LocalDate.parse(p[4]));
            m.setActive(Boolean.parseBoolean(p[5]));
            return m;
        } catch (NumberFormatException e) {
            return null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static void printHeader() {
        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("%-5s %-22s %-13s %-26s %-11s %-8s%n",
                "ID", "Name", "Phone", "Email", "Joined", "Status");
        System.out.println("------------------------------------------------------------------------------------");
    }

    public void printRow() {
        System.out.printf("%-5d %-22s %-13s %-26s %-11s %-8s%n",
                id, cut(name, 22), phone, cut(email, 26), joinDate,
                active ? "Active" : "Inactive");
    }

    public void printDetails() {
        System.out.println("  Member ID   : " + id);
        System.out.println("  Name        : " + name);
        System.out.println("  Phone       : " + phone);
        System.out.println("  Email       : " + email);
        System.out.println("  Joined on   : " + joinDate);
        System.out.println("  Status      : " + (active ? "Active" : "Inactive"));
    }

    private static String cut(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 2) + "..";
    }
}

/* ============================================================
 *  ISSUE RECORD CLASS
 *  One record = one book borrowed by one member.
 * ============================================================ */
class IssueRecord {
    private int issueId;
    private int bookId;
    private int memberId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate returnDate; // null means "not returned yet"
    private double fine;
    private int renewCount;

    public IssueRecord(int issueId, int bookId, int memberId,
                       LocalDate issueDate, LocalDate dueDate) {
        this.issueId = issueId;
        this.bookId = bookId;
        this.memberId = memberId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = null;
        this.fine = 0;
        this.renewCount = 0;
    }

    // Getters
    public int getIssueId() { return issueId; }
    public int getBookId() { return bookId; }
    public int getMemberId() { return memberId; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public double getFine() { return fine; }
    public int getRenewCount() { return renewCount; }

    // Setters
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public void setFine(double fine) { this.fine = fine; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setRenewCount(int renewCount) { this.renewCount = renewCount; }

    // Still with the member?
    public boolean isActive() {
        return returnDate == null;
    }

    // Is the book late right now (not returned and past due date)?
    public boolean isOverdue() {
        return isActive() && LocalDate.now().isAfter(dueDate);
    }

    // How many days late (0 if not late)
    public long daysLate() {
        LocalDate endDate;
        if (returnDate == null) {
            endDate = LocalDate.now();
        } else {
            endDate = returnDate;
        }
        long days = ChronoUnit.DAYS.between(dueDate, endDate);
        if (days < 0) {
            return 0;
        }
        return days;
    }

    // Fine = late days x fine per day
    public double calculateFine() {
        return daysLate() * Settings.FINE_PER_DAY;
    }

    public String getStatusText() {
        if (!isActive()) {
            return "Returned";
        }
        if (isOverdue()) {
            return "OVERDUE";
        }
        return "Issued";
    }

    public String toFileString() {
        String ret;
        if (returnDate == null) {
            ret = "NA";
        } else {
            ret = returnDate.toString();
        }
        return issueId + "|" + bookId + "|" + memberId + "|" + issueDate + "|"
                + dueDate + "|" + ret + "|" + fine + "|" + renewCount;
    }

    public static IssueRecord fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 8) {
            return null;
        }
        try {
            IssueRecord r = new IssueRecord(Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    LocalDate.parse(p[3]), LocalDate.parse(p[4]));
            if (!p[5].equals("NA")) {
                r.setReturnDate(LocalDate.parse(p[5]));
            }
            r.setFine(Double.parseDouble(p[6]));
            r.setRenewCount(Integer.parseInt(p[7]));
            return r;
        } catch (NumberFormatException e) {
            return null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static void printHeader() {
        System.out.println("---------------------------------------------------------------------------------------");
        System.out.printf("%-6s %-6s %-7s %-11s %-11s %-11s %-9s %-8s%n",
                "IssID", "BookID", "Member", "Issued", "Due", "Returned", "Status", "Fine");
        System.out.println("---------------------------------------------------------------------------------------");
    }

    public void printRow() {
        String ret;
        if (returnDate == null) {
            ret = "-";
        } else {
            ret = returnDate.toString();
        }
        // For books still out, show the fine so far
        double shownFine = fine;
        if (isActive()) {
            shownFine = calculateFine();
        }
        System.out.printf("%-6d %-6d %-7d %-11s %-11s %-11s %-9s %-8.2f%n",
                issueId, bookId, memberId, issueDate, dueDate, ret,
                getStatusText(), shownFine);
    }
}

/* ============================================================
 *  BOOK MANAGER
 *  Keeps the list of books and saves/loads it from file.
 * ============================================================ */
class BookManager {
    private static final String FILE_NAME = "books.txt";
    private ArrayList<Book> books = new ArrayList<Book>();

    public BookManager() {
        load();
    }

    // Load books from file
    private void load() {
        books.clear();
        for (String line : FileHelper.readLines(FILE_NAME)) {
            Book b = Book.fromFileString(line);
            if (b != null) {
                books.add(b);
            }
        }
    }

    // Save all books to file
    public void save() {
        ArrayList<String> lines = new ArrayList<String>();
        for (Book b : books) {
            lines.add(b.toFileString());
        }
        FileHelper.writeLines(FILE_NAME, lines);
    }

    public ArrayList<Book> getAll() {
        return books;
    }

    public int count() {
        return books.size();
    }

    // Next free ID = biggest ID + 1
    public int nextId() {
        int max = 0;
        for (Book b : books) {
            if (b.getId() > max) {
                max = b.getId();
            }
        }
        return max + 1;
    }

    // Find a book by ID (null if not found)
    public Book findById(int id) {
        for (Book b : books) {
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    // Check if same title + author already exists (duplicate check)
    public boolean exists(String title, String author) {
        for (Book b : books) {
            if (b.getTitle().equalsIgnoreCase(title)
                    && b.getAuthor().equalsIgnoreCase(author)) {
                return true;
            }
        }
        return false;
    }

    public void add(Book book) {
        books.add(book);
        save();
    }

    public void remove(Book book) {
        books.remove(book);
        save();
    }

    // Search by part of the title
    public ArrayList<Book> searchByTitle(String text) {
        ArrayList<Book> result = new ArrayList<Book>();
        for (Book b : books) {
            if (b.getTitle().toLowerCase().contains(text.toLowerCase())) {
                result.add(b);
            }
        }
        return result;
    }

    // Search by part of the author name
    public ArrayList<Book> searchByAuthor(String text) {
        ArrayList<Book> result = new ArrayList<Book>();
        for (Book b : books) {
            if (b.getAuthor().toLowerCase().contains(text.toLowerCase())) {
                result.add(b);
            }
        }
        return result;
    }

    // Search by category
    public ArrayList<Book> searchByCategory(String text) {
        ArrayList<Book> result = new ArrayList<Book>();
        for (Book b : books) {
            if (b.getCategory().toLowerCase().contains(text.toLowerCase())) {
                result.add(b);
            }
        }
        return result;
    }

    // Only books with copies on the shelf
    public ArrayList<Book> getAvailableBooks() {
        ArrayList<Book> result = new ArrayList<Book>();
        for (Book b : books) {
            if (b.isAvailable()) {
                result.add(b);
            }
        }
        return result;
    }

    // Return a sorted COPY of the list (original stays unchanged)
    public ArrayList<Book> getSortedByTitle() {
        ArrayList<Book> copy = new ArrayList<Book>(books);
        Collections.sort(copy, new Comparator<Book>() {
            public int compare(Book a, Book b) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
        });
        return copy;
    }

    public ArrayList<Book> getSortedByAuthor() {
        ArrayList<Book> copy = new ArrayList<Book>(books);
        Collections.sort(copy, new Comparator<Book>() {
            public int compare(Book a, Book b) {
                return a.getAuthor().compareToIgnoreCase(b.getAuthor());
            }
        });
        return copy;
    }

    public int totalCopies() {
        int sum = 0;
        for (Book b : books) {
            sum += b.getTotalCopies();
        }
        return sum;
    }

    public int availableCopies() {
        int sum = 0;
        for (Book b : books) {
            sum += b.getAvailableCopies();
        }
        return sum;
    }
}

/* ============================================================
 *  MEMBER MANAGER
 * ============================================================ */
class MemberManager {
    private static final String FILE_NAME = "members.txt";
    private ArrayList<Member> members = new ArrayList<Member>();

    public MemberManager() {
        load();
    }

    private void load() {
        members.clear();
        for (String line : FileHelper.readLines(FILE_NAME)) {
            Member m = Member.fromFileString(line);
            if (m != null) {
                members.add(m);
            }
        }
    }

    public void save() {
        ArrayList<String> lines = new ArrayList<String>();
        for (Member m : members) {
            lines.add(m.toFileString());
        }
        FileHelper.writeLines(FILE_NAME, lines);
    }

    public ArrayList<Member> getAll() {
        return members;
    }

    public int count() {
        return members.size();
    }

    public int nextId() {
        int max = 0;
        for (Member m : members) {
            if (m.getId() > max) {
                max = m.getId();
            }
        }
        return max + 1;
    }

    public Member findById(int id) {
        for (Member m : members) {
            if (m.getId() == id) {
                return m;
            }
        }
        return null;
    }

    // Phone number must be unique for each member
    public boolean phoneExists(String phone) {
        for (Member m : members) {
            if (m.getPhone().equals(phone)) {
                return true;
            }
        }
        return false;
    }

    // Same check, but ignoring one member (used while updating)
    public boolean phoneUsedByOther(String phone, int ownId) {
        for (Member m : members) {
            if (m.getPhone().equals(phone) && m.getId() != ownId) {
                return true;
            }
        }
        return false;
    }

    public void add(Member member) {
        members.add(member);
        save();
    }

    public void remove(Member member) {
        members.remove(member);
        save();
    }

    public ArrayList<Member> searchByName(String text) {
        ArrayList<Member> result = new ArrayList<Member>();
        for (Member m : members) {
            if (m.getName().toLowerCase().contains(text.toLowerCase())) {
                result.add(m);
            }
        }
        return result;
    }

    public ArrayList<Member> searchByPhone(String text) {
        ArrayList<Member> result = new ArrayList<Member>();
        for (Member m : members) {
            if (m.getPhone().contains(text)) {
                result.add(m);
            }
        }
        return result;
    }

    public ArrayList<Member> getSortedByName() {
        ArrayList<Member> copy = new ArrayList<Member>(members);
        Collections.sort(copy, new Comparator<Member>() {
            public int compare(Member a, Member b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return copy;
    }

    public int countActive() {
        int count = 0;
        for (Member m : members) {
            if (m.isActive()) {
                count++;
            }
        }
        return count;
    }
}

/* ============================================================
 *  ISSUE MANAGER
 *  Handles issue, return, renew and history.
 * ============================================================ */
class IssueManager {
    private static final String FILE_NAME = "issues.txt";
    private ArrayList<IssueRecord> records = new ArrayList<IssueRecord>();
    private BookManager bookManager;
    private MemberManager memberManager;

    public IssueManager(BookManager bookManager, MemberManager memberManager) {
        this.bookManager = bookManager;
        this.memberManager = memberManager;
        load();
    }

    private void load() {
        records.clear();
        for (String line : FileHelper.readLines(FILE_NAME)) {
            IssueRecord r = IssueRecord.fromFileString(line);
            if (r != null) {
                records.add(r);
            }
        }
    }

    public void save() {
        ArrayList<String> lines = new ArrayList<String>();
        for (IssueRecord r : records) {
            lines.add(r.toFileString());
        }
        FileHelper.writeLines(FILE_NAME, lines);
    }

    public ArrayList<IssueRecord> getAll() {
        return records;
    }

    public int nextId() {
        int max = 0;
        for (IssueRecord r : records) {
            if (r.getIssueId() > max) {
                max = r.getIssueId();
            }
        }
        return max + 1;
    }

    public IssueRecord findById(int issueId) {
        for (IssueRecord r : records) {
            if (r.getIssueId() == issueId) {
                return r;
            }
        }
        return null;
    }

    // ---------- counting helpers ----------

    // How many books does this member currently hold?
    public int countActiveForMember(int memberId) {
        int count = 0;
        for (IssueRecord r : records) {
            if (r.getMemberId() == memberId && r.isActive()) {
                count++;
            }
        }
        return count;
    }

    // Is this book currently issued to anyone?
    public boolean bookHasActiveIssue(int bookId) {
        for (IssueRecord r : records) {
            if (r.getBookId() == bookId && r.isActive()) {
                return true;
            }
        }
        return false;
    }

    // Does this member already hold this same book?
    public boolean memberHoldsBook(int memberId, int bookId) {
        for (IssueRecord r : records) {
            if (r.getMemberId() == memberId && r.getBookId() == bookId
                    && r.isActive()) {
                return true;
            }
        }
        return false;
    }

    // Does this member have any unreturned book?
    public boolean memberHasActiveIssue(int memberId) {
        return countActiveForMember(memberId) > 0;
    }

    // ---------- main operations ----------

    /*
     * Issue a book to a member.
     * Returns "" if everything is OK, otherwise returns the error message.
     */
    public String issueBook(int bookId, int memberId) {
        Book book = bookManager.findById(bookId);
        if (book == null) {
            return "Book with ID " + bookId + " was not found.";
        }
        Member member = memberManager.findById(memberId);
        if (member == null) {
            return "Member with ID " + memberId + " was not found.";
        }
        if (!member.isActive()) {
            return "This member is inactive and cannot borrow books.";
        }
        if (!book.isAvailable()) {
            return "No copies of this book are available right now.";
        }
        if (countActiveForMember(memberId) >= Settings.MAX_BOOKS_PER_MEMBER) {
            return "Member already holds the maximum of "
                    + Settings.MAX_BOOKS_PER_MEMBER + " books.";
        }
        if (memberHoldsBook(memberId, bookId)) {
            return "This member already has a copy of this book.";
        }
        if (memberHasOverdue(memberId)) {
            return "Member has an overdue book. Return it before borrowing again.";
        }

        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(Settings.LOAN_DAYS);
        IssueRecord record = new IssueRecord(nextId(), bookId, memberId, today, due);
        records.add(record);
        book.decreaseAvailable();

        save();
        bookManager.save();

        System.out.println("\n  Book issued successfully!");
        System.out.println("  Issue ID   : " + record.getIssueId());
        System.out.println("  Book       : " + book.getTitle());
        System.out.println("  Member     : " + member.getName());
        System.out.println("  Issue date : " + today);
        System.out.println("  Due date   : " + due);
        return "";
    }

    // Does the member have any book that is overdue right now?
    public boolean memberHasOverdue(int memberId) {
        for (IssueRecord r : records) {
            if (r.getMemberId() == memberId && r.isOverdue()) {
                return true;
            }
        }
        return false;
    }

    /*
     * Return a book.  Returns "" if OK, otherwise an error message.
     */
    public String returnBook(int issueId) {
        IssueRecord record = findById(issueId);
        if (record == null) {
            return "Issue record with ID " + issueId + " was not found.";
        }
        if (!record.isActive()) {
            return "This book was already returned on " + record.getReturnDate() + ".";
        }

        record.setReturnDate(LocalDate.now());
        double fine = record.calculateFine();
        record.setFine(fine);

        Book book = bookManager.findById(record.getBookId());
        if (book != null) {
            book.increaseAvailable();
        }

        save();
        bookManager.save();

        Member member = memberManager.findById(record.getMemberId());
        System.out.println("\n  Book returned successfully!");
        System.out.println("  Book        : " + (book != null ? book.getTitle() : "(deleted)"));
        System.out.println("  Member      : " + (member != null ? member.getName() : "(deleted)"));
        System.out.println("  Due date    : " + record.getDueDate());
        System.out.println("  Return date : " + record.getReturnDate());
        System.out.println("  Days late   : " + record.daysLate());
        if (fine > 0) {
            System.out.printf("  FINE TO PAY : %.2f%n", fine);
        } else {
            System.out.println("  No fine. Thank you!");
        }
        return "";
    }

    /*
     * Renew a book (extend the due date).
     * Returns "" if OK, otherwise an error message.
     */
    public String renewBook(int issueId) {
        IssueRecord record = findById(issueId);
        if (record == null) {
            return "Issue record with ID " + issueId + " was not found.";
        }
        if (!record.isActive()) {
            return "This book is already returned, it cannot be renewed.";
        }
        if (record.isOverdue()) {
            return "Overdue books cannot be renewed. Please return the book.";
        }
        if (record.getRenewCount() >= Settings.MAX_RENEWALS) {
            return "This book was already renewed " + Settings.MAX_RENEWALS
                    + " times. No more renewals allowed.";
        }

        LocalDate newDue = record.getDueDate().plusDays(Settings.RENEW_DAYS);
        record.setDueDate(newDue);
        record.setRenewCount(record.getRenewCount() + 1);
        save();

        System.out.println("\n  Book renewed successfully!");
        System.out.println("  New due date : " + newDue);
        System.out.println("  Renewals used: " + record.getRenewCount()
                + " of " + Settings.MAX_RENEWALS);
        return "";
    }

    // ---------- lists and totals ----------

    public ArrayList<IssueRecord> getActiveRecords() {
        ArrayList<IssueRecord> result = new ArrayList<IssueRecord>();
        for (IssueRecord r : records) {
            if (r.isActive()) {
                result.add(r);
            }
        }
        return result;
    }

    public ArrayList<IssueRecord> getOverdueRecords() {
        ArrayList<IssueRecord> result = new ArrayList<IssueRecord>();
        for (IssueRecord r : records) {
            if (r.isOverdue()) {
                result.add(r);
            }
        }
        return result;
    }

    public ArrayList<IssueRecord> getHistoryForMember(int memberId) {
        ArrayList<IssueRecord> result = new ArrayList<IssueRecord>();
        for (IssueRecord r : records) {
            if (r.getMemberId() == memberId) {
                result.add(r);
            }
        }
        return result;
    }

    public ArrayList<IssueRecord> getHistoryForBook(int bookId) {
        ArrayList<IssueRecord> result = new ArrayList<IssueRecord>();
        for (IssueRecord r : records) {
            if (r.getBookId() == bookId) {
                result.add(r);
            }
        }
        return result;
    }

    // Total fines collected from returned books
    public double totalFinesCollected() {
        double total = 0;
        for (IssueRecord r : records) {
            if (!r.isActive()) {
                total += r.getFine();
            }
        }
        return total;
    }

    // Fines that will be charged for books that are still late
    public double totalPendingFines() {
        double total = 0;
        for (IssueRecord r : records) {
            if (r.isOverdue()) {
                total += r.calculateFine();
            }
        }
        return total;
    }

    // How many times has this book been issued in total?
    public int timesIssued(int bookId) {
        int count = 0;
        for (IssueRecord r : records) {
            if (r.getBookId() == bookId) {
                count++;
            }
        }
        return count;
    }
}

/* ============================================================
 *  BOOK MENU  (all screens related to books)
 * ============================================================ */
class BookMenu {
    private BookManager bookManager;
    private IssueManager issueManager;

    public BookMenu(BookManager bookManager, IssueManager issueManager) {
        this.bookManager = bookManager;
        this.issueManager = issueManager;
    }

    public void show() {
        while (true) {
            System.out.println("\n========== BOOK MANAGEMENT ==========");
            System.out.println(" 1. Add new book");
            System.out.println(" 2. View all books");
            System.out.println(" 3. View book details");
            System.out.println(" 4. Update book");
            System.out.println(" 5. Delete book");
            System.out.println(" 6. View books sorted by title");
            System.out.println(" 7. View books sorted by author");
            System.out.println(" 0. Back to main menu");
            int choice = InputHelper.readIntInRange("Enter choice: ", 0, 7);

            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1: addBook(); break;
                case 2: viewAll(bookManager.getAll(), "ALL BOOKS"); break;
                case 3: viewDetails(); break;
                case 4: updateBook(); break;
                case 5: deleteBook(); break;
                case 6: viewAll(bookManager.getSortedByTitle(), "BOOKS SORTED BY TITLE"); break;
                case 7: viewAll(bookManager.getSortedByAuthor(), "BOOKS SORTED BY AUTHOR"); break;
            }
            InputHelper.pause();
        }
    }

    private void addBook() {
        System.out.println("\n--- Add New Book ---");
        String title = InputHelper.readText("Title    : ");
        String author = InputHelper.readText("Author   : ");

        if (bookManager.exists(title, author)) {
            System.out.println("  ! This book (same title and author) already exists.");
            System.out.println("    Use 'Update book' to change the number of copies.");
            return;
        }

        String category = InputHelper.readText("Category : ");
        int copies = InputHelper.readPositiveInt("Number of copies: ");

        Book book = new Book(bookManager.nextId(), title, author, category, copies);
        bookManager.add(book);
        System.out.println("  Book added successfully with ID " + book.getId() + ".");
    }

    // Prints a list of books as a table
    private void viewAll(ArrayList<Book> list, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (list.isEmpty()) {
            System.out.println("  No books found.");
            return;
        }
        Book.printHeader();
        for (Book b : list) {
            b.printRow();
        }
        System.out.println("Total: " + list.size() + " book title(s)");
    }

    private void viewDetails() {
        int id = InputHelper.readInt("Enter book ID: ");
        Book book = bookManager.findById(id);
        if (book == null) {
            System.out.println("  ! Book not found.");
            return;
        }
        System.out.println("\n--- Book Details ---");
        book.printDetails();
        System.out.println("  Times issued     : " + issueManager.timesIssued(id));
    }

    private void updateBook() {
        int id = InputHelper.readInt("Enter ID of the book to update: ");
        Book book = bookManager.findById(id);
        if (book == null) {
            System.out.println("  ! Book not found.");
            return;
        }
        System.out.println("\nCurrent details:");
        book.printDetails();
        System.out.println("\n(Press Enter to keep the old value)");

        String title = InputHelper.readOptionalText("New title    [" + book.getTitle() + "]: ");
        if (!title.isEmpty()) {
            book.setTitle(title);
        }
        String author = InputHelper.readOptionalText("New author   [" + book.getAuthor() + "]: ");
        if (!author.isEmpty()) {
            book.setAuthor(author);
        }
        String category = InputHelper.readOptionalText("New category [" + book.getCategory() + "]: ");
        if (!category.isEmpty()) {
            book.setCategory(category);
        }

        if (InputHelper.readYesNo("Change number of copies?")) {
            int issued = book.getIssuedCopies();
            int newTotal = InputHelper.readPositiveInt("New total copies: ");
            if (newTotal < issued) {
                System.out.println("  ! " + issued + " copies are currently issued. "
                        + "Total cannot be less than that.");
            } else {
                book.setTotalCopies(newTotal);
                book.setAvailableCopies(newTotal - issued);
            }
        }
        bookManager.save();
        System.out.println("  Book updated successfully.");
    }

    private void deleteBook() {
        int id = InputHelper.readInt("Enter ID of the book to delete: ");
        Book book = bookManager.findById(id);
        if (book == null) {
            System.out.println("  ! Book not found.");
            return;
        }
        if (issueManager.bookHasActiveIssue(id)) {
            System.out.println("  ! Cannot delete. Some copies of this book are still issued.");
            return;
        }
        book.printDetails();
        if (InputHelper.readYesNo("Are you sure you want to delete this book?")) {
            bookManager.remove(book);
            System.out.println("  Book deleted.");
        } else {
            System.out.println("  Delete cancelled.");
        }
    }
}

/* ============================================================
 *  MEMBER MENU  (all screens related to members)
 * ============================================================ */
class MemberMenu {
    private MemberManager memberManager;
    private IssueManager issueManager;

    public MemberMenu(MemberManager memberManager, IssueManager issueManager) {
        this.memberManager = memberManager;
        this.issueManager = issueManager;
    }

    public void show() {
        while (true) {
            System.out.println("\n========== MEMBER MANAGEMENT ==========");
            System.out.println(" 1. Add new member");
            System.out.println(" 2. View all members");
            System.out.println(" 3. View member details");
            System.out.println(" 4. Update member");
            System.out.println(" 5. Delete member");
            System.out.println(" 6. Activate / Deactivate member");
            System.out.println(" 7. View members sorted by name");
            System.out.println(" 0. Back to main menu");
            int choice = InputHelper.readIntInRange("Enter choice: ", 0, 7);

            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1: addMember(); break;
                case 2: viewAll(memberManager.getAll(), "ALL MEMBERS"); break;
                case 3: viewDetails(); break;
                case 4: updateMember(); break;
                case 5: deleteMember(); break;
                case 6: changeStatus(); break;
                case 7: viewAll(memberManager.getSortedByName(), "MEMBERS SORTED BY NAME"); break;
            }
            InputHelper.pause();
        }
    }

    private void addMember() {
        System.out.println("\n--- Add New Member ---");
        String name = InputHelper.readText("Name  : ");
        String phone = InputHelper.readPhone("Phone : ");
        if (memberManager.phoneExists(phone)) {
            System.out.println("  ! A member with this phone number already exists.");
            return;
        }
        String email = InputHelper.readEmail("Email : ");

        Member member = new Member(memberManager.nextId(), name, phone, email,
                LocalDate.now());
        memberManager.add(member);
        System.out.println("  Member added successfully with ID " + member.getId() + ".");
    }

    private void viewAll(ArrayList<Member> list, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (list.isEmpty()) {
            System.out.println("  No members found.");
            return;
        }
        Member.printHeader();
        for (Member m : list) {
            m.printRow();
        }
        System.out.println("Total: " + list.size() + " member(s)");
    }

    private void viewDetails() {
        int id = InputHelper.readInt("Enter member ID: ");
        Member member = memberManager.findById(id);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        System.out.println("\n--- Member Details ---");
        member.printDetails();
        System.out.println("  Books held now : " + issueManager.countActiveForMember(id)
                + " of " + Settings.MAX_BOOKS_PER_MEMBER);
    }

    private void updateMember() {
        int id = InputHelper.readInt("Enter ID of the member to update: ");
        Member member = memberManager.findById(id);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        System.out.println("\nCurrent details:");
        member.printDetails();
        System.out.println("\n(Press Enter to keep the old value)");

        String name = InputHelper.readOptionalText("New name  [" + member.getName() + "]: ");
        if (!name.isEmpty()) {
            member.setName(name);
        }

        // Phone: ask until valid or blank
        while (true) {
            String phone = InputHelper.readOptionalText("New phone [" + member.getPhone() + "]: ");
            if (phone.isEmpty()) {
                break;
            }
            if (!phone.matches("\\d{7,15}")) {
                System.out.println("  ! Phone must contain only digits (7 to 15 digits).");
            } else if (memberManager.phoneUsedByOther(phone, id)) {
                System.out.println("  ! Another member already uses this phone number.");
            } else {
                member.setPhone(phone);
                break;
            }
        }

        // Email: ask until valid or blank
        while (true) {
            String email = InputHelper.readOptionalText("New email [" + member.getEmail() + "]: ");
            if (email.isEmpty()) {
                break;
            }
            if (email.contains("@") && email.contains(".") && !email.contains(" ")) {
                member.setEmail(email);
                break;
            }
            System.out.println("  ! Please enter a valid email (example: name@mail.com).");
        }

        memberManager.save();
        System.out.println("  Member updated successfully.");
    }

    private void deleteMember() {
        int id = InputHelper.readInt("Enter ID of the member to delete: ");
        Member member = memberManager.findById(id);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        if (issueManager.memberHasActiveIssue(id)) {
            System.out.println("  ! Cannot delete. This member still has unreturned books.");
            return;
        }
        member.printDetails();
        if (InputHelper.readYesNo("Are you sure you want to delete this member?")) {
            memberManager.remove(member);
            System.out.println("  Member deleted.");
        } else {
            System.out.println("  Delete cancelled.");
        }
    }

    private void changeStatus() {
        int id = InputHelper.readInt("Enter member ID: ");
        Member member = memberManager.findById(id);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        System.out.println("  Current status: " + (member.isActive() ? "Active" : "Inactive"));
        if (member.isActive()) {
            if (InputHelper.readYesNo("Deactivate this member?")) {
                member.setActive(false);
                memberManager.save();
                System.out.println("  Member is now Inactive.");
            }
        } else {
            if (InputHelper.readYesNo("Activate this member?")) {
                member.setActive(true);
                memberManager.save();
                System.out.println("  Member is now Active.");
            }
        }
    }
}

/* ============================================================
 *  ISSUE MENU  (issue, return, renew, history)
 * ============================================================ */
class IssueMenu {
    private BookManager bookManager;
    private MemberManager memberManager;
    private IssueManager issueManager;

    public IssueMenu(BookManager bookManager, MemberManager memberManager,
                     IssueManager issueManager) {
        this.bookManager = bookManager;
        this.memberManager = memberManager;
        this.issueManager = issueManager;
    }

    public void show() {
        while (true) {
            System.out.println("\n========== ISSUE / RETURN ==========");
            System.out.println(" 1. Issue a book");
            System.out.println(" 2. Return a book");
            System.out.println(" 3. Renew a book");
            System.out.println(" 4. View books currently issued");
            System.out.println(" 5. View all issue history");
            System.out.println(" 6. View history of a member");
            System.out.println(" 7. View history of a book");
            System.out.println(" 0. Back to main menu");
            int choice = InputHelper.readIntInRange("Enter choice: ", 0, 7);

            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1: issue(); break;
                case 2: giveBack(); break;
                case 3: renew(); break;
                case 4: showList(issueManager.getActiveRecords(), "BOOKS CURRENTLY ISSUED"); break;
                case 5: showList(issueManager.getAll(), "COMPLETE ISSUE HISTORY"); break;
                case 6: memberHistory(); break;
                case 7: bookHistory(); break;
            }
            InputHelper.pause();
        }
    }

    private void issue() {
        System.out.println("\n--- Issue a Book ---");
        int memberId = InputHelper.readInt("Enter member ID: ");
        Member member = memberManager.findById(memberId);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        System.out.println("  Member: " + member.getName()
                + " (holding " + issueManager.countActiveForMember(memberId) + " book(s))");

        int bookId = InputHelper.readInt("Enter book ID  : ");
        Book book = bookManager.findById(bookId);
        if (book == null) {
            System.out.println("  ! Book not found.");
            return;
        }
        System.out.println("  Book  : " + book.getTitle()
                + " (available: " + book.getAvailableCopies() + ")");

        String error = issueManager.issueBook(bookId, memberId);
        if (!error.isEmpty()) {
            System.out.println("  ! " + error);
        }
    }

    private void giveBack() {
        System.out.println("\n--- Return a Book ---");
        int memberId = InputHelper.readInt("Enter member ID: ");
        Member member = memberManager.findById(memberId);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        // Show the books this member currently holds
        ArrayList<IssueRecord> held = new ArrayList<IssueRecord>();
        for (IssueRecord r : issueManager.getHistoryForMember(memberId)) {
            if (r.isActive()) {
                held.add(r);
            }
        }
        if (held.isEmpty()) {
            System.out.println("  This member has no books to return.");
            return;
        }
        System.out.println("  Books held by " + member.getName() + ":");
        IssueRecord.printHeader();
        for (IssueRecord r : held) {
            r.printRow();
        }
        int issueId = InputHelper.readInt("Enter Issue ID to return: ");
        IssueRecord chosen = issueManager.findById(issueId);
        if (chosen == null || chosen.getMemberId() != memberId) {
            System.out.println("  ! That Issue ID does not belong to this member.");
            return;
        }
        String error = issueManager.returnBook(issueId);
        if (!error.isEmpty()) {
            System.out.println("  ! " + error);
        }
    }

    private void renew() {
        System.out.println("\n--- Renew a Book ---");
        int issueId = InputHelper.readInt("Enter Issue ID: ");
        String error = issueManager.renewBook(issueId);
        if (!error.isEmpty()) {
            System.out.println("  ! " + error);
        }
    }

    private void showList(ArrayList<IssueRecord> list, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (list.isEmpty()) {
            System.out.println("  No records found.");
            return;
        }
        IssueRecord.printHeader();
        for (IssueRecord r : list) {
            r.printRow();
        }
        System.out.println("Total: " + list.size() + " record(s)");
    }

    private void memberHistory() {
        int id = InputHelper.readInt("Enter member ID: ");
        Member member = memberManager.findById(id);
        if (member == null) {
            System.out.println("  ! Member not found.");
            return;
        }
        showList(issueManager.getHistoryForMember(id),
                "HISTORY OF " + member.getName().toUpperCase());
    }

    private void bookHistory() {
        int id = InputHelper.readInt("Enter book ID: ");
        Book book = bookManager.findById(id);
        if (book == null) {
            System.out.println("  ! Book not found.");
            return;
        }
        showList(issueManager.getHistoryForBook(id),
                "HISTORY OF \"" + book.getTitle() + "\"");
    }
}

/* ============================================================
 *  SEARCH MENU
 * ============================================================ */
class SearchMenu {
    private BookManager bookManager;
    private MemberManager memberManager;

    public SearchMenu(BookManager bookManager, MemberManager memberManager) {
        this.bookManager = bookManager;
        this.memberManager = memberManager;
    }

    public void show() {
        while (true) {
            System.out.println("\n========== SEARCH ==========");
            System.out.println(" 1. Search book by ID");
            System.out.println(" 2. Search book by title");
            System.out.println(" 3. Search book by author");
            System.out.println(" 4. Search book by category");
            System.out.println(" 5. Show only available books");
            System.out.println(" 6. Search member by name");
            System.out.println(" 7. Search member by phone");
            System.out.println(" 0. Back to main menu");
            int choice = InputHelper.readIntInRange("Enter choice: ", 0, 7);

            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1: byId(); break;
                case 2:
                    showBooks(bookManager.searchByTitle(
                            InputHelper.readText("Enter part of the title: ")));
                    break;
                case 3:
                    showBooks(bookManager.searchByAuthor(
                            InputHelper.readText("Enter part of the author name: ")));
                    break;
                case 4:
                    showBooks(bookManager.searchByCategory(
                            InputHelper.readText("Enter category: ")));
                    break;
                case 5: showBooks(bookManager.getAvailableBooks()); break;
                case 6:
                    showMembers(memberManager.searchByName(
                            InputHelper.readText("Enter part of the name: ")));
                    break;
                case 7:
                    showMembers(memberManager.searchByPhone(
                            InputHelper.readText("Enter part of the phone number: ")));
                    break;
            }
            InputHelper.pause();
        }
    }

    private void byId() {
        int id = InputHelper.readInt("Enter book ID: ");
        Book book = bookManager.findById(id);
        if (book == null) {
            System.out.println("  ! Book not found.");
        } else {
            System.out.println();
            book.printDetails();
        }
    }

    private void showBooks(ArrayList<Book> list) {
        System.out.println();
        if (list.isEmpty()) {
            System.out.println("  No matching books found.");
            return;
        }
        Book.printHeader();
        for (Book b : list) {
            b.printRow();
        }
        System.out.println(list.size() + " book(s) found.");
    }

    private void showMembers(ArrayList<Member> list) {
        System.out.println();
        if (list.isEmpty()) {
            System.out.println("  No matching members found.");
            return;
        }
        Member.printHeader();
        for (Member m : list) {
            m.printRow();
        }
        System.out.println(list.size() + " member(s) found.");
    }
}

/* ============================================================
 *  REPORT MENU
 * ============================================================ */
class ReportMenu {
    private BookManager bookManager;
    private MemberManager memberManager;
    private IssueManager issueManager;

    public ReportMenu(BookManager bookManager, MemberManager memberManager,
                      IssueManager issueManager) {
        this.bookManager = bookManager;
        this.memberManager = memberManager;
        this.issueManager = issueManager;
    }

    public void show() {
        while (true) {
            System.out.println("\n========== REPORTS ==========");
            System.out.println(" 1. Library summary");
            System.out.println(" 2. Overdue books");
            System.out.println(" 3. Fine report");
            System.out.println(" 4. Most popular books");
            System.out.println(" 5. Books with no copies available");
            System.out.println(" 6. Members who currently hold books");
            System.out.println(" 0. Back to main menu");
            int choice = InputHelper.readIntInRange("Enter choice: ", 0, 6);

            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1: summary(); break;
                case 2: overdue(); break;
                case 3: fines(); break;
                case 4: popular(); break;
                case 5: unavailable(); break;
                case 6: membersWithBooks(); break;
            }
            InputHelper.pause();
        }
    }

    private void summary() {
        System.out.println("\n--- LIBRARY SUMMARY ---");
        System.out.println("  Book titles            : " + bookManager.count());
        System.out.println("  Total copies           : " + bookManager.totalCopies());
        System.out.println("  Copies on shelf        : " + bookManager.availableCopies());
        System.out.println("  Copies issued          : "
                + (bookManager.totalCopies() - bookManager.availableCopies()));
        System.out.println("  Registered members     : " + memberManager.count());
        System.out.println("  Active members         : " + memberManager.countActive());
        System.out.println("  Total issues (ever)    : " + issueManager.getAll().size());
        System.out.println("  Currently overdue      : " + issueManager.getOverdueRecords().size());
        System.out.printf("  Fines collected        : %.2f%n", issueManager.totalFinesCollected());
    }

    private void overdue() {
        System.out.println("\n--- OVERDUE BOOKS ---");
        ArrayList<IssueRecord> list = issueManager.getOverdueRecords();
        if (list.isEmpty()) {
            System.out.println("  Good news! No books are overdue.");
            return;
        }
        System.out.printf("%-6s %-24s %-18s %-11s %-6s %-8s%n",
                "IssID", "Book", "Member", "Due date", "Days", "Fine");
        System.out.println("-----------------------------------------------------------------------------");
        for (IssueRecord r : list) {
            Book b = bookManager.findById(r.getBookId());
            Member m = memberManager.findById(r.getMemberId());
            String bookName = (b != null) ? b.getTitle() : "(deleted)";
            String memberName = (m != null) ? m.getName() : "(deleted)";
            if (bookName.length() > 24) {
                bookName = bookName.substring(0, 22) + "..";
            }
            if (memberName.length() > 18) {
                memberName = memberName.substring(0, 16) + "..";
            }
            System.out.printf("%-6d %-24s %-18s %-11s %-6d %-8.2f%n",
                    r.getIssueId(), bookName, memberName, r.getDueDate(),
                    r.daysLate(), r.calculateFine());
        }
        System.out.println("\n  Total overdue books: " + list.size());
    }

    private void fines() {
        System.out.println("\n--- FINE REPORT ---");
        System.out.println("  Fine rate                   : " + Settings.FINE_PER_DAY + " per day");
        System.out.printf("  Fines from returned books   : %.2f%n", issueManager.totalFinesCollected());
        System.out.printf("  Fines pending (still late)  : %.2f%n", issueManager.totalPendingFines());

        // List returned records that had a fine
        System.out.println("\n  Returned books that had fines:");
        boolean found = false;
        for (IssueRecord r : issueManager.getAll()) {
            if (!r.isActive() && r.getFine() > 0) {
                if (!found) {
                    IssueRecord.printHeader();
                    found = true;
                }
                r.printRow();
            }
        }
        if (!found) {
            System.out.println("  (none)");
        }
    }

    private void popular() {
        System.out.println("\n--- MOST POPULAR BOOKS ---");
        if (bookManager.count() == 0) {
            System.out.println("  No books in the library yet.");
            return;
        }
        // Copy the list and sort by number of times issued (highest first)
        ArrayList<Book> sorted = new ArrayList<Book>(bookManager.getAll());
        Collections.sort(sorted, new Comparator<Book>() {
            public int compare(Book a, Book b) {
                return issueManager.timesIssued(b.getId())
                        - issueManager.timesIssued(a.getId());
            }
        });
        System.out.printf("%-5s %-32s %-20s %-8s%n", "Rank", "Title", "Author", "Issued");
        System.out.println("---------------------------------------------------------------");
        int rank = 1;
        for (Book b : sorted) {
            if (rank > 5) {
                break; // show only the top 5
            }
            int times = issueManager.timesIssued(b.getId());
            if (times == 0) {
                break;
            }
            System.out.printf("%-5d %-32s %-20s %-8d%n", rank, b.getTitle(),
                    b.getAuthor(), times);
            rank++;
        }
        if (rank == 1) {
            System.out.println("  No books have been issued yet.");
        }
    }

    private void unavailable() {
        System.out.println("\n--- BOOKS WITH NO COPIES AVAILABLE ---");
        boolean found = false;
        for (Book b : bookManager.getAll()) {
            if (!b.isAvailable()) {
                if (!found) {
                    Book.printHeader();
                    found = true;
                }
                b.printRow();
            }
        }
        if (!found) {
            System.out.println("  All books have at least one copy on the shelf.");
        }
    }

    private void membersWithBooks() {
        System.out.println("\n--- MEMBERS WHO CURRENTLY HOLD BOOKS ---");
        boolean found = false;
        for (Member m : memberManager.getAll()) {
            int count = issueManager.countActiveForMember(m.getId());
            if (count > 0) {
                if (!found) {
                    System.out.printf("%-5s %-25s %-13s %-6s%n", "ID", "Name", "Phone", "Books");
                    System.out.println("-----------------------------------------------------");
                    found = true;
                }
                System.out.printf("%-5d %-25s %-13s %-6d%n", m.getId(), m.getName(),
                        m.getPhone(), count);
            }
        }
        if (!found) {
            System.out.println("  No member is holding any book right now.");
        }
    }
}

/* ============================================================
 *  MAIN CLASS  (program starts here)
 * ============================================================ */
public class Librarymanagementsystem {

    // Ask for username and password. Allows 3 attempts.
    private static boolean login() {
        System.out.println("=============================================");
        System.out.println("        LIBRARY MANAGEMENT SYSTEM");
        System.out.println("=============================================");
        System.out.println("            Librarian Login");
        for (int attempt = 1; attempt <= 3; attempt++) {
            String user = InputHelper.readLine("Username: ");
            String pass = InputHelper.readLine("Password: ");
            if (user.equals(Settings.ADMIN_USER) && pass.equals(Settings.ADMIN_PASS)) {
                System.out.println("\nLogin successful. Welcome!");
                return true;
            }
            System.out.println("  ! Wrong username or password. Attempts left: " + (3 - attempt));
        }
        return false;
    }

    // Show a quick status line each time the main menu appears
    private static void showQuickStatus(BookManager books, MemberManager members,
                                        IssueManager issues) {
        System.out.println("  Today: " + LocalDate.now()
                + "  |  Books: " + books.count()
                + "  |  Members: " + members.count()
                + "  |  Overdue: " + issues.getOverdueRecords().size());
    }

    public static void main(String[] args) {
        FileHelper.createFolder();

        if (!login()) {
            System.out.println("Too many failed attempts. Program closed.");
            return;
        }

        // Create the managers (they load data from files)
        BookManager bookManager = new BookManager();
        MemberManager memberManager = new MemberManager();
        IssueManager issueManager = new IssueManager(bookManager, memberManager);

        // Create the menus
        BookMenu bookMenu = new BookMenu(bookManager, issueManager);
        MemberMenu memberMenu = new MemberMenu(memberManager, issueManager);
        IssueMenu issueMenu = new IssueMenu(bookManager, memberManager, issueManager);
        SearchMenu searchMenu = new SearchMenu(bookManager, memberManager);
        ReportMenu reportMenu = new ReportMenu(bookManager, memberManager, issueManager);

        // Main loop
        while (true) {
            System.out.println("\n=============================================");
            System.out.println("           MAIN MENU");
            System.out.println("=============================================");
            showQuickStatus(bookManager, memberManager, issueManager);
            System.out.println("---------------------------------------------");
            System.out.println(" 1. Book Management");
            System.out.println(" 2. Member Management");
            System.out.println(" 3. Issue / Return Books");
            System.out.println(" 4. Search");
            System.out.println(" 5. Reports");
            System.out.println(" 0. Exit");
            int choice = InputHelper.readIntInRange("Enter your choice: ", 0, 5);

            if (choice == 0) {
                System.out.println("\nAll data is saved. Thank you for using the system. Goodbye!");
                break;
            }
            switch (choice) {
                case 1: bookMenu.show(); break;
                case 2: memberMenu.show(); break;
                case 3: issueMenu.show(); break;
                case 4: searchMenu.show(); break;
                case 5: reportMenu.show(); break;
            }
        }
    }
}