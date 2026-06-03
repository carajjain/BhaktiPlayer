package com.bhakti.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    TextView tvDisplay, tvNowPlaying, tvFolder;
    Button btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9;
    Button btnClear, btnPlay, btnFolder;
    Button btnPrev, btnNext, btnPauseResume, btnRewind, btnFastFwd, btnStop;
    SeekBar seekVolume;

    MediaPlayer mediaPlayer;
    SharedPreferences prefs;

    List<DocumentFile> songFiles = new ArrayList<>();
    int currentIndex = -1;
    String inputStr = "";
    boolean isPaused = false;

    // Fast forward / rewind step in milliseconds
    static final int SEEK_STEP_MS = 10000; // 10 seconds
    static final int FOLDER_PICK_CODE = 101;

    // Handler to keep updating display with current song number while playing
    Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("BhaktiPrefs", MODE_PRIVATE);

        // Bind views
        tvDisplay       = findViewById(R.id.tvDisplay);
        tvNowPlaying    = findViewById(R.id.tvNowPlaying);
        tvFolder        = findViewById(R.id.tvFolder);
        btn0            = findViewById(R.id.btn0);
        btn1            = findViewById(R.id.btn1);
        btn2            = findViewById(R.id.btn2);
        btn3            = findViewById(R.id.btn3);
        btn4            = findViewById(R.id.btn4);
        btn5            = findViewById(R.id.btn5);
        btn6            = findViewById(R.id.btn6);
        btn7            = findViewById(R.id.btn7);
        btn8            = findViewById(R.id.btn8);
        btn9            = findViewById(R.id.btn9);
        btnClear        = findViewById(R.id.btnClear);
        btnPlay         = findViewById(R.id.btnPlay);
        btnFolder       = findViewById(R.id.btnFolder);
        btnPrev         = findViewById(R.id.btnPrev);
        btnNext         = findViewById(R.id.btnNext);
        btnPauseResume  = findViewById(R.id.btnPauseResume);
        btnRewind       = findViewById(R.id.btnRewind);
        btnFastFwd      = findViewById(R.id.btnFastFwd);
        btnStop         = findViewById(R.id.btnStop);
        seekVolume      = findViewById(R.id.seekVolume);

        // ── Number buttons ────────────────────────────────────────────────────
        View.OnClickListener numClick = v -> {
            String digit = ((Button) v).getText().toString();
            if (inputStr.length() >= 3) inputStr = "";
            inputStr += digit;
            tvDisplay.setText(inputStr);
            if (inputStr.length() == 3) {
                new Handler(Looper.getMainLooper()).postDelayed(this::playCurrent, 400);
            }
        };
        btn0.setOnClickListener(numClick);
        btn1.setOnClickListener(numClick);
        btn2.setOnClickListener(numClick);
        btn3.setOnClickListener(numClick);
        btn4.setOnClickListener(numClick);
        btn5.setOnClickListener(numClick);
        btn6.setOnClickListener(numClick);
        btn7.setOnClickListener(numClick);
        btn8.setOnClickListener(numClick);
        btn9.setOnClickListener(numClick);

        // ── Backspace ─────────────────────────────────────────────────────────
        btnClear.setOnClickListener(v -> {
            if (inputStr.length() > 0)
                inputStr = inputStr.substring(0, inputStr.length() - 1);
            tvDisplay.setText(inputStr.isEmpty() ? "—" : inputStr);
        });

        // ── Play (by number) ──────────────────────────────────────────────────
        btnPlay.setOnClickListener(v -> playCurrent());

        // ── Pause / Resume ────────────────────────────────────────────────────
        btnPauseResume.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            if (isPaused) {
                mediaPlayer.start();
                isPaused = false;
                btnPauseResume.setText("⏸");
                updateNowPlaying();
            } else {
                mediaPlayer.pause();
                isPaused = true;
                btnPauseResume.setText("▶");
                tvNowPlaying.setText("⏸  Paused — " + getCurrentSongLabel());
            }
        });

        // ── Stop ──────────────────────────────────────────────────────────────
        btnStop.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            isPaused = false;
            btnPauseResume.setText("⏸");
            tvNowPlaying.setText("⏹  Stopped");
            tvDisplay.setText(currentIndex >= 0
                    ? String.valueOf(extractNum(songFiles.get(currentIndex).getName()))
                    : "—");
        });

        // ── Previous song ─────────────────────────────────────────────────────
        btnPrev.setOnClickListener(v -> {
            if (songFiles.isEmpty()) return;
            int idx = currentIndex <= 0 ? songFiles.size() - 1 : currentIndex - 1;
            playAtIndex(idx);
        });

        // ── Next song ─────────────────────────────────────────────────────────
        btnNext.setOnClickListener(v -> {
            if (songFiles.isEmpty()) return;
            int idx = (currentIndex + 1) % songFiles.size();
            playAtIndex(idx);
        });

        // ── Rewind 10 seconds ─────────────────────────────────────────────────
        btnRewind.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            int pos = mediaPlayer.getCurrentPosition() - SEEK_STEP_MS;
            mediaPlayer.seekTo(Math.max(pos, 0));
        });

        // ── Fast Forward 10 seconds ───────────────────────────────────────────
        btnFastFwd.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            int pos = mediaPlayer.getCurrentPosition() + SEEK_STEP_MS;
            int dur = mediaPlayer.getDuration();
            if (pos >= dur) {
                // Jump to next song if fast forward goes past end
                int idx = (currentIndex + 1) % songFiles.size();
                playAtIndex(idx);
            } else {
                mediaPlayer.seekTo(pos);
            }
        });

        // ── Volume slider ─────────────────────────────────────────────────────
        seekVolume.setProgress(80);
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (mediaPlayer != null) mediaPlayer.setVolume(p / 100f, p / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // ── Folder button ─────────────────────────────────────────────────────
        btnFolder.setOnClickListener(v -> openFolderPicker());

        // ── Restore saved folder on launch ────────────────────────────────────
        String savedUri = prefs.getString("folderUri", null);
        if (savedUri != null) {
            loadSongsFromUri(Uri.parse(savedUri), true);
        }
    }

    // ── Open system folder picker ─────────────────────────────────────────────
    void openFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, FOLDER_PICK_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FOLDER_PICK_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            prefs.edit().putString("folderUri", uri.toString()).apply();
            loadSongsFromUri(uri, false);
        }
    }

    // ── Load audio files from folder ──────────────────────────────────────────
    void loadSongsFromUri(Uri uri, boolean silent) {
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
            if (dir == null || !dir.exists()) {
                if (!silent) toast("Cannot open folder");
                return;
            }
            DocumentFile[] files = dir.listFiles();
            songFiles.clear();
            for (DocumentFile f : files) {
                if (f.isFile() && isAudio(f.getName())) songFiles.add(f);
            }
            Collections.sort(songFiles,
                    (a, b) -> extractNum(a.getName()) - extractNum(b.getName()));

            String name = dir.getName() != null ? dir.getName() : "Folder";
            tvFolder.setText("📁 " + name + " · " + songFiles.size() + " songs");
            btnFolder.setText("📁 Change Folder");
            if (!silent) toast("✓ " + songFiles.size() + " songs loaded");
        } catch (Exception e) {
            if (!silent) toast("Error loading folder");
        }
    }

    // ── Play song by typed number ─────────────────────────────────────────────
    void playCurrent() {
        if (inputStr.isEmpty()) { toast("Enter a song number"); return; }
        if (songFiles.isEmpty()) { toast("Choose song folder first"); return; }
        int num;
        try { num = Integer.parseInt(inputStr); } catch (NumberFormatException e) { return; }
        inputStr = "";
        int idx = findIndexByNum(num);
        if (idx == -1) {
            tvDisplay.setText("?");
            toast("Song " + num + " not found");
            new Handler(Looper.getMainLooper()).postDelayed(() -> tvDisplay.setText("—"), 1500);
            return;
        }
        playAtIndex(idx);
    }

    // ── Core play by array index ──────────────────────────────────────────────
    void playAtIndex(int idx) {
        if (idx < 0 || idx >= songFiles.size()) return;
        DocumentFile song = songFiles.get(idx);
        currentIndex = idx;
        isPaused = false;
        btnPauseResume.setText("⏸");

        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, song.getUri());
            // Apply current volume
            float vol = seekVolume.getProgress() / 100f;
            mediaPlayer.setVolume(vol, vol);
            mediaPlayer.prepare();
            mediaPlayer.start();

            updateNowPlaying();

            // Auto-play next (circular) when done
            mediaPlayer.setOnCompletionListener(mp -> {
                int next = (currentIndex + 1) % songFiles.size();
                playAtIndex(next);
            });

        } catch (Exception e) {
            toast("Cannot play song");
        }
    }

    // ── Update "Now Playing" label ─────────────────────────────────────────────
    void updateNowPlaying() {
        if (currentIndex < 0 || currentIndex >= songFiles.size()) return;
        int num = extractNum(songFiles.get(currentIndex).getName());
        String clean = cleanFileName(songFiles.get(currentIndex).getName());
        tvDisplay.setText(String.valueOf(num));
        tvNowPlaying.setText("▶  #" + num + "   " + clean);
    }

    String getCurrentSongLabel() {
        if (currentIndex < 0 || currentIndex >= songFiles.size()) return "";
        int num = extractNum(songFiles.get(currentIndex).getName());
        return "#" + num + " " + cleanFileName(songFiles.get(currentIndex).getName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    int findIndexByNum(int num) {
        for (int i = 0; i < songFiles.size(); i++)
            if (extractNum(songFiles.get(i).getName()) == num) return i;
        return -1;
    }

    boolean isAudio(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".ogg")
                || n.endsWith(".wav") || n.endsWith(".aac") || n.endsWith(".flac");
    }

    int extractNum(String name) {
        if (name == null) return 0;
        Matcher m = Pattern.compile("(\\d+)").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    String cleanFileName(String name) {
        if (name == null) return "";
        return name.replaceAll("^\\d+[\\s\\-_.]*", "").replaceAll("\\.[^.]+$", "");
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
    }
}
