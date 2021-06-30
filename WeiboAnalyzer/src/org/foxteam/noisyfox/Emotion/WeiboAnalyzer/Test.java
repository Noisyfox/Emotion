package org.foxteam.noisyfox.Emotion.WeiboAnalyzer;

import org.foxteam.noisyfox.Emotion.Core.IAnalyzer;
import org.foxteam.noisyfox.Emotion.EmotionDict.EmotionDict;

import java.io.IOException;

/**
 * Created by Noisyfox on 14-3-6.
 */
public class Test {
    public static void main(String[] args){
        EmotionDict dict = new EmotionDict();
        try {
            dict.loadDefaultDict();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        IAnalyzer analyzer = new Analyzer();
        analyzer.setEmotionDict(dict);
        double r = analyzer.analyze("我也很期待的呢 //@夏普智能电视:#4G来了#期待普及！求便宜资费！");

        r = 0;
    }
}
