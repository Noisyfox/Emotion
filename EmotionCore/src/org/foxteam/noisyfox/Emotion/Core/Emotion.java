package org.foxteam.noisyfox.Emotion.Core;

/**
 * Created by Noisyfox on 14-2-28.
 * 情感类
 */
public class Emotion {

    /**
     * 词汇极性
     * NEUTRAL 中性
     * COMMENDATORY 褒义
     * DEROGATORY 贬义
     * AMPHOTERIC 两性
     */
    public static final int EMOTION_POL_NEUTRAL = 0;
    public static final int EMOTION_POL_COMMENDATORY = 1;
    public static final int EMOTION_POL_DEROGATORY = 2;
    public static final int EMOTION_POL_AMPHOTERIC = 3;

    public static final String[] EMOTIONS_DES = {"PA", "PE", "PD", "PH", "PG", "PB", "PK",
            "NA", "NB", "NJ", "NH", "PF", "NI", "NC", "NG", "NE", "ND", "NN", "NK", "NL", "PC"};

    public static int parseEmotion(String desc) {
        for (int i = 0; i < EMOTIONS_DES.length; i++) {
            if (EMOTIONS_DES[i].equals(desc)) return i + 1;
        }
        throw new IllegalArgumentException("Unknown emotion \"" + desc + "\"");
    }

    public static String parseEmotion(int emotion) {
        if (emotion < 1 || emotion > EMOTIONS_DES.length) throw new IllegalArgumentException();

        return EMOTIONS_DES[emotion - 1];
    }

    private int emotion;
    private int strength;
    private int polarity;
    private double confidence;

    public void setEmotion(int emotion) {
        if (emotion <= 0 || emotion > EMOTIONS_DES.length) throw new IndexOutOfBoundsException();

        this.emotion = emotion;
    }

    public void setEmotion(String emotionDes) {
        this.emotion = parseEmotion(emotionDes);
    }

    public void setStrength(int strength) {
        if (strength < 0 || strength > 9) throw new IndexOutOfBoundsException();

        this.strength = strength;
    }

    public void setPolarity(int polarity) {
        if (polarity < 0 || polarity > 3) throw new IndexOutOfBoundsException();

        this.polarity = polarity;
    }

    public void setConfidence(double confidence) {
        if (confidence < 0) throw new IllegalArgumentException();

        this.confidence = confidence;
    }

    public int getEmotion() {
        return emotion;
    }

    public int getStrength() {
        return strength;
    }

    public int getPolarity() {
        return polarity;
    }

    public double getConfidence() {
        return confidence;
    }

}
