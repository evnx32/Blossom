package id.blossom.ui.activity.stream

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import id.blossom.BlossomApp
import id.blossom.R
import id.blossom.data.model.anime.stream.EpisodeUrlItem
import id.blossom.data.storage.entity.CurrentWatchEntity
import id.blossom.databinding.ActivityStreamBinding
import id.blossom.di.component.DaggerActivityComponent
import id.blossom.di.module.ActivityModule
import id.blossom.ui.base.UiState
import id.blossom.utils.AppConstant
import id.blossom.utils.Utils
import kotlinx.coroutines.launch
import javax.inject.Inject

class StreamAnimeActivity : AppCompatActivity() {

    lateinit var binding: ActivityStreamBinding
    private var exoPlayer: SimpleExoPlayer? = null

    // ExoPlayer Components
    private lateinit var fwdBtn: ImageView
    private lateinit var rewBtn: ImageView
    private lateinit var settingBtn: ImageView
    private lateinit var fullscreenBtn: ImageView
    private lateinit var floatWindowBtn: ImageView
    private lateinit var exoEpisode: TextView
    private lateinit var fillScreen: ImageView
    private lateinit var exoSettingVideo: ImageView

    private var animeId = ""
    private var episodeId = ""
    private var saveStateDuration = 0L

    @Inject
    lateinit var streamAnimeViewModel: StreamAnimeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        injectDependencies()

        Utils().setFullScreen(window)

        setupUI()
        setupObserver()
    }

    private fun setupUI() {

        episodeId = intent.getStringExtra("episodeId").toString()
        animeId = intent.getStringExtra("animeId").toString()

        if (episodeId == "") {
            streamAnimeViewModel.fetchEpisodeUrlAnime(animeId)
            streamAnimeViewModel.getCurrentWatchByAnimeId(animeId)
        } else if (animeId == "") {
            streamAnimeViewModel.fetchEpisodeUrlAnime(episodeId)
            streamAnimeViewModel.getCurrentWatchByEpisodeId(episodeId)
        } else {
            Toast.makeText(this, "episode id or anime id is null", Toast.LENGTH_SHORT).show()
        }

        // ExoPlayer Components
        fwdBtn = findViewById(R.id.fwd)
        rewBtn = findViewById(R.id.rew)
        settingBtn = findViewById(R.id.exo_track_selection_view)
        fullscreenBtn = findViewById(R.id.fullscreen)
        floatWindowBtn = findViewById(R.id.exo_float_video)
        exoEpisode = findViewById(R.id.exo_episode)
        fillScreen = findViewById(R.id.exo_fill_screen)
        exoSettingVideo = findViewById(R.id.exo_settings_video)

        fwdBtn.setOnClickListener {
            exoPlayer?.seekTo(exoPlayer!!.currentPosition + 10000)
        }

        rewBtn.setOnClickListener {
            exoPlayer?.seekTo(exoPlayer!!.currentPosition - 10000)
        }

        fullscreenBtn.setOnClickListener {
            streamAnimeViewModel.savedStateDuration = exoPlayer!!.currentPosition
            val orientation: Int = this@StreamAnimeActivity.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                fillScreen.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                binding.playerView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                fillScreen.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                binding.playerView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
            }

        }

    }

    private fun setupObserver() {

        streamAnimeViewModel.uiStateEpisodeUrlAnime.observe(this@StreamAnimeActivity) {
            when (it) {
                is UiState.Success -> {
                    binding.progressBarExoplayer.visibility = View.GONE
                    renderEpisodeUrlList(it.data.episodeUrl!!)
                }
                is UiState.Loading -> {
                    binding.progressBarExoplayer.visibility = View.VISIBLE
                }
                is UiState.Error -> {
                    //Handle Error
                    Toast.makeText(this@StreamAnimeActivity, it.message, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

    }


    private fun renderEpisodeUrlList(list: List<EpisodeUrlItem>) {
        val resolution = "720"
        if (!streamAnimeViewModel.isAlreadyPlaying) {
            Log.e("EpisodeUrlItem", list.toString())
            list.map {
                if (it.size == resolution) {
                    setupPlayer(it.episode!!)
                }
            }
        }
    }


    private fun setupPlayer(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "Url is empty", Toast.LENGTH_LONG).show()
            return
        }

        exoPlayer = SimpleExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer

        val mediaSource: MediaSource? = if (url.contains("m3u8")) {
            buildHlsMediaSource(url)
        } else {
            buildMediaSource(url)
        }


        if (mediaSource != null) {
            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true

            streamAnimeViewModel.uiStateCurrentWatch.observe(this) { data ->
                if (data == null) {
                    streamAnimeViewModel.setSavedStateDuration(
                        CurrentWatchEntity(
                            0,
                            animeId = animeId,
                            episodeId = episodeId,
                            duration = exoPlayer!!.duration,
                            currentDuration = exoPlayer!!.currentPosition,
                            true
                        )
                    )
                } else {
                    exoPlayer!!.seekTo(data.currentDuration)
                }
            }

            exoPlayer?.addListener(object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        Toast.makeText(
                            this@StreamAnimeActivity,
                            "Video has ended",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

            handler.post(updateRunnable)
        }
    }

    private fun buildHlsMediaSource(url: String): MediaSource? {
        return HlsMediaSource.Factory(DefaultDataSourceFactory(this, AppConstant.USER_AGENT))
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .build()
            )
    }

    private fun buildMediaSource(url: String): MediaSource {
        return ProgressiveMediaSource.Factory(
            DefaultDataSourceFactory(
                this,
                AppConstant.USER_AGENT
            )
        )
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .build()
            )
    }


    private fun injectDependencies() {
        DaggerActivityComponent.builder()
            .applicationComponent((application as BlossomApp).applicationComponent)
            .activityModule(ActivityModule(this))
            .build()
            .inject(this)
    }


    override fun onBackPressed() {
        super.onBackPressed()
        if (exoPlayer != null) {

            if (exoPlayer!!.isPlaying) {
                streamAnimeViewModel.savedStateDuration = exoPlayer!!.currentPosition
                exoPlayer?.playWhenReady = false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (exoPlayer != null) {

            if (exoPlayer!!.isPlaying) {
                streamAnimeViewModel.savedStateDuration = exoPlayer!!.currentPosition
                exoPlayer?.playWhenReady = false
                handler.removeCallbacks(updateRunnable)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (exoPlayer != null) {
            exoPlayer?.playWhenReady = true
            handler.post(updateRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    override fun onResume() {
        super.onResume()
        Utils().setFullScreen(window)
        if (exoPlayer != null) {
            exoPlayer?.playWhenReady = true
            handler.post(updateRunnable)
        }
    }


    companion object {

        fun start(context: Context, episodeId: String, animeId: String) {
            val intent = Intent(context, StreamAnimeActivity::class.java)
            intent.putExtra("episodeId", episodeId)
            intent.putExtra("animeId", animeId)
            context.startActivity(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (exoPlayer != null) {
                val duration = exoPlayer!!.duration
                val currentPosition = exoPlayer!!.currentPosition
                lifecycleScope.launch {
                    if (currentPosition > 0) {
                        streamAnimeViewModel.updateSavedStateDuration(
                            true,
                            duration,
                            currentPosition,
                            animeId, episodeId
                        )
                    }
                }
                handler.postDelayed(this, 10000) // Post every 10 seconds
            }
        }
    }
}