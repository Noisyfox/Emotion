package org.foxteam.noisyfox.Emotion.Core;

import java.util.List;

/**
 * Created by Noisyfox on 14-3-7.
 * 情感词典接口
 */
public interface IDict {
    Word getWord(String word);

    Word getEmotionWord(String emotion);

    List<String> getWordsString();
}
