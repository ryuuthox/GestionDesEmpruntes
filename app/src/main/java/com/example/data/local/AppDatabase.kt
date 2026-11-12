package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookEntity::class,
        StudentEntity::class,
        LoanEntity::class,
        EmailNotificationLogEntity::class,
        AppSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "biblio_emprunts_usmba_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database.libraryDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val count = database.libraryDao().getBooksCount()
                    if (count == 0) {
                        populateInitialData(database.libraryDao())
                    }
                }
            }
        }

        companion object {
            suspend fun populateInitialData(dao: LibraryDao) {
            val now = System.currentTimeMillis()
            val dayInMillis = 86400000L

            // ==========================================
            // MOCK DATA : ÉTUDIANTS USMBA (FÈS)
            // ==========================================
            val students = listOf(
                StudentEntity(
                    id = 1,
                    studentCardQr = "STUDENT-USMBA-2024-001",
                    studentNumber = "USMBA-2024-001",
                    fullName = "Sophie Martin",
                    email = "sophie.martin@usmba.ac.ma",
                    department = "FSDM Fès - Informatique & IA",
                    phone = "06 12 34 56 78"
                ),
                StudentEntity(
                    id = 2,
                    studentCardQr = "STUDENT-USMBA-2024-002",
                    studentNumber = "USMBA-2024-002",
                    fullName = "Lucas Bernard",
                    email = "lucas.bernard@usmba.ac.ma",
                    department = "FST Fès - Sciences & Technologies",
                    phone = "06 98 76 54 32"
                ),
                StudentEntity(
                    id = 3,
                    studentCardQr = "STUDENT-USMBA-2024-003",
                    studentNumber = "USMBA-2024-003",
                    fullName = "Amina Diallo",
                    email = "amina.diallo@usmba.ac.ma",
                    department = "FLSH Dhar El Mehraz - Lettres & Langues",
                    phone = "06 55 44 33 22"
                ),
                StudentEntity(
                    id = 4,
                    studentCardQr = "STUDENT-USMBA-2024-004",
                    studentNumber = "USMBA-2024-004",
                    fullName = "Thomas Lefebvre",
                    email = "thomas.lefebvre@usmba.ac.ma",
                    department = "FSJES Fès - Droit & Sciences Politiques",
                    phone = "06 11 22 33 44"
                ),
                StudentEntity(
                    id = 5,
                    studentCardQr = "STUDENT-USMBA-2024-005",
                    studentNumber = "USMBA-2024-005",
                    fullName = "Yassine El Amrani",
                    email = "yassine.elamrani@usmba.ac.ma",
                    department = "ENSA Fès - Génie Logiciel",
                    phone = "06 77 88 99 00"
                ),
                StudentEntity(
                    id = 6,
                    studentCardQr = "STUDENT-USMBA-2024-006",
                    studentNumber = "USMBA-2024-006",
                    fullName = "Fatima-Zahra Berrada",
                    email = "fatimazahra.berrada@usmba.ac.ma",
                    department = "FMP Fès - Faculté de Médecine & Pharmacie",
                    phone = "06 44 33 22 11"
                ),
                StudentEntity(
                    id = 7,
                    studentCardQr = "STUDENT-USMBA-2024-007",
                    studentNumber = "USMBA-2024-007",
                    fullName = "Mehdi Tazi",
                    email = "mehdi.tazi@usmba.ac.ma",
                    department = "ENCG Fès - Management & Commerce",
                    phone = "06 66 55 44 33"
                ),
                StudentEntity(
                    id = 8,
                    studentCardQr = "STUDENT-ENTTAIM-8",
                    studentNumber = "USMBA-2024-008",
                    fullName = "Enttaim Test",
                    email = "enttaim8@gmail.com",
                    department = "FSDM Fès - Informatique",
                    phone = "06 00 00 00 01"
                ),
                StudentEntity(
                    id = 9,
                    studentCardQr = "STUDENT-MUSIXA-9",
                    studentNumber = "USMBA-2024-009",
                    fullName = "Musixa Test",
                    email = "musixa360@gmail.com",
                    department = "FST Fès - Génie Logiciel",
                    phone = "06 00 00 00 02"
                ),
                StudentEntity(
                    id = 10,
                    studentCardQr = "STUDENT-USMBA-2024-010",
                    studentNumber = "USMBA-2024-010",
                    fullName = "Adam Abderrahmane Rahmaoui",
                    email = "adamabderrahmane.rahmaoui@usmba.ac.ma",
                    department = "FSDM Fès - Informatique & IA",
                    phone = "06 12 00 00 10"
                )
            )
            dao.insertStudents(students)

            // ==========================================
            // MOCK DATA : CATALOGUE D'OUVRAGES
            // ==========================================
            val books = listOf(
                BookEntity(
                    id = 1,
                    qrCode = "BOOK-978-01001",
                    title = "Conception et Architecture Logicielle",
                    author = "Alain Dupont",
                    isbn = "978-2100100011",
                    category = "Informatique",
                    publishYear = 2022,
                    totalCopies = 3,
                    availableCopies = 3,
                    description = "Principes et modèles de conception pour concevoir des applications modulaires, robustes et évolutives.",
                    shelfLocation = "Rayon Info - B3"
                ),
                BookEntity(
                    id = 2,
                    qrCode = "BOOK-978-02002",
                    title = "Anthologie Poétique Contemporaine",
                    author = "Claire Fontaine",
                    isbn = "978-2200200022",
                    category = "Littérature",
                    publishYear = 2020,
                    totalCopies = 1,
                    availableCopies = 0,
                    description = "Recueil littéraire et études de style sur la poésie moderne en langue française.",
                    shelfLocation = "Rayon Litt - A1"
                ),
                BookEntity(
                    id = 3,
                    qrCode = "BOOK-978-03003",
                    title = "Algorithmes et Structures de Données Avancées",
                    author = "Marc Lambert",
                    isbn = "978-2300300033",
                    category = "Informatique",
                    publishYear = 2021,
                    totalCopies = 4,
                    availableCopies = 3,
                    description = "Graphes, arbres de recherche, programmation dynamique et analyse de complexité temporelle.",
                    shelfLocation = "Rayon Info - B1"
                ),
                BookEntity(
                    id = 4,
                    qrCode = "BOOK-978-04004",
                    title = "Chroniques et Récits Modernes",
                    author = "Paul Valmont",
                    isbn = "978-2400400044",
                    category = "Littérature",
                    publishYear = 2019,
                    totalCopies = 1,
                    availableCopies = 0,
                    description = "Analyse sociologique et littéraire des grands récits et récits de voyage modernes.",
                    shelfLocation = "Rayon Litt - A2"
                ),
                BookEntity(
                    id = 5,
                    qrCode = "BOOK-978-05005",
                    title = "Fondements de l'Intelligence Artificielle & Deep Learning",
                    author = "Sarah Benali",
                    isbn = "978-2500500055",
                    category = "Informatique",
                    publishYear = 2023,
                    totalCopies = 2,
                    availableCopies = 1,
                    description = "Apprentissage profond, réseaux de neurones convolutifs, modèles génératifs et vision par ordinateur.",
                    shelfLocation = "Rayon Info - C2"
                ),
                BookEntity(
                    id = 6,
                    qrCode = "BOOK-978-06006",
                    title = "Physique Expérimentale et Mécanique Quantique",
                    author = "Denis Mercier",
                    isbn = "978-2600600066",
                    category = "Sciences",
                    publishYear = 2018,
                    totalCopies = 1,
                    availableCopies = 0,
                    description = "Cours complet et travaux pratiques sur les postulats fondamentaux de la mécanique ondulatoire.",
                    shelfLocation = "Rayon Sciences - S4"
                ),
                BookEntity(
                    id = 7,
                    qrCode = "BOOK-978-07007",
                    title = "Histoire des Civilisations et Économie Mondiale",
                    author = "Julien Rousseau",
                    isbn = "978-2700700077",
                    category = "Histoire",
                    publishYear = 2017,
                    totalCopies = 2,
                    availableCopies = 2,
                    description = "Étude panoramique de l'évolution des structures politiques, des traités commerciaux et des sociétés.",
                    shelfLocation = "Rayon Hist - H1"
                ),
                BookEntity(
                    id = 8,
                    qrCode = "BOOK-978-08008",
                    title = "Principes Fondamentaux du Droit Civil et des Obligations",
                    author = "Élodie Garnier",
                    isbn = "978-2800800088",
                    category = "Droit",
                    publishYear = 2020,
                    totalCopies = 3,
                    availableCopies = 2,
                    description = "Manuel juridique complet sur le droit des contrats, la responsabilité civile et les biens.",
                    shelfLocation = "Rayon Droit - D2"
                ),
                BookEntity(
                    id = 9,
                    qrCode = "BOOK-978-09009",
                    title = "Base de Données Relationnelles et Systèmes Distribués",
                    author = "Karim Bennani",
                    isbn = "978-2900900099",
                    category = "Informatique",
                    publishYear = 2024,
                    totalCopies = 3,
                    availableCopies = 3,
                    description = "Optimisation SQL, transactions ACID, réplication de données et architectures NoSQL modernes.",
                    shelfLocation = "Rayon Info - B2"
                ),
                BookEntity(
                    id = 10,
                    qrCode = "BOOK-978-10010",
                    title = "Biochimie Médicale et Biologie Moléculaire",
                    author = "Dr. Houda Chraibi",
                    isbn = "978-2101000100",
                    category = "Sciences",
                    publishYear = 2022,
                    totalCopies = 2,
                    availableCopies = 1,
                    description = "Manuel pratique pour les étudiants en médecine et pharmacie sur le métabolisme cellulaire.",
                    shelfLocation = "Rayon Santé - M1"
                ),
                BookEntity(
                    id = 11,
                    qrCode = "BOOK-978-11011",
                    title = "Macroéconomie et Politiques Monétaires",
                    author = "Prof. Omar Fassi",
                    isbn = "978-2110100111",
                    category = "Droit & Économie",
                    publishYear = 2021,
                    totalCopies = 2,
                    availableCopies = 2,
                    description = "Théories monétaires contemporaines, régulation bancaire et commerce international.",
                    shelfLocation = "Rayon Éco - E3"
                ),
                BookEntity(
                    id = 12,
                    qrCode = "BOOK-978-12012",
                    title = "Histoire et Patrimoine de la Ville de Fès",
                    author = "Abdelkader Filali",
                    isbn = "978-2120100122",
                    category = "Histoire",
                    publishYear = 2019,
                    totalCopies = 2,
                    availableCopies = 0,
                    description = "Monographie historique et architecturale de la médina de Fès et de l'université Al Quaraouiyine.",
                    shelfLocation = "Rayon Hist - H3"
                )
            )
            dao.insertBooks(books)

            // ==========================================
            // MOCK DATA : EMPRUNTS EN COURS, RETARDS & RETOURS
            // ==========================================
            val loans = listOf(
                // 1) En retard de 5 jours
                LoanEntity(
                    id = 1,
                    bookId = 2,
                    bookTitle = "Anthologie Poétique Contemporaine",
                    bookQrCode = "BOOK-978-02002",
                    studentId = 1,
                    studentName = "Sophie Martin",
                    studentEmail = "sophie.martin@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-001",
                    loanDate = now - (19 * dayInMillis),
                    dueDate = now - (5 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = now - (1 * dayInMillis)
                ),
                // 2) En retard de 3 jours
                LoanEntity(
                    id = 2,
                    bookId = 6,
                    bookTitle = "Physique Expérimentale et Mécanique Quantique",
                    bookQrCode = "BOOK-978-06006",
                    studentId = 2,
                    studentName = "Lucas Bernard",
                    studentEmail = "lucas.bernard@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-002",
                    loanDate = now - (17 * dayInMillis),
                    dueDate = now - (3 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                ),
                // 3) En cours (reste 6 jours)
                LoanEntity(
                    id = 3,
                    bookId = 4,
                    bookTitle = "Chroniques et Récits Modernes",
                    bookQrCode = "BOOK-978-04004",
                    studentId = 3,
                    studentName = "Amina Diallo",
                    studentEmail = "amina.diallo@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-003",
                    loanDate = now - (8 * dayInMillis),
                    dueDate = now + (6 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                ),
                // 4) En retard de 8 jours
                LoanEntity(
                    id = 4,
                    bookId = 12,
                    bookTitle = "Histoire et Patrimoine de la Ville de Fès",
                    bookQrCode = "BOOK-978-12012",
                    studentId = 5,
                    studentName = "Yassine El Amrani",
                    studentEmail = "yassine.elamrani@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-005",
                    loanDate = now - (22 * dayInMillis),
                    dueDate = now - (8 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = now - (2 * dayInMillis)
                ),
                // 5) En cours (reste 11 jours)
                LoanEntity(
                    id = 5,
                    bookId = 5,
                    bookTitle = "Fondements de l'Intelligence Artificielle & Deep Learning",
                    bookQrCode = "BOOK-978-05005",
                    studentId = 6,
                    studentName = "Fatima-Zahra Berrada",
                    studentEmail = "fatimazahra.berrada@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-006",
                    loanDate = now - (3 * dayInMillis),
                    dueDate = now + (11 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                ),
                // 6) Déjà retourné
                LoanEntity(
                    id = 6,
                    bookId = 1,
                    bookTitle = "Conception et Architecture Logicielle",
                    bookQrCode = "BOOK-978-01001",
                    studentId = 4,
                    studentName = "Thomas Lefebvre",
                    studentEmail = "thomas.lefebvre@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-004",
                    loanDate = now - (30 * dayInMillis),
                    dueDate = now - (16 * dayInMillis),
                    returnDate = now - (15 * dayInMillis),
                    isReturned = true,
                    lastEmailReminderSent = null
                ),
                // 7) En retard de 4 jours pour enttaim8@gmail.com
                LoanEntity(
                    id = 7,
                    bookId = 3,
                    bookTitle = "Algèbre Linéaire et Géométrie Analytique",
                    bookQrCode = "BOOK-978-03003",
                    studentId = 8,
                    studentName = "Enttaim Test",
                    studentEmail = "enttaim8@gmail.com",
                    studentCardQr = "STUDENT-ENTTAIM-8",
                    loanDate = now - (18 * dayInMillis),
                    dueDate = now - (4 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                ),
                // 8) En retard de 7 jours pour musixa360@gmail.com
                LoanEntity(
                    id = 8,
                    bookId = 9,
                    bookTitle = "Base de Données Relationnelles et Systèmes Distribués",
                    bookQrCode = "BOOK-978-09009",
                    studentId = 9,
                    studentName = "Musixa Test",
                    studentEmail = "musixa360@gmail.com",
                    studentCardQr = "STUDENT-MUSIXA-9",
                    loanDate = now - (21 * dayInMillis),
                    dueDate = now - (7 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                ),
                // 9) En retard de 5 jours pour Adam Abderrahmane Rahmaoui (pour tester la relance)
                LoanEntity(
                    id = 9,
                    bookId = 1,
                    bookTitle = "Conception et Architecture Logicielle",
                    bookQrCode = "BOOK-978-01001",
                    studentId = 10,
                    studentName = "Adam Abderrahmane Rahmaoui",
                    studentEmail = "adamabderrahmane.rahmaoui@usmba.ac.ma",
                    studentCardQr = "STUDENT-USMBA-2024-010",
                    loanDate = now - (19 * dayInMillis),
                    dueDate = now - (5 * dayInMillis),
                    returnDate = null,
                    isReturned = false,
                    lastEmailReminderSent = null
                )
            )

            loans.forEach { dao.insertLoan(it) }

            // ==========================================
            // MOCK DATA : HISTORIQUE NOTIFICATIONS EMAIL
            // ==========================================
            val emailLogs = listOf(
                EmailNotificationLogEntity(
                    id = 1,
                    loanId = 1,
                    recipientEmail = "sophie.martin@usmba.ac.ma",
                    recipientName = "Sophie Martin",
                    bookTitle = "Anthologie Poétique Contemporaine",
                    subject = "Rappel de bibliothèque : Retard de restitution d'ouvrage",
                    messageBody = "Bonjour Sophie Martin,\n\nVotre emprunt de l'ouvrage 'Anthologie Poétique Contemporaine' est en retard de 5 jour(s). Merci de le restituer au guichet de la bibliothèque.",
                    sentTimestamp = now - (1 * dayInMillis),
                    status = "Délivré via Service Email"
                ),
                EmailNotificationLogEntity(
                    id = 2,
                    loanId = 4,
                    recipientEmail = "yassine.elamrani@usmba.ac.ma",
                    recipientName = "Yassine El Amrani",
                    bookTitle = "Histoire et Patrimoine de la Ville de Fès",
                    subject = "Deuxième relance : Restitution d'ouvrage en retard",
                    messageBody = "Bonjour Yassine El Amrani,\n\nVotre emprunt pour 'Histoire et Patrimoine de la Ville de Fès' accuse un retard de 8 jour(s). Merci de régulariser la situation sans délai.",
                    sentTimestamp = now - (2 * dayInMillis),
                    status = "Délivré via Service Email"
                )
            )
            emailLogs.forEach { dao.insertEmailLog(it) }
        }
    }
}
}
