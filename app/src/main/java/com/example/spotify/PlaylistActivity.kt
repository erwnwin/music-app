package com.example.spotify

import TrackAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private val songList = mutableListOf<Map<String, String>>()
    private val playlist = mutableListOf<Map<String, String>>()
    private var currentSongIndex = 0
    private var isPlaying = false
    private var mediaPlayer = MediaPlayer()

    private lateinit var songProgressBar: ProgressBar
    private lateinit var songDurationText: TextView
    private lateinit var playPauseButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var previousButton: ImageView

    private var currentUri: String? = null  // Store the current URI
    private val REQUEST_CODE_PERMISSIONS = 1001  // Request code for permissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playlist_activity)

        // Check and request permissions for Android 11+
        if (isPermissionGranted()) {
            initializeUI()
        } else {
            requestPermissions()
        }
    }

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeUI()
            } else {
                Toast.makeText(this, "Izin ditolak, aplikasi tidak dapat berjalan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeUI() {
        // Get the playlist passed from DashboardActivity
//        val playlistData = intent.getSerializableExtra("playlist") as? ArrayList<Map<String, String>>
//        playlistData?.let {
//            playlist.addAll(it)
//            songList.addAll(it)
//        }
//        val currentPlaylist = getPlaylist()

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // TrackAdapter setup
        trackAdapter = TrackAdapter(
            this,
            songList,
            { song -> playMusic(song["name"], song["uri"]) },
            { song -> togglePlaylist(song) }
        )
        recyclerView.adapter = trackAdapter

        // UI Elements
        songProgressBar = findViewById(R.id.song_progress_bar)
        songDurationText = findViewById(R.id.song_duration)
        playPauseButton = findViewById(R.id.play_pause_button)
        nextButton = findViewById(R.id.next_button)
        previousButton = findViewById(R.id.previous_button)

        // Set up the Play/Pause button
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        // Set up the Next button
        nextButton.setOnClickListener {
            playNextSong()
        }

        // Set up the Previous button
        previousButton.setOnClickListener {
            playPreviousSong()
        }

        // Floating Action Button (Playlist button)
        val fabPlaylist = findViewById<ImageView>(R.id.fab_playlist)
        fabPlaylist.setOnClickListener {
            finish()  // Back to DashboardActivity
        }

        // Initialize ProgressBar for song playback duration
        songProgressBar.max = 100
    }

    private fun playMusic(name: String?, uri: String?) {
        if (name == null || uri == null) {
            Toast.makeText(this, "File tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        // If the same song is already playing, do nothing
        if (isPlaying && uri == currentUri) {
            Toast.makeText(this, "Lagu sudah diputar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()

            // For Android 11 and above, we use content resolver with URI
            val songUri = Uri.parse(uri)  // Convert the URI string to a URI object

            // Use content resolver to set the data source, passing context (this) instead of contentResolver
            mediaPlayer.setDataSource(this, songUri)  // Correct usage of context (this) for setDataSource
            mediaPlayer.prepare()
            mediaPlayer.start()

            // Update UI for play state
            isPlaying = true
            currentUri = uri  // Save the current URI
            currentSongIndex = songList.indexOfFirst { it["uri"] == uri }
            songDurationText.text = "00:00"

            // Set on completion listener to play the next song
            mediaPlayer.setOnCompletionListener {
                playNextSong()
            }

            updateProgressBar()

        } catch (e: Exception) {
            Toast.makeText(this, "Error memutar musik: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer.pause()
            playPauseButton.setImageResource(R.drawable.play_button)
        } else {
            mediaPlayer.start()
            playPauseButton.setImageResource(R.drawable.pause_button)
        }
        isPlaying = !isPlaying
    }

    private fun playNextSong() {
        if (songList.isEmpty()) return

        currentSongIndex = (currentSongIndex + 1) % songList.size
        val nextSong = songList[currentSongIndex]
        playMusic(nextSong["name"], nextSong["uri"])
    }

    private fun playPreviousSong() {
        if (songList.isEmpty()) return

        currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
        val previousSong = songList[currentSongIndex]
        playMusic(previousSong["name"], previousSong["uri"])
    }

    private fun updateProgressBar() {
        val handler = Handler(Looper.getMainLooper())
        val updateProgress = object : Runnable {
            override fun run() {
                try {
                    if (mediaPlayer.isPlaying) {
                        val currentPosition = mediaPlayer.currentPosition
                        songProgressBar.progress = currentPosition
                        handler.postDelayed(this, 1000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        handler.post(updateProgress)
    }


    private fun togglePlaylist(song: Map<String, String>) {
        if (playlist.contains(song)) {
            playlist.remove(song)
            Toast.makeText(this, "${song["name"]} removed from playlist", Toast.LENGTH_SHORT).show()
        } else {
            playlist.add(song)
            Toast.makeText(this, "${song["name"]} added to playlist", Toast.LENGTH_SHORT).show()
        }
        trackAdapter.notifyDataSetChanged()  // Update RecyclerView
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()  // Release MediaPlayer resources
    }
}
