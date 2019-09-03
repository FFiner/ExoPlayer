package com.google.android.exoplayer2.demo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.spherical.SphericalSurfaceView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class FourPlayerActivity extends AppCompatActivity
        implements View.OnClickListener, PlaybackPreparer, PlayerControlView.VisibilityListener{

    public static final String DRM_SCHEME_EXTRA = "drm_scheme";
    public static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
    public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
    public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
    public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";

    public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
    public static final String EXTENSION_EXTRA = "extension";

    public static final String ACTION_VIEW_LIST =
            "com.google.android.exoplayer.demo.action.VIEW_LIST";
    public static final String URI_LIST_EXTRA = "uri_list";
    public static final String EXTENSION_LIST_EXTRA = "extension_list";

    public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";

    public static final String ABR_ALGORITHM_EXTRA = "abr_algorithm";
    public static final String ABR_ALGORITHM_DEFAULT = "default";
    public static final String ABR_ALGORITHM_RANDOM = "random";

    public static final String SPHERICAL_STEREO_MODE_EXTRA = "spherical_stereo_mode";
    public static final String SPHERICAL_STEREO_MODE_MONO = "mono";
    public static final String SPHERICAL_STEREO_MODE_TOP_BOTTOM = "top_bottom";
    public static final String SPHERICAL_STEREO_MODE_LEFT_RIGHT = "left_right";

    // For backwards compatibility only.
    private static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

    // Saved instance state keys.
    private static final String KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters";
    private static final String KEY_WINDOW = "window";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";

    Uri[] uris = new Uri[4];

    private static final CookieManager DEFAULT_COOKIE_MANAGER;
    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private PlayerView playerView_1;
    private PlayerView playerView_2;
    private PlayerView playerView_3;
    private PlayerView playerView_4;

    private LinearLayout debugRootView;
    private Button selectTracksButton;
    private TextView debugTextView;
    private boolean isShowingTrackSelectionDialog;

    //private DataSource.Factory dataSourceFactory;
    private SimpleExoPlayer player;
    private FrameworkMediaDrm mediaDrm;
    private MediaSource mediaSource;
    private DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters trackSelectorParameters;
    private DebugTextViewHelper debugViewHelper;
    private TrackGroupArray lastSeenTrackGroupArray;

    private boolean startAutoPlay;
    private int startWindow;
    private long startPosition;

    // Fields used only for ad playback. The ads loader is loaded via reflection.

    private AdsLoader adsLoader;
    private Uri loadedAdTagUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /*String sphericalStereoMode = getIntent().getStringExtra(SPHERICAL_STEREO_MODE_EXTRA);
        if (sphericalStereoMode != null) {
            setTheme(R.style.PlayerTheme_Spherical);
        }*/

        super.onCreate(savedInstanceState);

        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }

        setContentView(R.layout.activity_four_player);

        playerView_1 = findViewById(R.id.player_view_1);
        playerView_2 = findViewById(R.id.player_view_2);
        playerView_3 = findViewById(R.id.player_view_3);
        playerView_4 = findViewById(R.id.player_view_4);

        playerView_1.setControllerVisibilityListener(this);
        playerView_1.setErrorMessageProvider(new FourPlayerActivity.PlayerErrorMessageProvider());
        playerView_1.requestFocus();

        /*if (sphericalStereoMode != null) {
            int stereoMode;
            if (SPHERICAL_STEREO_MODE_MONO.equals(sphericalStereoMode)) {
                stereoMode = C.STEREO_MODE_MONO;
            } else if (SPHERICAL_STEREO_MODE_TOP_BOTTOM.equals(sphericalStereoMode)) {
                stereoMode = C.STEREO_MODE_TOP_BOTTOM;
            } else if (SPHERICAL_STEREO_MODE_LEFT_RIGHT.equals(sphericalStereoMode)) {
                stereoMode = C.STEREO_MODE_LEFT_RIGHT;
            } else {
                showToast(R.string.error_unrecognized_stereo_mode);
                finish();
                return;
            }
            ((SphericalSurfaceView) playerView_1.getVideoSurfaceView()).setDefaultStereoMode(stereoMode);
        }*/

        /*if (savedInstanceState != null) {
            trackSelectorParameters = savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS);
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startWindow = savedInstanceState.getInt(KEY_WINDOW);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            trackSelectorParameters = new DefaultTrackSelector.ParametersBuilder().build();
            clearStartPosition();
        }*/

        /*new Handler().postDelayed(new Runnable() {
            public void run() {
                initializePlayer(1);
            }
        }, 2 * 1000);

        new Handler().postDelayed(new Runnable() {
            public void run() {
                initializePlayer(2);
            }
        }, 6 * 1000);

        new Handler().postDelayed(new Runnable() {
            public void run() {
                initializePlayer(3);
            }
        }, 10 * 1000);

        new Handler().postDelayed(new Runnable() {
            public void run() {
                initializePlayer(4);
            }
        }, 14 * 1000);*/
        Intent intent = getIntent();
        uris[0] = Uri.parse(intent.getData().getPath() + "/source1.mp4");
        uris[1] = Uri.parse(intent.getData().getPath() + "/source2.mp4");
        uris[2] = Uri.parse(intent.getData().getPath() + "/source3.mp4");
        uris[3] = Uri.parse(intent.getData().getPath() + "/source4.mp4");
        if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ this, uris)) {
            // The player will be reinitialized if the permission is granted.
            return;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //releasePlayer();
        //releaseAdsLoader();
        clearStartPosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 100) {
            /*//initializePlayer(1);
            if (playerView_1 != null) {
                playerView_1.onResume();
            }
            //initializePlayer(2);
            if (playerView_2 != null) {
                playerView_2.onResume();
            }
            //initializePlayer(3);
            if (playerView_3 != null) {
                playerView_3.onResume();
            }
            //initializePlayer(4);
            if (playerView_4 != null) {
                playerView_4.onResume();
            }*/
            /*new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(1);
                    if (playerView_1 != null) {
                        playerView_1.onResume();
                    }
                }
            }, 5 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(2);
                    if (playerView_2 != null) {
                        playerView_2.onResume();
                    }
                }
            }, 10 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(3);
                    if (playerView_3 != null) {
                        playerView_3.onResume();
                    }
                }
            }, 15 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(4);
                    if (playerView_4 != null) {
                        playerView_4.onResume();
                    }
                }
            }, 20 * 1000);*/
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            /*//initializePlayer(1);
            if (playerView_1 != null) {
                playerView_1.onResume();
            }
            //initializePlayer(2);
            if (playerView_2 != null) {
                playerView_2.onResume();
            }
            //initializePlayer(3);
            if (playerView_3 != null) {
                playerView_3.onResume();
            }
            //initializePlayer(4);
            if (playerView_4 != null) {
                playerView_4.onResume();
            }*/
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(1);
                    if (playerView_1 != null) {
                        playerView_1.onResume();
                    }
                }
            }, 5 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(2);
                    if (playerView_2 != null) {
                        playerView_2.onResume();
                    }
                }
            }, 10 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(3);
                    if (playerView_3 != null) {
                        playerView_3.onResume();
                    }
                }
            }, 15 * 1000);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    initializePlayer(4);
                    if (playerView_4 != null) {
                        playerView_4.onResume();
                    }
                }
            }, 20 * 1000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            if (playerView_1 != null) {
                playerView_1.onPause();
            }
            if (playerView_2 != null) {
                playerView_2.onPause();
            }
            if (playerView_3 != null) {
                playerView_3.onPause();
            }
            if (playerView_4 != null) {
                playerView_4.onPause();
            }
            //releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            if (playerView_1 != null) {
                playerView_1.onPause();
            }
            if (playerView_2 != null) {
                playerView_2.onPause();
            }
            if (playerView_3 != null) {
                playerView_3.onPause();
            }
            if (playerView_4 != null) {
                playerView_4.onPause();
            }
            //releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //releaseAdsLoader();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (grantResults.length == 0) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //initializePlayer();
        } else {
            showToast(R.string.storage_permission_denied);
            finish();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //return super.dispatchKeyEvent(event);
        //return playerView_1.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
        playerView_1.dispatchKeyEvent(event);
        playerView_2.dispatchKeyEvent(event);
        playerView_3.dispatchKeyEvent(event);
        return playerView_4.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void preparePlayback() {

    }

    @Override
    public void onVisibilityChange(int visibility) {

    }

    private void initializePlayer(int index){
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(FourPlayerActivity.this);
        player.setPlayWhenReady(true);
        Intent intent = getIntent();
        Uri uri = null;// = intent.getData();
        switch (index){
            case 1:
                playerView_1.setPlayer(player);
                playerView_1.setPlaybackPreparer(this);
                uri = uris[0];
                break;
            case 2:
                playerView_2.setPlayer(player);
                playerView_2.setPlaybackPreparer(this);
                uri = uris[1];
                break;
            case 3:
                playerView_3.setPlayer(player);
                playerView_3.setPlaybackPreparer(this);
                uri = uris[2];
                break;
            case 4:
                playerView_4.setPlayer(player);
                playerView_4.setPlaybackPreparer(this);
                uri = uris[3];
                break;
        }

        /*Intent intent = getIntent();
        Uri uri = intent.getData();*/
        Log.d("xxxxxxxx", uri.getPath());

        if(uri != null){
            player.prepare(buildMediaSource(uri));
        }
        /*player.prepare(buildMediaSource(
                Uri.parse("https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8")));*/
    }

    /** Returns a new DataSource factory. */
    private DataSource.Factory buildDataSourceFactory() {
        //return ((DemoApplication) getApplication()).buildDataSourceFactory();
        return buildDataSourceFactory(null);
    }

    private DataSource.Factory buildDataSourceFactory(Uri uri) {
/*      @ContentType int type = Util.inferContentType(uri, null);
      if(type == C.TYPE_RTMP){
          return  new RtmpDataSourceFactory();
      }*/
        if(uri != null){
            String path = uri.getPath();
            if(path != null && path.startsWith("rtmp:")){
                return  new RtmpDataSourceFactory();
            }
        }

        return ((DemoApplication) getApplication()).buildDataSourceFactory();
    }

    private MediaSource buildMediaSource(Uri uri) {
        return buildMediaSource(uri, null);
    }

    private MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
        DataSource.Factory dataSourceFactory = buildDataSourceFactory(uri);

        DownloadRequest downloadRequest =
                ((DemoApplication) getApplication()).getDownloadTracker().getDownloadRequest(uri);
        if (downloadRequest != null) {
            return DownloadHelper.createMediaSource(downloadRequest, dataSourceFactory);
        }
        @C.ContentType int type = Util.inferContentType(uri, overrideExtension);
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

        @Override
        public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
            String errorString = getString(R.string.error_generic);
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                Exception cause = e.getRendererException();
                if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                            (MediaCodecRenderer.DecoderInitializationException) cause;
                    if (decoderInitializationException.decoderName == null) {
                        if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                            errorString = getString(R.string.error_querying_decoders);
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            errorString =
                                    getString(
                                            R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                        } else {
                            errorString =
                                    getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                        }
                    } else {
                        errorString =
                                getString(
                                        R.string.error_instantiating_decoder,
                                        decoderInitializationException.decoderName);
                    }
                }
            }
            return Pair.create(0, errorString);
        }
    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void clearStartPosition() {
        startAutoPlay = true;
        startWindow = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }
}
