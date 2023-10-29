package com.example.noiseautoreporterapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.EditText;

import androidx.core.app.ActivityCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

public class NoiseMeter {
    private static final String [] RECORDER_PERMISSION = {
            Manifest.permission.RECORD_AUDIO
    };

    private static final int DEFAULT_RECORDER_SAMPLE_DURATION = 5; // record for 5 seconds when noise is found
    private static final int RECORDER_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final short RECORDER_CHANNEL_NUM = 1;
    private static final int RECORDER_SAMPLE_RATE = 44100; // or, 8000
//    private static final int RECORDER_SAMPLE_SIZE = RECORDER_SAMPLE_DURATION * RECORDER_SAMPLE_RATE; // record for 5 seconds when noise is found
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final short RECORDER_AUDIO_PCM_FORMAT = 1;
    private static final short RECORDER_AUDIO_MAX_AMPLITUDE = Short.MAX_VALUE;
    private static final short RECORDER_AUDIO_REFERENCE_AMPLITUDE = 50; // TODO: depending on phone, need calibration
    private static final int BUFFER_ELEMENTS_TO_REC = 1024;
    private static final int BYTES_PER_SAMPLE = 2; // 2 bytes in 16bit format
    private static final int BITS_PER_SAMPLE = BYTES_PER_SAMPLE * 8; // 16 bits in 32bit format
//    private static final int RECORDER_DATA_BYTE_SIZE = BYTES_PER_SAMPLE * RECORDER_SAMPLE_SIZE;
    private AudioRecord mRecorder = null;
    private NoiseThresholdController mNoiseThresholdController = null;
    private NoiseRecorder mNoiseRecorder = null;
    private boolean mIsListening = false;
    private double curNoiseDB = 0.0;
    private Thread recordingThread = null;
    private EditText mEtUpdateFreq = null;
    private Context mContext = null;
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

    private int getRecorderSampleDuration() {
        String updateFreqStr = mEtUpdateFreq.getText().toString();
        if (!updateFreqStr.isEmpty()) {
            try {
                int updateFreqInt = Integer.parseInt(updateFreqStr);
                return Math.abs(updateFreqInt);

            } catch (NumberFormatException e) {
                return DEFAULT_RECORDER_SAMPLE_DURATION;
            }
        }
        return DEFAULT_RECORDER_SAMPLE_DURATION;
    }
    private int getRecorderSampleSize() {
        return  getRecorderSampleDuration() * RECORDER_SAMPLE_RATE;
    }
    private int getRecorderDataByteSize() {
        return getRecorderSampleSize() * BYTES_PER_SAMPLE;
    }
    private class NoiseMinMaxRecord {
        double minNoiseDB = Double.MAX_VALUE;
        double maxNoiseDB = Double.MIN_VALUE;
    }

