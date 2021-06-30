package org.foxteam.noisyfox.Emotion.Core;

/**
 * Created by Noisyfox on 14-3-7.
 * 微博分析器接口
 */
public interface IAnalyzer {
    void setEmotionDict(IDict dict);

    /**
     * 分析一条微博
     *
     * @param weibo 微博文本
     * @return 情感取向 >= 0 代表正向情感，否则为负面情感
     */
    double analyze(String weibo);
}
