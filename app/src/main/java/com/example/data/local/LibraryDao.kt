package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    // Books
    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE qrCode = :qrCode LIMIT 1")
    suspend fun getBookByQrCode(qrCode: String): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun getBookByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books WHERE qrCode LIKE '%' || :code || '%' OR isbn LIKE '%' || :code || '%' OR title LIKE '%' || :code || '%' LIMIT 1")
    suspend fun findBookFlexible(code: String): BookEntity?

    @Query("""
        SELECT * FROM books 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR isbn LIKE '%' || :query || '%')
        AND (:category = 'Tous' OR category = :category)
        AND (:availability = 'Tous' 
            OR (:availability = 'Disponible' AND availableCopies > 0)
            OR (:availability = 'Emprunté' AND availableCopies <= 0))
        ORDER BY 
            CASE WHEN :sortBy = 'title' THEN title END ASC,
            CASE WHEN :sortBy = 'author' THEN author END ASC,
            CASE WHEN :sortBy = 'year' THEN publishYear END DESC
    """)
    fun searchBooks(
        query: String,
        category: String,
        availability: String,
        sortBy: String
    ): Flow<List<BookEntity>>

    @Query("SELECT DISTINCT category FROM books ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    // Students
    @Query("SELECT * FROM students ORDER BY fullName ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): StudentEntity?

    @Query("SELECT * FROM students WHERE studentCardQr = :qrCode OR studentNumber = :qrCode LIMIT 1")
    suspend fun getStudentByQrCode(qrCode: String): StudentEntity?

    @Query("SELECT * FROM students WHERE studentCardQr LIKE '%' || :code || '%' OR studentNumber LIKE '%' || :code || '%' OR email LIKE '%' || :code || '%' LIMIT 1")
    suspend fun findStudentFlexible(code: String): StudentEntity?

    @Query("SELECT * FROM students WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getStudentByEmail(email: String): StudentEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBooksCount(): Int

    @Query("SELECT COUNT(*) FROM students")
    suspend fun getStudentsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity): Long

    // Loans
    @Query("SELECT * FROM loans ORDER BY loanDate DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE isReturned = 0 ORDER BY dueDate ASC")
    fun getActiveLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE isReturned = 0 AND dueDate < :currentTimestamp ORDER BY dueDate ASC")
    fun getOverdueLoans(currentTimestamp: Long): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE bookId = :bookId AND isReturned = 0 LIMIT 1")
    suspend fun getActiveLoanForBook(bookId: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun getLoanById(loanId: Long): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    // Email Logs
    @Query("SELECT * FROM email_logs ORDER BY sentTimestamp DESC")
    fun getAllEmailLogs(): Flow<List<EmailNotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmailLog(log: EmailNotificationLogEntity): Long

    // Session / Settings
    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
}
