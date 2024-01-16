package com.uits.streammicaudio;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    // Variables
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


        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
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

        SetupRecordButtons();  //MOVE THIS OUTSIDE ;)
    }

    //CUSTOM FUNCTIONS
    //below are all the custom function to perform the operations

    public void SetupRecordButtons() {
        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startRecording();
            }
        });

        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopRecording();
            }
        });
        stopButton.setEnabled(false);
    }

    public void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        recorder.startRecording();
        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioData();
            }

        });


        //Start recording thread
        recordingThread.start();

        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setEnabled(false);
        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setEnabled(true);
        TextView indicatorLabel = (TextView) findViewById(R.id.indicatorLabel);
        indicatorLabel.setText("recording");
    }

    public void stopRecording() {
        isRecording = false;
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;

        Button startButton = (Button) findViewById(R.id.btnStart);
        startButton.setEnabled(true);
        Button stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setEnabled(false);
        TextView indicatorLabel = (TextView) findViewById(R.id.indicatorLabel);
        indicatorLabel.setText("halted");
    }

    private void writeAudioData() {
        byte data[] = new byte[bufferSize];

        while (isRecording) {
            recorder.read(data, 0, bufferSize);
            send(data);

        }
    }

    private void send(byte[] data) {

        int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        AudioTrack at = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
                AudioTrack.MODE_STREAM
        );
        at.setPlaybackRate(SAMPLE_RATE);

        at.play();
        at.write(data, 0, bufferSize);
        at.stop();
        at.release();

    }

    private void stopStreaming() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}