    public NoiseMeter(Context context, EditText etUpdateFreq, NoiseThresholdController noiseThresholdController, NoiseRecorder noiseRecorder) {
        this.mNoiseThresholdController = noiseThresholdController;
        this.mNoiseRecorder = noiseRecorder;
        this.mEtUpdateFreq = etUpdateFreq;
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
        if (!Double.isInfinite(amplitudeDB)) {
            DecimalFormat df = new DecimalFormat("#.##");
            return Double.parseDouble(df.format(amplitudeDB));
        } else {
            return amplitudeDB;
        }
    }
    private File createWAVFile(String fname) {

        File saveDir = new File(this.mContext.getExternalFilesDir(null),"saved_noise_audio");

        if (!saveDir.exists()) {
            boolean ret = saveDir.mkdirs();
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
        return file;
    }
    private void saveWavHeader(DataOutputStream outputStream) throws IOException {
        // WAV file format header
        outputStream.writeBytes("RIFF"); // 4
        outputStream.writeInt(Integer.reverseBytes(44 + getRecorderDataByteSize()));  // 8
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
        outputStream.writeInt(Integer.reverseBytes(getRecorderDataByteSize()));  // 44 Placeholder for the total size of the audio data
    }

    private NoiseMinMaxRecord getMinMaxNoiseRecord(short[] audioBuffer, int bytesRead) {
        NoiseMinMaxRecord noiseMinMaxRecord = new NoiseMinMaxRecord();
        for (int i = 0; i < bytesRead; i++) {

            final double tempNoiseDB = convertToDB(Math.abs(audioBuffer[i]));
            if (tempNoiseDB > noiseMinMaxRecord.maxNoiseDB) {
                noiseMinMaxRecord.maxNoiseDB = tempNoiseDB;
            }
            if (tempNoiseDB < noiseMinMaxRecord.minNoiseDB) {
                noiseMinMaxRecord.minNoiseDB = tempNoiseDB;
            }
        }
        return noiseMinMaxRecord;
    }

    // this function does a background listening for noise
    private void listenForNoise() {
        short[] audioBuffer = new short[BUFFER_ELEMENTS_TO_REC];
        SaveConfig saveConfig = new SaveConfig();
        while (mIsListening) {
            final int bytesRead = this.mRecorder.read(audioBuffer, 0, audioBuffer.length);
            NoiseMinMaxRecord minMaxRecord = getMinMaxNoiseRecord(audioBuffer, bytesRead);
            double maxNoiseDB = minMaxRecord.maxNoiseDB;
            double minNoiseDB = minMaxRecord.minNoiseDB;

            this.curNoiseDB = maxNoiseDB;

            // check if noise level exceeds threshold while file is not being saved
            if (!saveConfig.isSavingFile && this.curNoiseDB > this.mNoiseThresholdController.getNoiseThreshold()) {
                saveConfig.recordKey = this.mNoiseRecorder.addRecord(maxNoiseDB,minNoiseDB);
                // check if record key has error
                if (!saveConfig.recordKey.equals(NoiseRecorder.INVALID_RECORD_ERROR)){
                    saveConfig.isSavingFile = true;
                }
            }

            // fake saving file, let the time elapse but do not actually save it
            if (saveConfig.isSavingFile) {

                for (int i = 0; i < bytesRead; i++) {
                    saveConfig.sampleNum += 1;
                    if (saveConfig.sampleNum >= getRecorderSampleSize()) {
                        saveConfig.isSavingFile = false;
                        break;
                    }
                }
            }
        }
    }

    // this function does a background listening for noise to happen and does a local recording of the noise
    private void listenForNoiseWithLocalRecord() {

        SaveConfig saveConfig = new SaveConfig();
        short[] audioBuffer = new short[BUFFER_ELEMENTS_TO_REC];
        while (mIsListening) {

            final int bytesRead = this.mRecorder.read(audioBuffer, 0, audioBuffer.length);
            if (bytesRead > 0) {
                NoiseMinMaxRecord minMaxRecord = getMinMaxNoiseRecord(audioBuffer, bytesRead);
                double maxNoiseDB = minMaxRecord.maxNoiseDB;
                double minNoiseDB = minMaxRecord.minNoiseDB;

                this.curNoiseDB = maxNoiseDB;
                // check if noise level exceeds threshold while file is not being saved
                if (!saveConfig.isSavingFile && this.curNoiseDB > this.mNoiseThresholdController.getNoiseThreshold()) {
                    saveConfig.recordKey = this.mNoiseRecorder.addRecord(maxNoiseDB,minNoiseDB);
                    // check if record key has error
                    if (!saveConfig.recordKey.equals(NoiseRecorder.INVALID_RECORD_ERROR)){
                        saveConfig.isSavingFile = true;
                        saveConfig.prepareFile(saveConfig.recordKey.replace(":","."));
                    }
                }

                if (saveConfig.isSavingFile) {
                    try {
                        for (int i = 0; i < bytesRead; i++) {

                            if (saveConfig.dataOutputStream != null) {
                                saveConfig.dataOutputStream.writeShort(Short.reverseBytes(audioBuffer[i]));
                                saveConfig.sampleNum += 1;
                                if (saveConfig.sampleNum >= getRecorderSampleSize()) {
                                    saveConfig.reset();
                                    saveConfig.isSavingFile = false;

                                    break;
                                }
                            } else {
                                Log.w("sound meter","unexpected null data output stream during audio save");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        saveConfig.isSavingFile = false;
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
