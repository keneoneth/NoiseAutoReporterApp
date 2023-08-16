package com.example.noiseautoreporterapp;

public class NoiseThresholdController {

    private static final int MAX_NOISE_LEVEL = 300;
    private static final int MIN_NOISE_LEVEL = 0;
    private int mNoiseLevel = 0;

    NoiseThresholdController(int noiseLevel) {
        this.mNoiseLevel = noiseLevel;
    }

    int getNoiseThreshold() {
        return mNoiseLevel;
    }
    void increaseNoiseLevel(int noiseDiff) {
        if (this.mNoiseLevel + noiseDiff > MAX_NOISE_LEVEL)
            return;
        this.mNoiseLevel += noiseDiff;
    }
    void decreaseNoiseLevel(int noiseDiff) {
        if (this.mNoiseLevel - noiseDiff < MIN_NOISE_LEVEL)
            return;
        this.mNoiseLevel -= noiseDiff;
    }
}
