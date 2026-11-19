/*
 * Copyright 2026 Rosaldivo.
 * https://github.com/Rosaldivo/rosaldivo-morphe-patches
 *
 * Licensed under the GNU General Public License v3.0.
 */

package app.rosaldivo.extension.music;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.Map;

/**
 * Keeps 'Play next' and 'Add to queue' from filling an empty queue with a radio mix when the
 * autoplay setting is off.
 *
 * <p>When the queue is empty, the queue add handler starts playing the song, and the server answers
 * with a radio mix of many songs. The server does so for any request for a single song. The app
 * also asks for more songs whenever the queue gets short, so a mix that is cut off would grow back.
 *
 * <p>This class cuts the mix off the queue once it arrives, and stops the app from asking for more
 * songs for as long as the queue still starts with the song that was added.
 */
@SuppressWarnings("unused")
public final class EmptyQueueMixPatch {

    private static final String TAG = "RosaldivoPatches";

    private static final String PREFERENCES_NAME = "youtube";
    private static final String AUTOPLAY_KEY = "autoplay_enabled";

    private static final long POLL_INTERVAL_MILLIS = 150;
    /**
     * How long to wait for the song to arrive.
     */
    private static final long WAIT_FOR_SONG_MILLIS = 20_000;
    /**
     * How long to keep cutting after the song arrived, in case the mix arrives in several parts.
     */
    private static final long KEEP_CUTTING_MILLIS = 3_000;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * The queue manager of the app, found when a queue lane is requested.
     */
    private static volatile Object queueManager;

    /**
     * A song was added to an empty queue with autoplay off, and the queue is still that song's.
     */
    private static volatile boolean singleSongQueue;
    /**
     * The first song of the queue, once it arrived. Only used from the main thread.
     */
    private static volatile Object firstSong;

    private static boolean polling;
    private static long giveUpAtMillis;
    private static long stopCuttingAtMillis;

    /**
     * Injection point. Called with the queue manager each time a queue lane is requested.
     */
    public static void setQueueManager(Object queueManager) {
        EmptyQueueMixPatch.queueManager = queueManager;
    }

    /**
     * Injection point. Called when a song is added to an empty queue.
     *
     * @param presenceBits Value the app is about to test, returned unchanged.
     */
    public static int onEmptyQueueAdd(int presenceBits) {
        try {
            if (isAutoplayOff()) {
                singleSongQueue = true;
                firstSong = null;
                handler.post(EmptyQueueMixPatch::startPolling);
            }
        } catch (Exception ex) {
            Log.e(TAG, "onEmptyQueueAdd failure", ex);
        }
        return presenceBits;
    }

    /**
     * Injection point. Called before the app asks for more songs for the queue.
     *
     * @param continuationType Type of the request: next page, previous page or radio.
     * @param continuations    The continuations the app knows of. Cleared when the request is dropped,
     *                         so the queue does not wait for more songs.
     * @return If the request is to be dropped.
     */
    public static boolean blockContinuation(Object continuationType, Map<?, ?> continuations) {
        if (!isSingleSongQueue()) return false;

        Log.d(TAG, "Dropped the request for " + continuationType);
        if (continuations != null) continuations.clear();
        return true;
    }

    /**
     * Injection point. Called when the queue is asked to load more songs.
     *
     * @return If the queue is the single song that was added, so no more songs are wanted.
     */
    public static boolean isSingleSongQueue() {
        try {
            if (!singleSongQueue) return false;

            Object manager = queueManager;
            if (manager != null && firstSong != null) {
                Object lane = getLane(manager, 0);
                if (laneSize(lane) == 0 || laneGet(lane, 0) != firstSong) {
                    // The queue is another one now.
                    singleSongQueue = false;
                    Log.d(TAG, "The queue changed");
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "isSingleSongQueue failure", ex);
            singleSongQueue = false;
            return false;
        }
    }

    private static void startPolling() {
        giveUpAtMillis = SystemClock.uptimeMillis() + WAIT_FOR_SONG_MILLIS;
        stopCuttingAtMillis = 0;
        if (!polling) {
            polling = true;
            handler.postDelayed(EmptyQueueMixPatch::poll, POLL_INTERVAL_MILLIS);
        }
        Log.d(TAG, "Waiting to cut the mix off the queue");
    }

    private static void poll() {
        try {
            long now = SystemClock.uptimeMillis();
            boolean songArrived = firstSong != null;
            if (!singleSongQueue || (songArrived ? now > stopCuttingAtMillis : now > giveUpAtMillis)) {
                polling = false;
                if (!songArrived) singleSongQueue = false;
                Log.d(TAG, "Stopped cutting");
                return;
            }

            Object manager = queueManager;
            if (manager != null) {
                Object lane = getLane(manager, 0);
                int size = laneSize(lane);
                if (size >= 1 && firstSong == null) {
                    firstSong = laneGet(lane, 0);
                    stopCuttingAtMillis = now + KEEP_CUTTING_MILLIS;
                }
                if (size > 1) {
                    for (int i = size - 1; i >= 1; i--) {
                        laneRemove(lane, i);
                    }
                    Log.d(TAG, "Cut " + (size - 1) + " songs of the mix off the queue");
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "poll failure", ex);
            polling = false;
            singleSongQueue = false;
            return;
        }
        handler.postDelayed(EmptyQueueMixPatch::poll, POLL_INTERVAL_MILLIS);
    }

    /**
     * Replaced by the patch. Returns a lane of the queue: 0 is the songs, 1 is the autoplay songs.
     */
    private static Object getLane(Object queueManager, int lane) {
        throw new IllegalStateException("Not replaced by the patch");
    }

    /**
     * Replaced by the patch.
     */
    private static int laneSize(Object lane) {
        throw new IllegalStateException("Not replaced by the patch");
    }

    /**
     * Replaced by the patch.
     */
    private static Object laneGet(Object lane, int index) {
        throw new IllegalStateException("Not replaced by the patch");
    }

    /**
     * Replaced by the patch.
     */
    private static void laneRemove(Object lane, int index) {
        throw new IllegalStateException("Not replaced by the patch");
    }

    /**
     * The setting is stored once per account, as '[account id]_autoplay_enabled'.
     * Autoplay counts as off only if every stored value is off, so a mixed state of several accounts
     * keeps the app's behavior.
     */
    private static boolean isAutoplayOff() throws Exception {
        Application application = (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null);
        SharedPreferences preferences = application.getSharedPreferences(PREFERENCES_NAME, 0);

        boolean found = false;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!(key.equals(AUTOPLAY_KEY) || key.endsWith("_" + AUTOPLAY_KEY))) continue;
            if (!(entry.getValue() instanceof Boolean)) continue;

            if ((Boolean) entry.getValue()) return false;
            found = true;
        }

        Log.d(TAG, "Autoplay off: " + found);
        return found;
    }
}
