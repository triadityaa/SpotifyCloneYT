package com.plcoding.spotifycloneyt.adapters

import androidx.recyclerview.widget.DiffUtil
import com.plcoding.spotifycloneyt.data.entities.Song

object SongDiffCallback : DiffUtil.ItemCallback<Song>() {

    override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
        oldItem.mediaId == newItem.mediaId

    override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
        oldItem == newItem
}
