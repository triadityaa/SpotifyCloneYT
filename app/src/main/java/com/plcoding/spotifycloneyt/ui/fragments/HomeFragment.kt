package com.plcoding.spotifycloneyt.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.RequestManager
import com.plcoding.spotifycloneyt.R
import com.plcoding.spotifycloneyt.adapters.SongAdapter
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.databinding.FragmentHomeBinding
import com.plcoding.spotifycloneyt.other.Resource
import com.plcoding.spotifycloneyt.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    @Inject
    lateinit var glide: RequestManager

    private val mainViewModel: MainViewModel by activityViewModels()

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        val songAdapter = SongAdapter(glide) { song -> mainViewModel.playOrToggleSong(song) }
        binding.rvAllSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        binding.btnRetry.setOnClickListener { mainViewModel.loadSongs() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.songs.collect { result -> render(result, songAdapter) }
            }
        }
    }

    private fun render(result: Resource<List<Song>>, songAdapter: SongAdapter) {
        binding.allSongsProgressBar.isVisible = result is Resource.Loading
        when (result) {
            Resource.Loading -> showMessage(null)
            is Resource.Success -> {
                songAdapter.submitList(result.data)
                showMessage(if (result.data.isEmpty()) getString(R.string.no_songs) else null)
            }
            is Resource.Error -> showMessage(getString(result.messageRes))
        }
    }

    private fun showMessage(message: String?) {
        binding.tvMessage.text = message
        binding.tvMessage.isVisible = message != null
        binding.btnRetry.isVisible = message != null
    }

    override fun onDestroyView() {
        binding.rvAllSongs.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
