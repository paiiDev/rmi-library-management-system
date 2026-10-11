package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface LibraryInterface extends Remote {

    String login(String username, String password) throws RemoteException;
    String viewAllBooks() throws RemoteException;
    String searchBooks(String keyword) throws RemoteException;
    String checkAvailability(int bookId) throws RemoteException;
    String viewUserBorrowedBooks(int userId) throws RemoteException;
    String borrowBook(int userId, int bookId) throws RemoteException;
    String returnBook(int userId, int bookId) throws RemoteException;
    String addBook(String title, String author, int totalCopies) throws RemoteException;
    String deleteBook(int bookId) throws RemoteException;
    String editBook(int bookId, String title, String author, int totalCopies) throws RemoteException;
    String viewAllTransactions() throws RemoteException;
}
