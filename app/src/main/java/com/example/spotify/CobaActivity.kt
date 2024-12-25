package com.example.spotify

import TrackAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CobaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var trackAdapter: TrackAdapter
    private var songList = mutableListOf<Map<String, String>>()
    private val playlist = mutableListOf<Map<String, String>>()
    private var currentSongIndex = 0
    private var isPlaying = false
    private var mediaPlayer = MediaPlayer()
    private var currentSongUri: Uri? = null
    private var handler: Handler? = null
    private var updateRunnable: Runnable? = null
    private lateinit var songProgressBar: ProgressBar
    private lateinit var songDurationText: TextView
    private lateinit var playPauseButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var previousButton: ImageView

    private var currentUri: String? = null  // Store the current URI
    private val REQUEST_CODE_PERMISSIONS = 1001  // Request code for permissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coba)

        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
        if (!isLoggedIn) {
            // Arahkan ke halaman login jika belum login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()  // Menutup DashboardActivity agar tidak bisa kembali
            return
        }

        // Muat playlist dari SharedPreferences (dapat dipanggil di sini)
        songList.addAll(loadPlaylistFromSharedPreferences(this))

        // Setup RecyclerView dan Adapter
        recyclerView = findViewById(R.id.recycler_view_fav)
        recyclerView.layoutManager = LinearLayoutManager(this)

        trackAdapter = TrackAdapter(
            this,
            songList,
            { song -> playMusic(song["name"], song["uri"]) },
            { song -> togglePlaylist(song) }
        )
        recyclerView.adapter = trackAdapter

        songProgressBar = findViewById(R.id.song_progress_bar_far)
        songDurationText = findViewById(R.id.song_duration_fav)
        playPauseButton = findViewById(R.id.play_pause_button_fav)
        nextButton = findViewById(R.id.next_button_fav)
        previousButton = findViewById(R.id.previous_button_fav)

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
        val fabPlaylist = findViewById<ImageView>(R.id.fab_playlist_back)
        fabPlaylist.setOnClickListener {
            finish()  // Back to DashboardActivity
        }

        // Initialize ProgressBar for song playback duration
        songProgressBar.max = 100
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
                setDataSource(this@CobaActivity, Uri.parse(uri))
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

//    private fun playPreviousSong() {
//        if (songList.isEmpty()) return
//
//        currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
//        val previousSong = songList[currentSongIndex]
//        playMusic(previousSong["name"], previousSong["uri"])
//    }


    private fun savePlaylistToSharedPreferences(context: Context, playlist: List<Map<String, String>>) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Menggunakan Gson untuk mengonversi playlist menjadi JSON string
        val gson = Gson()
        val playlistJson = gson.toJson(playlist)

        editor.putString("playlist", playlistJson)
        editor.apply()
    }
}