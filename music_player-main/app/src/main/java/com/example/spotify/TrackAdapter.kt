package com.example.spotify

import android.content.Context
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TrackAdapter(
    private val context: Context,
    private val songs: MutableList<Map<String, String>>, // Daftar lagu dalam bentuk Map
    private val onSongClickListener: (Map<String, String>) -> Unit, // Listener untuk memutar lagu
    private val onAddToPlaylistListener: (Map<String, String>) -> Unit // Listener untuk menambah lagu ke playlist
) : RecyclerView.Adapter<TrackAdapter.SongViewHolder>() {

    private val playlist = mutableListOf<Map<String, String>>() // Playlist untuk menampung lagu yang ditambahkan

    // ViewHolder untuk item lagu
    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songName: TextView = itemView.findViewById(R.id.song_name)
        val artistName: TextView = itemView.findViewById(R.id.artist_name)
        val addToPlaylistButton: ImageButton = itemView.findViewById(R.id.add_to_playlist_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.track_activity, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]

        // Menampilkan nama lagu dan artis
        holder.songName.text = song["name"] ?: "Unknown Title"
        holder.artistName.text = song["artist"] ?: "Unknown Artist" // Menambahkan nama artis jika ada

        // Menambahkan listener untuk item klik untuk memutar lagu
        holder.itemView.setOnClickListener {
            onSongClickListener(song)
        }

        // Menampilkan status tombol (ditambahkan/tidak)
        updateButtonState(holder.addToPlaylistButton, playlist.contains(song))

        // Menambahkan listener klik untuk tombol tambah ke playlist
        holder.addToPlaylistButton.setOnClickListener {
            togglePlaylist(song)
            // Perbarui status tombol setelah toggle
            updateButtonState(holder.addToPlaylistButton, playlist.contains(song))
        }
    }

    override fun getItemCount(): Int = songs.size

    // Fungsi untuk memperbarui status tombol (apakah sudah ditambahkan ke playlist atau belum)
    private fun updateButtonState(button: ImageButton, isAdded: Boolean) {
        if (isAdded) {
            button.setImageResource(R.drawable.add) // Button state for added song
        } else {
            button.setImageResource(R.drawable.not_add) // Button state for non-added song
        }
    }

    // Fungsi untuk mendapatkan daftar playlist
    fun getPlaylist(): List<Map<String, String>> = playlist

    // Fungsi untuk menambah lagu ke playlist atau menghapusnya
    private fun togglePlaylist(song: Map<String, String>) {
        if (playlist.contains(song)) {
            playlist.remove(song)
            Toast.makeText(context, "${song["name"]} dihapus dari playlist", Toast.LENGTH_SHORT).show()
        } else {
            playlist.add(song)
            Toast.makeText(context, "${song["name"]} ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
        }
        notifyDataSetChanged() // Memperbarui tampilan daftar lagu
    }
}
