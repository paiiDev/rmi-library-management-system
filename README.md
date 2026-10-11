# Distributed Library Management System (Java RMI)

A three-tier, distributed library management system built with **Java RMI**, **Java Swing**, and **Microsoft SQL Server**. Multiple clients connect remotely to a single server that owns the data and business logic, giving every logged-in user a consistent, real-time view of the library catalog.

| Layer | Technology |
|-------|-----------|
| Client (Presentation) | Java Swing + FlatLaf |
| Middleware (RMI) | Java Remote Method Invocation (RMI) |
| Data (Persistence) | Microsoft SQL Server (MSSQL, JDBC driver) |

---

## 1. Distributed Systems — The Big Picture

A **distributed system** is a collection of independent computers that appear to users as one coherent system. The computers communicate and coordinate their actions by passing **messages** over a network.

Key characteristics and how this project satisfies them:

| Characteristic | Meaning | This Project |
|----------------|---------|--------------|
| **Concurrency** | Multiple components run at the same time | Many clients can log in and act simultaneously |
| **No global clock** | There is no single "correct" time | Each computer keeps its own clock; ordering is managed at the database |
| **Independent failure** | One component can fail without stopping the whole system | A client can crash; the server and other clients keep running |
| **Heterogeneity** | Different hardware / OS can cooperate | Any machine that speaks Java RMI can join the system |
| **Distribution transparency** | Users should not "see" the network | Clients call `remote.borrowBook(...)` as if it were a local method |

### Why distribute at all?
- **Resource sharing** — many users share one catalog and one database.
- **Openness** — the system can grow by adding clients or even new servers.
- **Scalability** — load is spread: each client does its own GUI work, the server centralizes data access.
- **Reliability** — the database and server are the single source of truth.

### Three-tier (3-Tier) architecture used here

```
+------------------+       RMI over TCP       +------------------+      JDBC       +------------------+
|  Client Layer    | ------------------------> |  Business Layer  | -------------> |  Data Layer      |
|  Swing GUI       |   stubs & skeletons       |  RMI Server      |   SQL/JDBC      |  MSSQL LibraryDB |
+------------------+                           +------------------+                +------------------+
```

- **Tier 1 – Client**: Swing GUI. Stores only the currently logged-in user's ID and gives visual feedback.
- **Tier 2 – Server**: Owns all business logic. Is the only component that talks to the database.
- **Tier 3 – Database**: Stores users, books, and borrow transactions.

---

## 2. Java RMI — How the Middleware Works

**RMI (Remote Method Invocation)** lets a Java object running in one JVM invoke methods on an object living in another JVM, as if the call were local.

### The five core RMI concepts in this project

1. **Remote Interface** — a contract that both sides agree on.
   - `rmi.LibraryInterface extends Remote`. Every method declares `throws RemoteException`.
   - This is the *only* interface the client ever codes against.

2. **Remote Implementation (Server)** — implements the remote interface.
   - `rmi.LibraryServer extends UnicastRemoteObject implements LibraryInterface`.
   - Actually opens connections to SQL Server and performs the real work.

3. **Stub (client side) / Skeleton (server side)** — the "magic" that hides the network.
   - When the client calls a method, the **stub** serialises the arguments (marshalling) and sends them over TCP.
   - The **skeleton** receives them, calls the real method, and sends the result back.

4. **RMI Registry** — a name service (like a phone book) that maps a name to a remote object.
   - Server: `LocateRegistry.createRegistry(1099)` then `registry.rebind("LibraryServer", server)`.
   - Client: `LocateRegistry.getRegistry("localhost", 1099)` then `registry.lookup("LibraryServer")`.

5. **Serialisation & RemoteException** — data crosses the network.
   - Arguments and return values are transmitted by Java **serialisation** (here, mostly Strings — easy to parse on the GUI side).
   - Network is unreliable: every call can throw `RemoteException`, so the GUI wraps calls in try/catch and shows friendly errors.

```
   Client JVM                              Server JVM
+-------------+   RMI protocol (TCP)   +---------------+
|  GUI code   | ---------------------> |  Skeleton     |
|    |        |   marshalled call      |     |         |
|  Stub()     | ---------------------> |  remote obj   |
|  remote ←---| <---------------------- | LibraryServer|
+-------------+   serialised result    +---------------+
```

