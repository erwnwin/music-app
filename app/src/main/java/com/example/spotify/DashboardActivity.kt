package com.example.spotify

import TrackAdapter
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spotify.database.DatabaseHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var songList: MutableList<Map<String, String>>
    private lateinit var songListCopy: MutableList<Map<String, String>> // A copy of the song list for searching
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = 0
    private var currentSongUri: Uri? = null
    private var isPlaying : Boolean = false
    private var handler: Handler? = null
    private var updateRunnable: Runnable? = null
    private lateinit var songProgressBar: ProgressBar
    private lateinit var songDurationText: TextView
    private val playlist = mutableListOf<Map<String, String>>() // Playlist
    private lateinit var databaseHelper: DatabaseHelper

    // Register the ActivityResultLauncher to handle folder selection
    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { loadSongsFromFolder(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_activity)

        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
        if (!isLoggedIn) {
            // Arahkan ke halaman login jika belum login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()  // Menutup DashboardActivity agar tidak bisa kembali
            return
        }

        databaseHelper = DatabaseHelper(this)


        // Initialize RecyclerView and adapter
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        songList = mutableListOf() // Initialize your song list here
        songListCopy = songList.toMutableList() // Create a copy of song list for searching

        // Perbaikan: Menambahkan parameter untuk listener
        trackAdapter = TrackAdapter(
            this,
            songList,
            { song ->
                // onSongClickListener: Ketika lagu dipilih, putar lagu
                playMusic(song["name"], song["uri"])
            },
            { song ->
                togglePlaylist(song)
            }
        )
        recyclerView.adapter = trackAdapter

        // Initialize UI elements
        songProgressBar = findViewById(R.id.song_progress_bar)
        songDurationText = findViewById(R.id.song_duration)

        // SearchView for searching songs
        val searchView = findViewById<SearchView>(R.id.search_view)
        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon.setColorFilter(Color.BLACK)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // You can perform any action when the search is submitted (optional)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Filter the song list based on search text
                filterSongs(newText)
                return true
            }
        })

        // Play/Pause button
        val playPauseButton = findViewById<ImageView>(R.id.play_pause_button)
        playPauseButton.setOnClickListener {
            togglePlayPause(playPauseButton)
        }

        // Next button
        val nextButton = findViewById<ImageView>(R.id.next_button)
        nextButton.setOnClickListener {
            playNextSong()
        }

        // Previous button
        val previousButton = findViewById<ImageView>(R.id.previous_button)
        previousButton.setOnClickListener {
            playPreviousSong()
        }

        // Playlist button
        val playlistButton = findViewById<ImageView>(R.id.fab_playlist)
        playlistButton.setOnClickListener {
            openPlaylist()
        }

        // FAB Folder button
        val fabFolderButton = findViewById<ImageView>(R.id.fab_folder)
        fabFolderButton.setOnClickListener {
            selectFolder()
        }

        // Get userId from SharedPreferences
