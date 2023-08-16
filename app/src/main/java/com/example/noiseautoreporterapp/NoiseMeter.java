package com.example.noiseautoreporterapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class NoiseMeter {
    private static final String [] RECORDER_PERMISSION = {
            Manifest.permission.RECORD_AUDIO
    };

    private static final int RECORDER_SAMPLE_DURATION = 5; // record for 5 seconds when noise is found
    private static final int RECORDER_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final short RECORDER_CHANNEL_NUM = 1;
    private static final int RECORDER_SAMPLE_RATE = 44100; // or, 8000
    private static final int RECORDER_SAMPLE_SIZE = RECORDER_SAMPLE_DURATION * RECORDER_SAMPLE_RATE; // record for 5 seconds when noise is found
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final short RECORDER_AUDIO_PCM_FORMAT = 1;
    private static final short RECORDER_AUDIO_MAX_AMPLITUDE = Short.MAX_VALUE;
    private static final short RECORDER_AUDIO_REFERENCE_AMPLITUDE = 50; // TODO: depending on phone, need calibration
    private static final int BUFFER_ELEMENTS_TO_REC = 1024;
    private static final int BYTES_PER_SAMPLE = 2; // 2 bytes in 16bit format
    private static final int BITS_PER_SAMPLE = BYTES_PER_SAMPLE * 8; // 16 bits in 32bit format
    private static final int RECORDER_DATA_BYTE_SIZE = BYTES_PER_SAMPLE * RECORDER_SAMPLE_SIZE;
    private AudioRecord mRecorder = null;
    private NoiseThresholdController mNoiseThresholdController = null;
    private NoiseRecorder mNoiseRecorder = null;
    private boolean mIsListening = false;
    private double curNoiseDB = 0.0;
    private Thread recordingThread = null;
    Context mContext = null;
    private class SaveConfig {
        public boolean isSavingFile = false;
        public String recordKey = "";
        public int sampleNum = 0;

        public DataOutputStream dataOutputStream = null;

        void prepareFile(String outputFileName) {
            try {

                File outputFile = createWAVFile(outputFileName);

                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                this.dataOutputStream = new DataOutputStream(fileOutputStream);
                saveWavHeader(this.dataOutputStream);
                Log.i("sound meter", "Preparing file now! " + (this.dataOutputStream == null));
            } catch (IOException e) {
                Log.e("sound meter", "Data output stream creation failed");
                e.printStackTrace();
            }
        }
        void reset() throws IOException {
            if (dataOutputStream != null) dataOutputStream.close();
            isSavingFile = false;
            recordKey = "";
            sampleNum = 0;
            dataOutputStream = null;
        }
    }

    public NoiseMeter(Context context, NoiseThresholdController noiseThresholdController, NoiseRecorder noiseRecorder) {
        this.mNoiseThresholdController = noiseThresholdController;
        this.mNoiseRecorder = noiseRecorder;
        this.mContext = context;
    }

    public boolean hasPermissions() {
        for (String permission : RECORDER_PERMISSION) {
            if (ActivityCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public String[] getPermissions() {
        return RECORDER_PERMISSION;
    }

    public double getCurNoiseLevel() { return curNoiseDB; }

    public boolean isListening() { return mIsListening; }

    private double convertToDB(int amplitude) {
        final double amplitudeDB = 20 * Math.log10((double) amplitude / RECORDER_AUDIO_REFERENCE_AMPLITUDE);
        return (double) (Math.round(amplitudeDB * 100) / 100);
    }
    private File createWAVFile(String fname) {

        File saveDir = new File(this.mContext.getExternalFilesDir(null),"saved_noise_audio");

        Log.i("makedir","saveDir"+saveDir.toString()+" "+saveDir.exists());
        if (!saveDir.exists()) {
            boolean ret = saveDir.mkdirs();
            Log.i("makedir","ret"+ret);
        }
        File file = new File(saveDir, fname + ".wav");

        try
        {
            file.createNewFile();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Log.i("makedir","saveFile "+file.toString()+" "+file.exists());
        return file;
    }
    private void saveWavHeader(DataOutputStream outputStream) throws IOException {
        // TODO: fix this bug
        // WAV file format header
        outputStream.writeBytes("RIFF"); // 4
        outputStream.writeInt(Integer.reverseBytes(44 + RECORDER_DATA_BYTE_SIZE));  // 8
        outputStream.writeBytes("WAVE"); // 12
        outputStream.writeBytes("fmt "); // 16
        outputStream.writeInt(Integer.reverseBytes(16));  // 20 Size of the format chunk
        outputStream.writeShort(Short.reverseBytes((short) RECORDER_AUDIO_PCM_FORMAT)); // 22
        outputStream.writeShort(Short.reverseBytes((short) RECORDER_CHANNEL_NUM)); // 24
        outputStream.writeInt(Integer.reverseBytes(RECORDER_SAMPLE_RATE)); // 28
        outputStream.writeInt(Integer.reverseBytes(RECORDER_SAMPLE_RATE * RECORDER_CHANNEL_NUM * BYTES_PER_SAMPLE));  // 32 Byte rate
        outputStream.writeShort(Short.reverseBytes((short) (RECORDER_CHANNEL_NUM * BYTES_PER_SAMPLE))); // 34
        outputStream.writeShort(Short.reverseBytes((short) (BITS_PER_SAMPLE)));  // 36 Bits per sample
        outputStream.writeBytes("data"); // 40
        outputStream.writeInt(Integer.reverseBytes(RECORDER_DATA_BYTE_SIZE));  // 44 Placeholder for the total size of the audio data
    }
    // this function does a background listening for noise to happen
    private void listenForNoise() {

        SaveConfig saveConfig = new SaveConfig();
        short[] audioBuffer = new short[BUFFER_ELEMENTS_TO_REC];
        while (mIsListening) {

            final int bytesRead = this.mRecorder.read(audioBuffer, 0, audioBuffer.length);
            if (bytesRead > 0) {
                double maxNoiseDB = Double.MIN_VALUE;
                for (int i = 0; i < bytesRead; i++) {

                    final double tempNoiseDB = convertToDB(Math.abs(audioBuffer[i]));
                    if (tempNoiseDB > maxNoiseDB) {
                        maxNoiseDB = tempNoiseDB;
                    }
                }

                this.curNoiseDB = maxNoiseDB;
                if (!saveConfig.isSavingFile && this.curNoiseDB > this.mNoiseThresholdController.getNoiseThreshold()) {
                    saveConfig.recordKey = this.mNoiseRecorder.addRecord(this.curNoiseDB);
                    // check if record key has error
                    if (!saveConfig.recordKey.equals(NoiseRecorder.INVALID_RECORD_ERROR)){
                        saveConfig.isSavingFile = true;
                        saveConfig.prepareFile(saveConfig.recordKey.replace(":","."));
                        Log.i("sound meter","prepare file now! "+(saveConfig.dataOutputStream == null));
                    }
                }

                if (saveConfig.isSavingFile) {
                    try {
                        for (int i = 0; i < bytesRead; i++) {
                            Log.i("sound meter","saving file now! "+bytesRead+" - "+saveConfig.sampleNum+"/"+RECORDER_SAMPLE_SIZE);
                            if (saveConfig.dataOutputStream != null) {
                                saveConfig.dataOutputStream.writeShort(Short.reverseBytes(audioBuffer[i]));
                                saveConfig.sampleNum += 1;
                                if (saveConfig.sampleNum >= RECORDER_SAMPLE_SIZE) {
                                    saveConfig.reset();
                                    saveConfig.isSavingFile = false;
                                    Log.i("sound meter","noise recording done!");
                                    break;
                                }
                            } else {
                                Log.w("sound meter","unexpected null data output stream during audio save");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    public boolean startListening() {

        if (!hasPermissions())
            return false;
        if (this.mIsListening)
            return false;

        this.mRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLE_RATE,
                RECORDER_CHANNEL,
                RECORDER_AUDIO_ENCODING,
                BUFFER_ELEMENTS_TO_REC * BYTES_PER_SAMPLE
        );
        this.mRecorder.startRecording();
        recordingThread = new Thread(new Runnable() {
            public void run() {
                listenForNoise();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
        this.mIsListening = true;
        return true;
    }
    public boolean stopListening() {
        if (mRecorder != null && isListening()){
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            recordingThread = null;
            this.mIsListening = false;
            return true;
        } else {
            return false;
        }
    }

}
