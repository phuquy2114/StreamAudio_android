package com.uits.streammicaudio;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class AudioStreamer {
    private static final String LOG_TAG = "AudioStreamer";

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean isRecording = false;

    public void startStreaming(Context context , final String serverAddress, final int serverPort) {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        final byte[] buffer = new byte[bufferSize];

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(serverAddress, serverPort);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                    audioRecord.startRecording();
                    isRecording = true;

                    while (isRecording) {
                        int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    audioRecord.stop();
                    audioRecord.release();
                    outputStream.close();
                    socket.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error during audio streaming", e);
                }
            }
        }).start();
    }

    public void stopStreaming() {
        isRecording = false;
    }
}
