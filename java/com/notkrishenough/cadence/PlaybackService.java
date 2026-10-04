package com.notkrishenough.cadence;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.io.IOException;

public final class PlaybackService extends Service {
    private static final String CHANNEL = "cadence_playback";
    private static final String ACTION_PLAY = "com.notkrishenough.cadence.PLAY";
    private static final String ACTION_PAUSE = "com.notkrishenough.cadence.PAUSE";
    private static final String ACTION_STOP = "com.notkrishenough.cadence.STOP";

    private MediaPlayer player;
    private MediaSession session;
    private String title = "Cadence";
    private String artist = "Music";
    private boolean prepared = false;

    public static void startPlayback(Context context, String path, String title, String artist) {
        Intent i = new Intent(context, PlaybackService.class);
        i.setAction(ACTION_PLAY);
        i.putExtra("path", path);
        i.putExtra("title", title);
        i.putExtra("artist", artist);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }
    public static void pausePlayback(Context context) {
        context.startService(new Intent(context, PlaybackService.class).setAction(ACTION_PAUSE));
    }
    public static void resumePlayback(Context context) {
        context.startService(new Intent(context, PlaybackService.class).setAction(ACTION_PLAY));
    }
    public static void stopPlayback(Context context) {
        context.startService(new Intent(context, PlaybackService.class).setAction(ACTION_STOP));
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        session = new MediaSession(this, "Cadence");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopSelf(); }
        });
        session.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PAUSE.equals(action)) {
                pause();
            } else if (ACTION_PLAY.equals(action)) {
                String path = intent.getStringExtra("path");
                if (path != null) {
                    title = intent.getStringExtra("title");
                    artist = intent.getStringExtra("artist");
                    startTrack(path);
                } else {
                    resume();
                }
            }
        }
        promoteToForeground();
        return START_STICKY;
    }

    private void startTrack(String path) {
        releasePlayer();
        player = new MediaPlayer();
        try {
            player.setDataSource(path);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                updateState(true);
                promoteToForeground();
            });
            player.setOnCompletionListener(mp -> updateState(false));
            player.prepareAsync();
        } catch (IOException e) {
            releasePlayer();
        }
    }

    private void pause() {
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
            updateState(false);
            promoteToForeground();
        }
    }
    private void resume() {
        if (player != null && prepared) {
            player.start();
            updateState(true);
            promoteToForeground();
        }
    }

    private void updateState(boolean playing) {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState ps = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, player != null ? player.getCurrentPosition() : 0, 1.0f)
                .build();
        session.setPlaybackState(ps);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Cadence")
                .build();
        session.setMetadata(metadata);
    }

    private void promoteToForeground() {
        boolean isPlaying = player != null && prepared && player.isPlaying();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0));

        Intent toggle = new Intent(this, PlaybackService.class)
                .setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent pi = PendingIntent.getService(this, 1, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play", pi).build());
        startForeground(1001, b.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL, "Cadence playback", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Cadence music playback controls");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
