package rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;

/**
 * RMI Server implementing LibraryInterface.
 *
 * Concurrency Control:
 *   The borrowBook() and returnBook() methods use the 'synchronized' keyword
 *   so that only one thread can execute the critical section at a time.  Inside
 *   the critical section we also use explicit JDBC transaction boundaries
 *   (SET AUTOCOMMIT OFF / COMMIT / ROLLBACK) to provide a second layer of
 *   safety and to ensure atomicity of the read-then-write pattern that
 *   otherwise leads to race conditions on AvailableCopies.
 *
 * Security:
 *   - Every query uses PreparedStatement to prevent SQL injection.
 *   - try-with-resources guarantees Connection / Statement / ResultSet closure.
 */
public class LibraryServer extends UnicastRemoteObject implements LibraryInterface {

    private static final String DB_URL  = "jdbc:sqlserver://localhost:1433;databaseName=LibraryDB;encrypt=true;trustServerCertificate=true;";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "sasa@123";

    public LibraryServer() throws RemoteException {
        super();
    }

    // ------------------------------------------------------------------
    // JDBC helper – loads driver once, returns a fresh connection each time.
    // ------------------------------------------------------------------
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MSSQL JDBC driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // ==================================================================
    //  AUTHENTICATION
    // ==================================================================

    @Override
    public String login(String username, String password) throws RemoteException {
        String sql = "SELECT UserID, Role FROM Users WHERE Username = ? AND Password = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("UserID") + "|" + rs.getString("Role");   // "UserID|Role"
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return null;   // no match
    }

    // ==================================================================
    //  USER OPERATIONS
    // ==================================================================

    @Override
    public String viewAllBooks() throws RemoteException {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT BookID, Title, Author, TotalCopies, AvailableCopies FROM Books";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sb.append(rs.getInt("BookID"))
                  .append("|")
                  .append(rs.getString("Title"))
                  .append("|")
                  .append(rs.getString("Author"))
                  .append("|")
                  .append(rs.getInt("TotalCopies"))
                  .append("|")
                  .append(rs.getInt("AvailableCopies"))
                  .append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return sb.length() > 0 ? sb.toString() : "EMPTY";
    }