### How client and server find each other

```java
// SERVER (once at startup)
Registry registry = LocateRegistry.createRegistry(1099);      // start name service
registry.rebind("LibraryServer", new LibraryServer());        // publish object

// CLIENT (at login)
Registry registry = LocateRegistry.getRegistry("localhost", 1099);   // find name service
LibraryInterface remote = (LibraryInterface) registry.lookup("LibraryServer"); // resolve name
```

Now `remote.borrowBook(userId, bookId)` works exactly like a local method call — but it executes inside the server JVM and updates the shared database.

---

## 3. Remote API (`LibraryInterface`)

| Method | Returns | Description |
|--------|---------|-------------|
| `login(username, password)` | `"UserID\|Role"` or `null` | Authenticates against `Users` table |
| `viewAllBooks()` | pipe-delimited string | Full catalog: ID, Title, Author, Total, Available |
| `searchBooks(keyword)` | pipe-delimited string | Partial match on title or author |
| `checkAvailability(bookId)` | string | How many copies are currently in stock |
| `borrowBook(userId, bookId)` | success/error message | Atomically decrements `AvailableCopies` |
| `returnBook(userId, bookId)` | success/error message | Atomically increments `AvailableCopies` |
| `addBook(title, author, totalCopies)` | success/error message | Adds a book to the catalog (Admin) |
| `deleteBook(bookId)` | success/error message | Deletes a book if no active borrows (Admin) |
| `viewAllTransactions()` | pipe-delimited string | Full borrow/return audit history (Admin) |

> Data is transferred as pipe-delimited text so the Swing client can parse it directly into table models.
> Only the minimum data crosses the network — business objects stay server-side.

---

## 4. Concurrency Control (important for a distributed system)

Multiple clients can try to borrow the **last copy** of the same book at the same time. Without control, two requests could both read `AvailableCopies = 1` and both succeed — over-committing a copy that does not exist.

This project protects the critical section on **two layers**:

1. **`synchronized` methods** on the server — only one thread may execute `borrowBook`/`returnBook` at a time inside that JVM.
2. **Explicit JDBC transactions** — a second, stronger layer:
   - `SET AUTOCOMMIT OFF`
   - `UPDATE Books SET AvailableCopies = AvailableCopies - 1 WHERE BookID = ? AND AvailableCopies > 0`
   - `INSERT INTO Transactions ...`
   - Check affected row count; `COMMIT` on success or `ROLLBACK` on failure.
   - If the `UPDATE` matched 0 rows, there is no available copy and the borrow is rejected.

```java
public synchronized String borrowBook(int userId, int bookId) {
    // ... in a single DB transaction:
    // UPDATE Books SET AvailableCopies = AvailableCopies - 1
    // WHERE  BookID = ? AND AvailableCopies > 0
    // INSERT INTO Transactions (UserID, BookID, BorrowDate, Status) VALUES (?, ?, GETDATE(), 'Borrowed')
    // COMMIT only if the UPDATE affected exactly 1 row, else ROLLBACK and return "error"
}
```

The `AvailableCopies = AvailableCopies - 1 ... AND AvailableCopies > 0` pattern makes the check-and-decrement **atomic at the database level**, so even multiple server processes would stay correct.

---

## 5. Security Highlights

- **SQL injection protection** — every query uses `PreparedStatement` with bound parameters.
- **Resource safety** — `try-with-resources` guarantees that Connections, Statements and ResultSets are always closed.
- **Role separation** — Admin operations (`addBook`, `deleteBook`, `viewAllTransactions`) are not exposed in the user dashboard UI.
- **Plain-text password note** — passwords are stored in plain text for demo purposes only; a production system must store salted hashes.

---

## 6. Project Structure

```
LibraryManagementRMI - GUI/
├── pom.xml                                  # Maven build config (Java 26)
├── README.md
└── src/main/
    ├── java/
    │   ├── rmi/
    │   │   ├── LibraryInterface.java        # Remote contract (client's view)
    │   │   └── LibraryServer.java           # RMI server + DB logic + concurrency
    │   └── gui/
    │       └── LibraryClientGUI.java        # Swing login + user/admin dashboards
    └── resources/
        ├── sql/LibraryDB.sql                # Schema + seed data (MSSQL)
        └── icons/*.svg                      # FlatLaf SVG icons for the GUI
```

