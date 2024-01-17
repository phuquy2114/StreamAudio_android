package com.uits.streammicaudio;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.uits.streammicaudio.component.RecordWaveView;

public class MainActivity extends AppCompatActivity {

    // Variables
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    private static final String TAG = "Aufnahme";
    private AudioRecord recorder = null;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    private int bufferSize;
    private Thread recordingThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestAudioPermissions();
        SetupRecordButtons();  //MOVE THIS OUTSIDE ;)
    }

    //CUSTOM FUNCTIONS
    //below are all the custom function to perform the operations

    public void SetupRecordButtons() {
        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setOnClickListener(v -> {
            startStreamAudio();
        });

        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setOnClickListener(v -> {
            stopStreaming();
        });
        stopButton.setEnabled(false);
    }

    @SuppressLint("SetTextI18n")
    void startStreamAudio() {
        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setEnabled(false);
        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setEnabled(true);
        TextView indicatorLabel = (TextView) findViewById(R.id.indicatorLabel);
        indicatorLabel.setText("I'm Recording ...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    audioRecord.startRecording();
                    audioTrack.play();

                    byte[] buffer = new byte[bufferSize];

                    while (true) {
                        int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                        audioTrack.write(buffer, 0, bytesRead);
                    }
                } catch (Exception e) {
                    //Log.e(LOG_TAG, "Error during audio streaming", e);
                }
            }
        }).start();

    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            //Go ahead with recording audio now
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            audioTrack = new AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                    //Go ahead with recording audio now
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            bufferSize
                    );

                    audioTrack = new AudioTrack(
                            android.media.AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AUDIO_FORMAT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                    );
                } else {
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;

            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void stopStreaming() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setEnabled(true);
        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setEnabled(false);
        TextView indicatorLabel = (TextView) findViewById(R.id.indicatorLabel);
        indicatorLabel.setText("Halted");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}