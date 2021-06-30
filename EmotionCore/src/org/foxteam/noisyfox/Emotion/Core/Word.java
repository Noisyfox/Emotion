package org.foxteam.noisyfox.Emotion.Core;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Noisyfox on 14-2-28.
 * 词条类
 */
public class Word {
    public static final String[] WORD_PART = {"noun", "verb", "adj", "adv", "nw", "idiom", "prep", "emotion"};

    public static int parseWordPart(String desc) {
        for (int i = 0; i < WORD_PART.length; i++) {
            if (WORD_PART[i].equals(desc)) return i;
        }
        throw new IllegalArgumentException("Unknown word part \"" + desc + "\".");
    }

    public static String parseWordPart(int part) {
        if (part < 0 || part > WORD_PART.length - 1) throw new IllegalArgumentException();

        return WORD_PART[part];
    }

    public String word = null;

    public int wordPart;
    public int meaningCount = 0;
    public int meaning = 0;
    public List<Emotion> emotions = new ArrayList<Emotion>();

}
