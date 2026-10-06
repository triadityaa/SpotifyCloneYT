package com.plcoding.spotifycloneyt.ui.fragments

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.RequestManager
import com.plcoding.spotifycloneyt.R
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.databinding.FragmentSongBinding
import com.plcoding.spotifycloneyt.other.formatPlaybackTime
import com.plcoding.spotifycloneyt.ui.viewmodels.MainViewModel
import com.plcoding.spotifycloneyt.ui.viewmodels.PlaybackProgress
import com.plcoding.spotifycloneyt.ui.viewmodels.SongViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SongFragment : Fragment(R.layout.fragment_song) {

    @Inject
    lateinit var glide: RequestManager

    private val mainViewModel: MainViewModel by activityViewModels()
    private val songViewModel: SongViewModel by viewModels()

    private var _binding: FragmentSongBinding? = null
    private val binding get() = _binding!!

    private var isUserSeeking = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSongBinding.bind(view)

        binding.ivPlayPauseDetail.setOnClickListener { mainViewModel.togglePlayPause() }
        binding.ivSkipPrevious.setOnClickListener { mainViewModel.skipToPreviousSong() }
        binding.ivSkip.setOnClickListener { mainViewModel.skipToNextSong() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurTime.text = formatPlaybackTime(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mainViewModel.seekTo(seekBar.progress.toLong())
                isUserSeeking = false
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { mainViewModel.currentSong.collect { song -> bindSong(song) } }
                launch {
                    mainViewModel.isPlaying.collect { isPlaying ->
                        binding.ivPlayPauseDetail.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }
                }
                launch { songViewModel.playbackProgress.collect { progress -> bindProgress(progress) } }
            }
        }
    }

    private fun bindSong(song: Song?) {
        binding.tvSongName.text = song?.title.orEmpty()
        glide.load(song?.imageUrl?.ifEmpty { null }).into(binding.ivSongImage)
    }

    private fun bindProgress(progress: PlaybackProgress) {
        binding.tvSongDuration.text = formatPlaybackTime(progress.durationMs)
        if (isUserSeeking) return
        binding.seekBar.max = progress.durationMs.toInt()
        binding.seekBar.progress = progress.positionMs.toInt()
        binding.tvCurTime.text = formatPlaybackTime(progress.positionMs)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
