import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

class LibraryException extends Exception {
    public LibraryException(String message) {
        super(message);
    }
}

class BookNotFoundException extends LibraryException {
    public BookNotFoundException(String isbn) {
        super("No book found with ISBN: " + isbn);
    }
}

class BookUnavailableException extends LibraryException {
    public BookUnavailableException(String title) {
        super("\"" + title + "\" is currently checked out.");
    }
}

class AlreadyCheckedOutException extends LibraryException {
    public AlreadyCheckedOutException(String title) {
        super("\"" + title + "\" is already checked out and cannot be checked out again.");
    }
}

class MaxRenewalsReachedException extends LibraryException {
    public MaxRenewalsReachedException(String title, int max) {
        super("\"" + title + "\" has already been renewed " + max + " time(s). Maximum reached.");
    }
}

class ReservationException extends LibraryException {
    public ReservationException(String message) {
        super(message);
    }
}

class InvalidPasswordException extends LibraryException {
    public InvalidPasswordException() {
        super("Incorrect password.");
    }
}

interface Manageable<T> {
    void addBook(T item) throws LibraryException;
    void deleteBook(String isbn) throws LibraryException;
}

class Book implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String title;
    private String author;
    private String publisher;
    private String isbn;
    private boolean available;
    private int renewalCount;
    private ArrayList<String> reservations;

    public Book(String title, String author, String publisher, String isbn) {
        this.title        = title;
        this.author       = author;
        this.publisher    = publisher;
        this.isbn         = isbn;
        this.available    = true;
        this.renewalCount = 0;
        this.reservations = new ArrayList<>();
    }

    public String  getTitle()        { return title; }
    public String  getAuthor()       { return author; }
    public String  getPublisher()    { return publisher; }
    public String  getIsbn()         { return isbn; }
    public boolean isAvailable()     { return available; }
    public int     getRenewalCount() { return renewalCount; }

    public ArrayList<String> getReservations() {
        return new ArrayList<>(reservations);
    }

    public void setAvailable(boolean available) {
        this.available = available;
        if (available) renewalCount = 0;
    }

    public void incrementRenewal() { renewalCount++; }

    public void addReservation(String patronName) {
        reservations.add(patronName);
    }

    public String removeNextReservation() {
        if (reservations.isEmpty()) return null;
        return reservations.remove(0);
    }

    public boolean hasReservations() {
        return !reservations.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
            "%-22s %-20s %-18s %-16s %-10s Renewals: %d  Queue: %d",
            title, author, publisher, isbn,
            available ? "Yes" : "No",
            renewalCount,
            reservations.size()
        );
    }
}

class Library implements Manageable<Book> {
    private final ArrayList<Book> books        = new ArrayList<>();
    private static final int      MAX_RENEWALS = 3;
    private static final String   DATA_FILE    = "library_data.ser";

    @Override
    public void addBook(Book book) throws LibraryException {
        for (Book b : books) {
            if (b.getIsbn().equalsIgnoreCase(book.getIsbn())) {
                throw new LibraryException(
                    "A book with ISBN " + book.getIsbn() + " already exists.");
            }
        }
        books.add(book);
        saveToFile();
        System.out.println("\tBook added successfully!");
    }

    @Override
    public void deleteBook(String isbn) throws LibraryException {
        Book target = findBook(isbn);
        books.remove(target);
        saveToFile();
        System.out.println("\tBook deleted successfully.");
    }

    public Book searchBook(String isbn) throws LibraryException {
        return findBook(isbn);
    }

    public void checkOut(String isbn) throws LibraryException {
        Book book = findBook(isbn);
        if (!book.isAvailable()) {
            throw new AlreadyCheckedOutException(book.getTitle());
        }
        book.setAvailable(false);
        saveToFile();
        System.out.println("\tBook checked out successfully.");
    }