    @Override
    public String searchBooks(String keyword) throws RemoteException {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT BookID, Title, Author, TotalCopies, AvailableCopies "
                   + "FROM Books WHERE Title LIKE ? OR Author LIKE ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, "%" + keyword + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getInt("BookID"))
                      .append("|")
                      .append(rs.getString("Title"))
                      .append("|")
                      .append(rs.getString("Author"))
                      .append("|")
                      .append(rs.getInt("TotalCopies"))
                      .append("|")
                      .append(rs.getInt("AvailableCopies"))
                      .append("\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return sb.length() > 0 ? sb.toString() : "EMPTY";
    }

    @Override
    public String checkAvailability(int bookId) throws RemoteException {
        String sql = "SELECT Title, TotalCopies, AvailableCopies FROM Books WHERE BookID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bookId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("Title");
                    int total = rs.getInt("TotalCopies");
                    int avail = rs.getInt("AvailableCopies");
                    return "Book: " + title + " | Available: " + avail + " / " + total;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return "Book not found.";
    }

    @Override
    public String viewUserBorrowedBooks(int userId) throws RemoteException {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT T.TransactionID, B.BookID, B.Title, T.BorrowDate "
                   + "FROM Transactions T "
                   + "INNER JOIN Books B ON T.BookID = B.BookID "
                   + "WHERE T.UserID = ? AND T.Status = 'Borrowed' "
                   + "ORDER BY T.TransactionID DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getInt("TransactionID"))
                      .append("|")
                      .append(rs.getInt("BookID"))
                      .append("|")
                      .append(rs.getString("Title"))
                      .append("|")
                      .append(rs.getTimestamp("BorrowDate"))
                      .append("\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return sb.length() > 0 ? sb.toString() : "EMPTY";
    }


    @Override
    public synchronized String borrowBook(int userId, int bookId) throws RemoteException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);   // begin transaction

            String selectSql = "SELECT AvailableCopies, Title FROM Books WITH (UPDLOCK, ROWLOCK) WHERE BookID = ?";
            int availableCopies = -1;
            String bookTitle = "";

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        availableCopies = rs.getInt("AvailableCopies");
                        bookTitle = rs.getString("Title");
                    } else {
                        conn.rollback();
                        return "Book not found.";
                    }
                }
            }

            if (availableCopies <= 0) {
                conn.rollback();
                return "No copies of '" + bookTitle + "' available for borrowing.";
            }

            String checkSql = "SELECT TransactionID FROM Transactions "
                            + "WHERE UserID = ? AND BookID = ? AND Status = 'Borrowed'";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        conn.rollback();
                        return "You have already borrowed '" + bookTitle + "'. Return it first.";
                    }
                }
            }

            String updateBook = "UPDATE Books SET AvailableCopies = AvailableCopies - 1 WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateBook)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }

            String insertTx = "INSERT INTO Transactions (UserID, BookID, BorrowDate, Status) "
                            + "VALUES (?, ?, GETDATE(), 'Borrowed')";
            try (PreparedStatement ps = conn.prepareStatement(insertTx)) {
                ps.setInt(1, userId);
                ps.setInt(2, bookId);
                ps.executeUpdate();
            }

            conn.commit();
            return "Successfully borrowed '" + bookTitle + "'. Return within 14 days.";

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }



    @Override
    public synchronized String returnBook(int userId, int bookId) throws RemoteException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // 1. Find active borrow transaction
            String findTx = "SELECT TransactionID FROM Transactions "
                          + "WHERE UserID = ? AND BookID = ? AND Status = 'Borrowed'";
            int transactionId = -1;

            try (PreparedStatement ps = conn.prepareStatement(findTx)) {
                ps.setInt(1, userId);
                ps.setInt(2, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        transactionId = rs.getInt("TransactionID");
                    } else {
                        conn.rollback();
                        return "No active borrow found for this book.";
                    }
                }
            }

            // 2. Update transaction status
            String updateTx = "UPDATE Transactions SET ReturnDate = GETDATE(), Status = 'Returned' "
                            + "WHERE TransactionID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateTx)) {
                ps.setInt(1, transactionId);
                ps.executeUpdate();
            }

            // 3. Increment AvailableCopies
            String updateBook = "UPDATE Books SET AvailableCopies = AvailableCopies + 1 WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateBook)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }

            conn.commit();
            return "Book returned successfully.";

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }



    @Override
    public String addBook(String title, String author, int totalCopies) throws RemoteException {
        String sql = "INSERT INTO Books (Title, Author, TotalCopies, AvailableCopies) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, author);
            ps.setInt(3, totalCopies);
            ps.setInt(4, totalCopies);   // initially all copies available

            int rows = ps.executeUpdate();
            return rows > 0
                ? "Book '" + title + "' added successfully (" + totalCopies + " copies)."
                : "Failed to add book.";

        } catch (SQLException e) {
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        }
    }

    @Override
    public synchronized String deleteBook(int bookId) throws RemoteException {

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            String checkSql = "SELECT COUNT(*) FROM Transactions WHERE BookID = ? AND Status = 'Borrowed'";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return "Cannot delete: this book has active borrows. Wait for returns.";
                    }
                }
            }

            String deleteTx = "DELETE FROM Transactions WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteTx)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }

            String deleteBook = "DELETE FROM Books WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteBook)) {
                ps.setInt(1, bookId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    conn.commit();
                    return "Book deleted successfully.";
                } else {
                    conn.rollback();
                    return "Book not found.";
                }
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public synchronized String editBook(int bookId, String title, String author, int totalCopies) throws RemoteException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            String selectSql = "SELECT AvailableCopies, TotalCopies FROM Books WHERE BookID = ?";
            int availableCopies = -1;
            int currentTotal = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "Book not found.";
                    }
                    availableCopies = rs.getInt("AvailableCopies");
                    currentTotal = rs.getInt("TotalCopies");
                }
            }

            int currentlyBorrowed = currentTotal - availableCopies;
            if (totalCopies < currentlyBorrowed) {
                conn.rollback();
                return "Cannot set total to " + totalCopies + ". "
                     + currentlyBorrowed + " copy/copies are currently borrowed.";
            }

            int newAvailable = availableCopies + (totalCopies - currentTotal);

            String updateSql = "UPDATE Books SET Title = ?, Author = ?, TotalCopies = ?, AvailableCopies = ? WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, title);
                ps.setString(2, author);
                ps.setInt(3, totalCopies);
                ps.setInt(4, newAvailable);
                ps.setInt(5, bookId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    conn.commit();
                    return "Book updated successfully.";
                } else {
                    conn.rollback();
                    return "Failed to update book.";
                }
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public String viewAllTransactions() throws RemoteException {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT T.TransactionID, U.Username, B.Title, T.BorrowDate, T.ReturnDate, T.Status "
                   + "FROM Transactions T "
                   + "INNER JOIN Users U ON T.UserID = U.UserID "
                   + "INNER JOIN Books B ON T.BookID = B.BookID "
                   + "ORDER BY T.TransactionID DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sb.append(rs.getInt("TransactionID"))
                  .append("|")
                  .append(rs.getString("Username"))
                  .append("|")
                  .append(rs.getString("Title"))
                  .append("|")
                  .append(rs.getTimestamp("BorrowDate"))
                  .append("|")
                  .append(rs.getTimestamp("ReturnDate") != null ? rs.getTimestamp("ReturnDate") : "N/A")
                  .append("|")
                  .append(rs.getString("Status"))
                  .append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
        return sb.length() > 0 ? sb.toString() : "EMPTY";
    }



    public static void main(String[] args) {
        try {
            LibraryServer server = new LibraryServer();

            // Create RMI registry on port 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("LibraryServer", server);

            System.out.println("===========================================");
            System.out.println("  Library RMI Server is running...");
            System.out.println("  Bound to: //localhost:1099/LibraryServer");
            System.out.println("  Waiting for client connections...");
            System.out.println("===========================================");

        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
