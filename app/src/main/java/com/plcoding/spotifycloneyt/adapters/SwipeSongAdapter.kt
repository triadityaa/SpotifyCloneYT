package com.plcoding.spotifycloneyt.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.plcoding.spotifycloneyt.R
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.databinding.SwipeItemBinding

class SwipeSongAdapter(
    private val onItemClick: (Song) -> Unit
) : ListAdapter<Song, SwipeSongAdapter.SwipeSongViewHolder>(SongDiffCallback) {

    class SwipeSongViewHolder(val binding: SwipeItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwipeSongViewHolder =
        SwipeSongViewHolder(SwipeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SwipeSongViewHolder, position: Int) {
        val song = getItem(position)
        holder.binding.apply {
            tvPrimary.text = if (song.subtitle.isEmpty()) {
                song.title
            } else {
                root.context.getString(R.string.song_title_with_artist, song.title, song.subtitle)
            }
            root.setOnClickListener { onItemClick(song) }
        }
    }
}
