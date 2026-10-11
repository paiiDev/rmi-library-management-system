-- ============================================================
-- LibraryDB - Complete Database Schema for MSSQL
-- Distributed Library Management System (RMI)
-- ============================================================

-- Drop existing tables if they exist (clean setup)
IF OBJECT_ID('dbo.Transactions', 'U') IS NOT NULL DROP TABLE dbo.Transactions;
IF OBJECT_ID('dbo.Books', 'U') IS NOT NULL DROP TABLE dbo.Books;
IF OBJECT_ID('dbo.Users', 'U') IS NOT NULL DROP TABLE dbo.Users;
GO

-- Create the database
IF DB_ID('LibraryDB') IS NULL
    CREATE DATABASE LibraryDB;
GO

USE LibraryDB;
GO

-- ============================================================
-- 1. Users Table
-- ============================================================
CREATE TABLE Users (
    UserID      INT IDENTITY(1,1) PRIMARY KEY,
    Username    NVARCHAR(50)  NOT NULL UNIQUE,
    Password    NVARCHAR(256) NOT NULL,
    Role        NVARCHAR(10)  NOT NULL CHECK (Role IN ('Admin', 'User'))
);
GO

-- ============================================================
-- 2. Books Table
-- ============================================================
-- AvailableCopies tracks how many copies are currently in stock.
-- TotalCopies is the maximum copies the library owns.
CREATE TABLE Books (
    BookID          INT IDENTITY(1,1) PRIMARY KEY,
    Title           NVARCHAR(200) NOT NULL,
    Author          NVARCHAR(200) NOT NULL,
    TotalCopies     INT           NOT NULL CHECK (TotalCopies > 0),
    AvailableCopies INT           NOT NULL CHECK (AvailableCopies >= 0)
);
GO

-- ============================================================
-- 3. Transactions Table
-- ============================================================
-- Status: 'Borrowed' or 'Returned'
CREATE TABLE Transactions (
    TransactionID INT IDENTITY(1,1) PRIMARY KEY,
    UserID        INT           NOT NULL,
    BookID        INT           NOT NULL,
    BorrowDate    DATETIME2     NOT NULL DEFAULT GETDATE(),
    ReturnDate    DATETIME2     NULL,
    Status        NVARCHAR(20)  NOT NULL CHECK (Status IN ('Borrowed', 'Returned')),
    FOREIGN KEY (UserID) REFERENCES Users(UserID),
    FOREIGN KEY (BookID) REFERENCES Books(BookID)
);
GO

-- ============================================================
-- Seed Data: Admin & User accounts
-- Password stored in plain-text for demo; production should hash.
-- ============================================================
INSERT INTO Users (Username, Password, Role) VALUES
    ('admin', 'admin123', 'Admin'),
    ('user',  'user123',  'User');
GO

-- Seed Data: Sample Books
INSERT INTO Books (Title, Author, TotalCopies, AvailableCopies) VALUES
    ('Effective Java',          'Joshua Bloch',         5, 5),
    ('Design Patterns',         'Gang of Four',         3, 3),
    ('Clean Code',              'Robert C. Martin',     4, 4),
    ('Head First Java',         'Kathy Sierra',         6, 6),
    ('Java Concurrency in Practice', 'Brian Goetz',    3, 3),
    ('The Pragmatic Programmer', 'Andrew Hunt',         2, 2),
    ('Refactoring',             'Martin Fowler',         3, 3),
    ('Domain-Driven Design',    'Eric Evans',            2, 2),
    ('Thinking in Java',        'Bruce Eckel',           4, 4),
    ('Java RMI: The Definitive Guide', 'David Flanagan', 2, 2);
GO

PRINT 'LibraryDB created and seeded successfully.';
GO