//        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userId = sharedPreferences.getInt("userId", -1)

        if (userId != -1) {
            val songsImported = databaseHelper.getImportedSongsForUser(userId)
            if (songsImported.isEmpty()) {
                selectFolder()  // User needs to import songs
            } else {
                loadExistingSongs(songsImported)  // Load existing songs
            }
        }
    }

    // Open playlist activity
    private fun openPlaylist() {
        val intent = Intent(this, CobaActivity::class.java)
//        intent.putExtra("playlist", ArrayList(playlist))
        startActivity(intent)
    }

    // Function to open folder picker
    private fun selectFolder() {
        // Launch Activity to pick folder
        selectFolderLauncher.launch(null)
    }

    // Load songs from selected folder URI
    private fun loadSongsFromFolder(folderUri: Uri) {
        val resolver: ContentResolver = contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val cursor = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )

        cursor?.use {
            songList.clear()

            // Ambil userId dari SharedPreferences
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val userId = sharedPreferences.getInt("userId", -1)

            while (it.moveToNext()) {
                val name = it.getString(0) // Get display name
                val documentId = it.getString(1) // Get document ID
                val mimeType = it.getString(2) // Get MIME type

                // Cek apakah file tersebut audio
                if (mimeType.startsWith("audio/")) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId).toString()

                    // Periksa apakah lagu sudah ada di database berdasarkan userId
                    if (userId != -1 && !isSongExist(name, fileUri, userId)) {
                        // Menambahkan lagu ke dalam database jika belum ada
                        addSongToDatabase(name, fileUri, userId)

                        // Menambahkan lagu ke dalam daftar lagu untuk ditampilkan
                        songList.add(mapOf("name" to name, "uri" to fileUri))
                    }
                }
            }

            songListCopy = songList.toMutableList() // Update the copy for searching
            trackAdapter.notifyDataSetChanged()
        }
    }

    private fun addSongToDatabase(songName: String, songUri: String, userId: Int) {
        val db = databaseHelper.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(DatabaseHelper.COLUMN_SONG_NAME, songName)
        contentValues.put(DatabaseHelper.COLUMN_SONG_URI, songUri)
        contentValues.put(DatabaseHelper.COLUMN_USER_ID, userId)

        db.insert(DatabaseHelper.TABLE_NAME_SONGS, null, contentValues)
    }

    private fun loadExistingSongs(songs: List<Map<String, String>>) {
        songList.clear()
        songList.addAll(songs)
        songListCopy = songList.toMutableList()
        trackAdapter.notifyDataSetChanged()
    }

    // Function to play music
    private fun playMusic(name: String?, uri: String?) {
        if (name == null || uri == null) {
            Toast.makeText(this, "File tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if the same song is already playing
        if (currentSongUri == Uri.parse(uri) && mediaPlayer?.isPlaying == true) {
            Toast.makeText(this, "Lagu sudah diputar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Stop and reset the player if it's already playing
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
            }

            // Create new MediaPlayer instance
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@DashboardActivity, Uri.parse(uri))
                prepare()
                start()
            }

            isPlaying = true
            currentSongUri = Uri.parse(uri)
            currentSongIndex = songList.indexOfFirst { it["uri"] == uri }
            Toast.makeText(this, "Memutar: $name", Toast.LENGTH_SHORT).show()

            startSongDurationUpdate()
            updateSongDuration()

            // Automatically play next song when current one finishes
            mediaPlayer?.setOnCompletionListener {
                playNextSong()
            }

            // Update progress bar
            updateProgressBar()

        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error memutar musik: ${e.message}")
            Toast.makeText(this, "Error memutar musik: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Add or remove song from playlist
//    private fun togglePlaylist(song: Map<String, String>) {
//
//        if (playlist.contains(song)) {
//            playlist.remove(song)
//            Toast.makeText(this, "${song["name"]} dihapus dari playlist", Toast.LENGTH_SHORT).show()
//        } else {
//            playlist.add(song)
//            Toast.makeText(this, "${song["name"]} ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
//        }
//        trackAdapter.notifyDataSetChanged() // Memperbarui tampilan daftar lagu
//    }

    private fun togglePlaylist(song: Map<String, String>) {
        if (playlist.contains(song)) {
            playlist.remove(song)
            Toast.makeText(this, "${song["name"]} dihapus dari playlist", Toast.LENGTH_SHORT).show()
        } else {
            playlist.add(song)
            Toast.makeText(this, "${song["name"]} ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
        }

        // Simpan perubahan playlist ke SharedPreferences
        savePlaylistToSharedPreferences(this, playlist)

        // Memperbarui tampilan daftar lagu
        trackAdapter.notifyDataSetChanged()
    }


    private fun savePlaylistToSharedPreferences(context: Context, playlist: List<Map<String, String>>) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Menggunakan Gson untuk mengonversi playlist menjadi JSON string
        val gson = Gson()
        val playlistJson = gson.toJson(playlist)

        editor.putString("playlist", playlistJson)
        editor.apply()
    }


    private fun loadPlaylistFromSharedPreferences(context: Context): List<Map<String, String>> {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

        // Mengambil string JSON dari SharedPreferences
        val playlistJson = sharedPreferences.getString("playlist", "[]")  // Default is empty array

        val gson = Gson()
        val type = object : TypeToken<List<Map<String, String>>>() {}.type

        // Mengonversi JSON string kembali ke daftar playlist
        return gson.fromJson(playlistJson, type)
    }



    // Function to filter songs based on search query
    private fun filterSongs(query: String?) {
        val filteredList = if (query.isNullOrEmpty()) {
            songListCopy // Return original list if query is empty
        } else {
            songListCopy.filter { song ->
                song["name"]?.contains(query, ignoreCase = true) == true
            }
        }
        songList.clear()
        songList.addAll(filteredList)
        trackAdapter.notifyDataSetChanged()
    }

    // Toggle play/pause
    private fun togglePlayPause(playPauseButton: ImageView) {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            playPauseButton.setImageResource(R.drawable.play_button) // Set to play icon
        } else {
            mediaPlayer?.start()
            isPlaying = true
            playPauseButton.setImageResource(R.drawable.pause_button) // Set to pause icon
        }
    }

//    private fun togglePlayPause(playPauseButton: ImageView) {
//        if (isPlaying) {
//            mediaPlayer?.pause()
//            isPlaying = false
//            playPauseButton.setImageResource(R.drawable.play_button) // Set to play icon
//        } else {
//            mediaPlayer?.start()
//            isPlaying = true
//            playPauseButton.setImageResource(R.drawable.pause_button) // Set to pause icon
//        }
//    }

    // Play next song
    private fun playNextSong() {
        if (currentSongIndex + 1 < songList.size) {
            val nextSong = songList[currentSongIndex + 1]
            playMusic(nextSong["name"], nextSong["uri"])
        } else {
            Toast.makeText(this, "This is the last song", Toast.LENGTH_SHORT).show()
        }
    }

    // Play previous song
    private fun playPreviousSong() {
        if (currentSongIndex - 1 >= 0) {
            val previousSong = songList[currentSongIndex - 1]
            playMusic(previousSong["name"], previousSong["uri"])
        } else {
            Toast.makeText(this, "This is the first song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSongDurationUpdate() {
        handler = Handler(Looper.getMainLooper())

        updateRunnable = object : Runnable {
            override fun run() {
                updateSongDuration() // Update song duration every second

                // Schedule the next update (1 second later)
                handler?.postDelayed(this, 1000)
            }
        }

        // Start the update loop immediately
        updateRunnable?.run()
    }

    // Update the song duration
    private fun updateSongDuration() {
        mediaPlayer?.apply {
            val currentPositionInSeconds = currentPosition / 1000
            val minutes = currentPositionInSeconds / 60
            val seconds = currentPositionInSeconds % 60

            songDurationText.text = String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Update the progress bar
    private fun updateProgressBar() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                // Check if mediaPlayer is initialized and playing
                mediaPlayer?.let {
                    val currentPosition = it.currentPosition
                    songProgressBar.progress = currentPosition
                    // Update every 1000 milliseconds (1 second)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun isSongExist(songName: String, songUri: String, userId: Int): Boolean {
        val db = databaseHelper.getReadableDatabaseInstance()

        val cursor = db.query(
            DatabaseHelper.TABLE_NAME_SONGS,
            arrayOf(DatabaseHelper.COLUMN_ID_SONG),
            "${DatabaseHelper.COLUMN_SONG_NAME} = ? AND ${DatabaseHelper.COLUMN_SONG_URI} = ? AND user_id = ?",
            arrayOf(songName, songUri, userId.toString()),
            null,
            null,
            null
        )

        val isExist = cursor?.count ?: 0 > 0
        cursor?.close()
        return isExist
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
        return sharedPreferences.getInt("userId", -1) // -1 sebagai fallback jika user_id tidak ditemukan
    }



}