    public void returnBook(String isbn) throws LibraryException {
        Book book = findBook(isbn);
        if (book.isAvailable()) {
            throw new BookUnavailableException(book.getTitle());
        }
        book.setAvailable(true);
        saveToFile();
        System.out.println("\tBook returned successfully.");

        String nextPatron = book.removeNextReservation();
        if (nextPatron != null) {
            System.out.println("\tNOTICE: \"" + book.getTitle() +
                "\" is now available for reserved patron: " + nextPatron);
            saveToFile();
        }
    }

    public void reserveBook(String isbn, String patronName) throws LibraryException {
        Book book = findBook(isbn);
        if (book.isAvailable()) {
            throw new ReservationException(
                "\"" + book.getTitle() + "\" is currently available — " +
                "just check it out directly.");
        }
        ArrayList<String> queue = book.getReservations();
        for (String existing : queue) {
            if (existing.equalsIgnoreCase(patronName)) {
                throw new ReservationException(
                    patronName + " already has a reservation for \"" +
                    book.getTitle() + "\".");
            }
        }
        book.addReservation(patronName);
        saveToFile();
        System.out.println("\tReservation confirmed for " + patronName +
            ". Position in queue: " + book.getReservations().size());
    }

    public void renewBook(String isbn) throws LibraryException {
        Book book = findBook(isbn);
        if (book.isAvailable()) {
            throw new LibraryException(
                "\"" + book.getTitle() + "\" is not checked out. Nothing to renew.");
        }
        if (book.getRenewalCount() >= MAX_RENEWALS) {
            throw new MaxRenewalsReachedException(book.getTitle(), MAX_RENEWALS);
        }
        book.incrementRenewal();
        saveToFile();
        System.out.println("\tBook renewed. Renewals used: " +
            book.getRenewalCount() + " / " + MAX_RENEWALS);
    }

