package com.example.spotify.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "user_db"
        private const val DATABASE_VERSION = 1

        // Tabel Users
        private const val TABLE_NAME_USERS = "users"
        private const val COLUMN_ID_USER = "id"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"

//        private const val TABLE_NAME_SONGS = "songs"
        const val TABLE_NAME_SONGS = "songs"
        const val COLUMN_ID_SONG = "id"
        const val COLUMN_USER_ID = "user_id" // Foreign key to users table
        const val COLUMN_SONG_NAME = "song_name"
        const val COLUMN_SONG_URI = "song_uri"

        // Table Names
        const val TABLE_NAME_FAVORITES = "favorites"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUserTable = "CREATE TABLE $TABLE_NAME_USERS (" +
                "$COLUMN_ID_USER INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_EMAIL TEXT, " +
                "$COLUMN_PASSWORD TEXT)"
        db.execSQL(createUserTable)

        val createSongsTable = """
            CREATE TABLE $TABLE_NAME_SONGS (
                $COLUMN_ID_SONG INTEGER PRIMARY KEY AUTOINCREMENT,
                song_name TEXT,
                user_id INTEGER,
                song_uri TEXT, 
                is_favorite INTEGER DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES $TABLE_NAME_USERS($COLUMN_ID_USER)
            );
        """
        db.execSQL(createSongsTable)


        val createTableQuery = """
            CREATE TABLE $TABLE_NAME_FAVORITES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER,
                $COLUMN_SONG_NAME TEXT,
                $COLUMN_SONG_URI TEXT
            )
        """
        db.execSQL(createTableQuery)


    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME_SONGS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME_FAVORITES")
        onCreate(db)
    }

    fun addUser(email: String, password: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
        }
        val result = db.insert(TABLE_NAME_USERS, null, values)
        db.close()
        return result != -1L
    }

    fun validateLogin(email: String, password: String): Boolean {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME_USERS WHERE $COLUMN_EMAIL = ? AND $COLUMN_PASSWORD = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(email, password))
        val isValid = cursor.count > 0
        cursor.close()
        db.close()
        return isValid
    }

    fun isEmailExist(email: String): Boolean {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME_USERS WHERE $COLUMN_EMAIL = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(email))
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun getUserIdByEmail(email: String): Int {
        val db = readableDatabase
        val query = "SELECT $COLUMN_ID_USER FROM $TABLE_NAME_USERS WHERE $COLUMN_EMAIL = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(email))

        var userId = -1
        if (cursor.moveToFirst()) {
            // Memastikan kolom $COLUMN_ID ada sebelum mengambil nilainya
            val columnIndex = cursor.getColumnIndex(COLUMN_ID_USER)
            if (columnIndex != -1) {
                userId = cursor.getInt(columnIndex)
            }
        }
        cursor.close()
        return userId
    }


    fun getImportedSongsForUser(userId: Int): List<Map<String, String>> {
        val db = readableDatabase
        val query = "SELECT song_name, song_uri FROM $TABLE_NAME_SONGS WHERE user_id = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(userId.toString()))

        val songs = mutableListOf<Map<String, String>>()
        val nameColumnIndex = cursor.getColumnIndex("song_name")
        val uriColumnIndex = cursor.getColumnIndex("song_uri")

        // Memastikan kolom song_name dan song_uri ada sebelum mengambil nilainya
        if (nameColumnIndex != -1 && uriColumnIndex != -1) {
            while (cursor.moveToNext()) {
                val songName = cursor.getString(nameColumnIndex)
                val songUri = cursor.getString(uriColumnIndex)
                songs.add(mapOf("name" to songName, "uri" to songUri))
            }
        }
        cursor.close()
        return songs
    }

    // Method untuk membaca data dari tabel
    fun getReadableDatabaseInstance(): SQLiteDatabase {
        return this.readableDatabase
    }

    // Method untuk menulis data ke tabel
    fun getWritableDatabaseInstance(): SQLiteDatabase {
        return this.writableDatabase
    }


    // Fungsi untuk menambahkan lagu ke playlist berdasarkan user_id (Integer)
    fun addToFavorites(userId: Int, songName: String, songUri: String): Long {
        val db = writableDatabase
        val contentValues = android.content.ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_SONG_NAME, songName)
            put(COLUMN_SONG_URI, songUri)
        }
        return db.insert(TABLE_NAME_FAVORITES, null, contentValues)
    }

    // Fungsi untuk menghapus lagu dari playlist berdasarkan user_id dan song_name
    fun removeFromFavorites(userId: Int, songName: String): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_NAME_FAVORITES,
            "$COLUMN_USER_ID = ? AND $COLUMN_SONG_NAME = ?",
            arrayOf(userId.toString(), songName)
        )
    }

    // Fungsi untuk mengambil playlist berdasarkan user_id
    fun getFavoritesByUserId(userId: Int): List<Map<String, String>> {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_NAME_FAVORITES,
            arrayOf(COLUMN_SONG_NAME, COLUMN_SONG_URI),
            "$COLUMN_USER_ID = ?",
            arrayOf(userId.toString()),
            null, null, null
        )

        val favorites = mutableListOf<Map<String, String>>()
        while (cursor.moveToNext()) {
            val songName = cursor.getString(cursor.getColumnIndex(COLUMN_SONG_NAME))
            val songUri = cursor.getString(cursor.getColumnIndex(COLUMN_SONG_URI))
            favorites.add(mapOf("name" to songName, "uri" to songUri))
        }
        cursor.close()
        return favorites
    }

}
