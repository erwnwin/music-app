package com.example.spotify

import android.content.ContentResolver
import android.content.Intent
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
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var songList: MutableList<Map<String, String>>
    private lateinit var songListCopy: MutableList<Map<String, String>> // A copy of the song list for searching
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = 0
    private var currentSongUri: Uri? = null
    private var isPlaying = false
    private lateinit var songProgressBar: ProgressBar
    private lateinit var songDurationText: TextView
    private val playlist = mutableListOf<Map<String, String>>() // Playlist

    // Register the ActivityResultLauncher to handle folder selection
    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { loadSongsFromFolder(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_activity)

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
                // onAddToPlaylistListener: Menambah atau menghapus lagu dari playlist
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
    }

    // Open playlist activity
    private fun openPlaylist() {
        val intent = Intent(this, PlaylistActivity::class.java)
        intent.putExtra("playlist", ArrayList(playlist)) // Pass the playlist to the new activity
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
            while (it.moveToNext()) {
                val name = it.getString(0)
                val documentId = it.getString(1)
                val mimeType = it.getString(2)

                if (mimeType.startsWith("audio/")) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId).toString()
                    songList.add(mapOf("name" to name, "uri" to fileUri))
                }
            }
            songListCopy = songList.toMutableList() // Update the copy for searching
            trackAdapter.notifyDataSetChanged()
        }
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

            // Update the duration of the song
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
    private fun togglePlaylist(song: Map<String, String>) {
        if (playlist.contains(song)) {
            playlist.remove(song)
            Toast.makeText(this, "${song["name"]} removed from playlist", Toast.LENGTH_SHORT).show()
        } else {
            playlist.add(song)
            Toast.makeText(this, "${song["name"]} added to playlist", Toast.LENGTH_SHORT).show()
        }
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

    // Update the song duration
    private fun updateSongDuration() {
        mediaPlayer?.apply {
            val durationInSeconds = duration / 1000
            val minutes = durationInSeconds / 60
            val seconds = durationInSeconds % 60
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
}