### Database schema

- **Users** (`UserID`, `Username`, `Password`, `Role`) — `Role` is `'Admin'` or `'User'`.
- **Books** (`BookID`, `Title`, `Author`, `TotalCopies`, `AvailableCopies`).
- **Transactions** (`TransactionID`, `UserID`, `BookID`, `BorrowDate`, `ReturnDate`, `Status`) — FK to `Users` and `Books`, `Status` is `'Borrowed'`/`'Returned'`.

Seed accounts:

| Username | Password | Role  |
|----------|----------|-------|
| `admin`  | `admin123` | Admin |
| `user`   | `user123`  | User  |

---

## 7. How to Run

### Step 1 — Create the database
Open SQL Server Management Studio, run `src/main/resources/sql/LibraryDB.sql` (creates and seeds `LibraryDB`).

### Step 2 — Check DB credentials
`LibraryServer.java` connects with:
```
URL:      jdbc:sqlserver://localhost:1433;databaseName=LibraryDB;encrypt=true;trustServerCertificate=true
User:     sa
Password: sasa@123
```
Adjust `DB_USER` / `DB_PASS` if your SQL Server uses different credentials.

### Step 3 — Start the RMI server
Run `rmi.LibraryServer` (IntelliJ IDEA / IDE). Expected output:
```
Library RMI Server is running...
Bound to: //localhost:1099/LibraryServer
Waiting for client connections...
```

### Step 4 — Start one or more clients
Run `gui.LibraryClientGUI` as many times as you like. Log in with:
- `admin / admin123` — Admin dashboard (add/delete books, view transactions, stat cards).
- `user / user123` — User dashboard (browse, search, borrow, return).

Because the system is **distributed**, you can open two clients and watch both update **in real time** — every client polls the server automatically every 3 seconds, so when the user borrows a book, the admin's screen (and any other open client) shows the new availability without touching any button.

---

## 8. GUI Overview

- **Login screen** — split layout: branded gradient panel on the left, sign-in form on the right.
- **User dashboard** — All Books table, live book search, borrow & return forms.
- **Admin dashboard** — All Books with stat cards (Total / Available / Currently Borrowed), Add Book, Delete Book, Transactions.
- **Real-time refresh** — every client auto-polls the server every 3 seconds (plus refresh on navigation and after each action), so catalog availability stays consistent across all connected clients. When one client borrows or returns a book, the other clients see the change within seconds.
- **Per-account identity** — the real `UserID` returned by `login()` is used for every borrow/return, so transaction history is attributed to the correct user.
- **Sidebar** — role-aware navigation: users never see admin actions and vice versa.

---

## 9. Presentation Talking Points & Live Demo Script

1. **Motivation** — "One library, many users, one truth." Show why we need a shared, server-owned catalog instead of each client holding its own data.
2. **Distributed concepts** — reference the table in §1 (concurrency, transparency, independent failures).
3. **RMI mechanism** — draw the stub/skeleton diagram in §2 and point out the registry `//localhost:1099/LibraryServer`.
4. **Run the server** — start one `LibraryServer`, show the console output.
5. **Run two clients** — log in as `user` in both windows.
6. **Prove distribution** — Client A borrows book 1; Client B (admin or user) sees `AvailableCopies` drop **automatically within ~3 seconds** (client-polling), no refresh button needed. Client A returns it; Client B sees it restored. This demonstrates a *shared* state changed by a *remote* client.
7. **Prove role separation** — Client C logs in as `admin` and sees the stat cards + Transactions and can Add/Delete books; the user clients never see these.
8. **Prove concurrency** — try to borrow the last available copy from two clients at once; only one succeeds (thanks to `synchronized` + the atomic JDBC `UPDATE ... AND AvailableCopies > 0`).
9. **Wrap up** — recap the 3-tier architecture and the two-layer concurrency safety.

---

## 10. Possible Extensions

- Password hashing (BCrypt / PBKDF2) instead of plain text.
- Distributed locking across multiple server instances (e.g., database `UPDLOCK` hints already used; add distributed cache).
- Server push via RMI callbacks (true real-time, replacing the 3-second client-polling).
- Per-user "My borrowed books" view.
- Audit logging, overdue-return notifications.