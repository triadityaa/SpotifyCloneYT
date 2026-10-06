package com.plcoding.spotifycloneyt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.RequestManager
import com.google.android.material.snackbar.Snackbar
import com.plcoding.spotifycloneyt.R
import com.plcoding.spotifycloneyt.adapters.SwipeSongAdapter
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.databinding.ActivityMainBinding
import com.plcoding.spotifycloneyt.exoplayer.MusicServiceConnection
import com.plcoding.spotifycloneyt.other.Resource
import com.plcoding.spotifycloneyt.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var musicServiceConnection: MusicServiceConnection

    @Inject
    lateinit var glide: RequestManager

    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val swipeSongAdapter = SwipeSongAdapter { navigateToSongFragment() }

    private var isOnSongScreen = false
    private var isUserSwipingSong = false

    // Playback works without this permission; Android 13+ only hides the media notification.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is enforced from Android 15 (targetSdk 35+). The app is always dark, so
        // the system bar icons must always be light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            isOnSongScreen = destination.id == R.id.songFragment
            updateBottomBarVisibility()
        }

        setUpMiniPlayer()
        observeViewModel()

        if (savedInstanceState == null) requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        musicServiceConnection.connect()
    }

    override fun onStop() {
        musicServiceConnection.disconnect()
        super.onStop()
    }

    private fun setUpMiniPlayer() {
        binding.vpSong.adapter = swipeSongAdapter
        binding.vpSong.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> isUserSwipingSong = true
                    ViewPager2.SCROLL_STATE_IDLE -> isUserSwipingSong = false
                }
            }

            override fun onPageSelected(position: Int) {
                // Only react to swipes. Pages selected in code just follow the current song.
                if (!isUserSwipingSong) return
                val song = swipeSongAdapter.currentList.getOrNull(position) ?: return
                if (song.mediaId != mainViewModel.currentSong.value?.mediaId) {
                    mainViewModel.skipToSong(song)
                }
            }
        })
        binding.ivPlayPause.setOnClickListener { mainViewModel.togglePlayPause() }
        binding.ivCurSongImage.setOnClickListener { navigateToSongFragment() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.songs.collect { result ->
                        if (result is Resource.Success) {
                            swipeSongAdapter.submitList(result.data) { showCurrentSongInPager() }
                        }
                    }
                }
                launch {
                    mainViewModel.currentSong.collect { song ->
                        bindCurrentSong(song)
                        updateBottomBarVisibility()
                    }
                }
                launch {
                    mainViewModel.isPlaying.collect { isPlaying ->
                        binding.ivPlayPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }
                }
                launch {
                    mainViewModel.playerErrors.collect { error ->
                        Snackbar.make(
                            binding.rootLayout,
                            getString(R.string.error_playback, error.message.orEmpty()),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun bindCurrentSong(song: Song?) {
        if (song == null) return
        glide.load(song.imageUrl.ifEmpty { null }).into(binding.ivCurSongImage)
        showCurrentSongInPager()
    }

    private fun showCurrentSongInPager() {
        val mediaId = mainViewModel.currentSong.value?.mediaId ?: return
        val index = swipeSongAdapter.currentList.indexOfFirst { it.mediaId == mediaId }
        if (index != -1 && binding.vpSong.currentItem != index) {
            binding.vpSong.setCurrentItem(index, binding.vpSong.isVisible)
        }
    }

    private fun updateBottomBarVisibility() {
        val showMiniPlayer = !isOnSongScreen && mainViewModel.currentSong.value != null
        binding.ivCurSongImage.isVisible = showMiniPlayer
        binding.vpSong.isVisible = showMiniPlayer
        binding.ivPlayPause.isVisible = showMiniPlayer
    }

    private fun navigateToSongFragment() {
        if (navController.currentDestination?.id == R.id.homeFragment) {
            navController.navigate(R.id.globalActionToSongFragment)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