    public void displayInventory() {
        if (books.isEmpty()) {
            System.out.println("\tNo books in inventory.");
            return;
        }
        System.out.printf("%n\t%-22s %-20s %-18s %-16s %-10s %-10s %-6s%n",
            "Title", "Author", "Publisher", "ISBN", "Available", "Renewals", "Queue");
        System.out.println("\t" + "-".repeat(106));
        for (Book b : books) {
            System.out.println("\t" + b);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadFromFile() {
        File f = new File(DATA_FILE);
        if (!f.exists()) {
            System.out.println("\tNo saved data found. Starting fresh.");
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            ArrayList<Book> loaded = (ArrayList<Book>) ois.readObject();
            books.clear();
            books.addAll(loaded);
            System.out.println("\tLoaded " + books.size() + " book(s) from file.");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("\tCould not load data: " + e.getMessage());
        }
    }

    public void saveToFile() {
        try (ObjectOutputStream oos =
                new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(books);
        } catch (IOException e) {
            System.out.println("\tCould not save data: " + e.getMessage());
        }
    }

    private Book findBook(String isbn) throws BookNotFoundException {
        for (Book b : books) {
            if (b.getIsbn().equalsIgnoreCase(isbn.trim())) return b;
        }
        throw new BookNotFoundException(isbn);
    }
}

public class LibrarySystem {
    private static final String  ADMIN_PASSWORD = "admin";
    private static final int     MAX_ATTEMPTS   = 3;
    private static final Scanner scanner        = new Scanner(System.in);
    private static final Library lib            = new Library();

    public static void main(String[] args) {
        lib.loadFromFile();
        heading();
        login();

        int choice;
        do {
            mainMenu();
            choice = readInt();
            try {
                switch (choice) {
                    case 1: addBook(); break;
                    case 2: searchBook(); break;
                    case 3: checkOut(); break;
                    case 4: returnBook(); break;
                    case 5: reserve(); break;
                    case 6: renew(); break;
                    case 7: manageInventory(); break;
                    case 8: System.out.println("\n\tGoodbye!\n"); break;
                    default: System.out.println("\n\tInvalid choice. Try again."); break;
                }
            } catch (LibraryException e) {
                System.out.println("\n\t[ERROR] " + e.getMessage());
            }
            if (choice != 8) pause();
        } while (choice != 8);
    }

    private static void login() {
        System.out.println("\tLOGIN");
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            System.out.print("\n\tEnter password: ");
            String input = scanner.nextLine().trim();
            if (input.equals(ADMIN_PASSWORD)) {
                System.out.println("\tAccess granted. Press Enter to continue...");
                scanner.nextLine();
                return;
            }
            attempts++;
            int remaining = MAX_ATTEMPTS - attempts;
            if (remaining > 0) {
                System.out.println("\tWrong password. " + remaining + " attempt(s) left.");
            }
        }
        System.out.println("\n\tAccess denied. Exiting.");
        System.exit(0);
    }

    private static void addBook() throws LibraryException {
        String title     = readRequired("\n\tTitle     : ");
        String author    = readRequired("\tAuthor    : ");
        String publisher = readRequired("\tPublisher : ");
        String isbn      = readRequired("\tISBN      : ");
        lib.addBook(new Book(title, author, publisher, isbn));
    }

    private static void searchBook() throws LibraryException {
        Book b = lib.searchBook(readRequired("\n\tEnter ISBN: "));
        System.out.println("\n\tBook found:");
        System.out.println("\tTitle        : " + b.getTitle());
        System.out.println("\tAuthor       : " + b.getAuthor());
        System.out.println("\tPublisher    : " + b.getPublisher());
        System.out.println("\tISBN         : " + b.getIsbn());
        System.out.println("\tAvailable    : " + (b.isAvailable() ? "Yes" : "No"));
        System.out.println("\tRenewals     : " + b.getRenewalCount() + " / 3");
        System.out.println("\tQueue size   : " + b.getReservations().size());
    }

    private static void checkOut() throws LibraryException {
        lib.checkOut(readRequired("\n\tEnter ISBN to check out: "));
    }

    private static void returnBook() throws LibraryException {
        lib.returnBook(readRequired("\n\tEnter ISBN to return: "));
    }

    private static void reserve() throws LibraryException {
        String isbn   = readRequired("\n\tEnter ISBN to reserve: ");
        String patron = readRequired("\tYour name   : ");
        lib.reserveBook(isbn, patron);
    }

    private static void renew() throws LibraryException {
        lib.renewBook(readRequired("\n\tEnter ISBN to renew: "));
    }

    private static void manageInventory() throws LibraryException {
        System.out.println("\n\t1. View inventory");
        System.out.println("\t2. Delete a book");
        System.out.println("\t3. Back");
        System.out.print("\n\tChoice: ");
        int c = readInt();
        switch (c) {
            case 1: lib.displayInventory(); break;
            case 2:
                lib.deleteBook(readRequired("\n\tEnter ISBN to delete: "));
                break;
            case 3: break;
            default: System.out.println("\tInvalid choice."); break;
        }
    }

    private static void heading() {
        System.out.println("\n\t<== LIBRARY BOOK MANAGEMENT SYSTEM ==>\n");
    }

    private static void mainMenu() {
        System.out.println("\n\t── MAIN MENU ──");
        System.out.println("\t1. Add a book");
        System.out.println("\t2. Search for a book");
        System.out.println("\t3. Check out a book");
        System.out.println("\t4. Return a book");
        System.out.println("\t5. Reserve a book");
        System.out.println("\t6. Renew a book");
        System.out.println("\t7. Manage inventory");
        System.out.println("\t8. Exit");
        System.out.print("\n\tChoice: ");
    }

    private static int readInt() {
        try {
            int n = Integer.parseInt(scanner.nextLine().trim());
            return n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readRequired(String prompt) {
        String value;
        do {
            System.out.print(prompt);
            value = scanner.nextLine().trim();
            if (value.isEmpty()) System.out.println("\tInput cannot be blank.");
        } while (value.isEmpty());
        return value;
    }

    private static void pause() {
        System.out.print("\n\tPress Enter to continue...");
        scanner.nextLine();
    }
}
