package org.foxteam.noisyfox.Emotion.WeiboAnalyzer;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.foxteam.noisyfox.Emotion.Core.IAnalyzer;
import org.foxteam.noisyfox.Emotion.Core.IDict;
import org.foxteam.noisyfox.Emotion.Core.Word;
import org.foxteam.noisyfox.Emotion.Core.Emotion;
import org.wltea.analyzer.cfg.DefaultConfig;
import org.wltea.analyzer.dic.Dictionary;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by Noisyfox on 14-3-5.
 * 单条微博分析器
 */
public class Analyzer implements IAnalyzer {
    private IKAnalyzer mIKAnalyzer = new IKAnalyzer(true);
    private Dictionary ikDictionary;

    private IDict mDict = null;

    public Analyzer() {
        //初始化分词器
        ikDictionary = Dictionary.initial(DefaultConfig.getInstance());
    }

    @Override
    public void setEmotionDict(IDict dict) {
        mDict = dict;
        if (mDict != null) {
            ikDictionary.addWords(mDict.getWordsString());
        }
    }

    /**
     * 一条微博记录，包含一条微博或回复正文，以及表情列表
     */
    class Record {
        String text = null;
        List<String> emotions = null;
    }

    private List<Record> weibo2Records(String weibo) {
        Stack<Record> records = new Stack<Record>();
        char[] cs = weibo.toCharArray();

        List<String> emotions = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length; i++) {
            switch (cs[i]) {
                case '@': // 去除@
                    for (; i < cs.length; i++) {
                        if (cs[i] == ' ') break;
                    }
                    break;
                case '/': // 转发分段
                    if (i + 2 < cs.length && cs[i + 1] == '/' && cs[i + 2] == '@') {
                        Record r = new Record();
                        r.text = sb.toString().trim();
                        r.emotions = emotions;
                        records.push(r);
                        emotions = new ArrayList<String>();
                        sb = new StringBuilder();
                        for (; i < cs.length; i++) {
                            if (cs[i] == ':') break;
                        }
                    } else {
                        sb.append(cs[i]);
                    }
                    break;
                case '[': // 提取表情符号
                {
                    int si = i;
                    int ei = i;
                    for (; i < cs.length; i++) {
                        if (cs[i] == ']') {
                            ei = i + 1;
                            break;
                        }
                    }
                    String mo = String.valueOf(cs, si, ei - si);
                    if (!mo.isEmpty()) emotions.add(mo);
                    break;
                }
                case '#': // 去除话题
                    for (i++; i < cs.length; i++) {
                        if (cs[i] == '#') break;
                    }
                    break;
                default:
                    sb.append(cs[i]);
                    break;
            }
        }
        Record r = new Record();
        r.text = sb.toString().trim();
        r.emotions = emotions;
        records.push(r);

        List<Record> recordsL = new ArrayList<Record>();

        while(!records.isEmpty()){
            recordsL.add(records.pop());
        }

        return recordsL;
    }

    @Override
    public double analyze(String weibo) {
        List<Record> records = weibo2Records(weibo);

        double total = 0;

        for (Record r : records) {
            double emo = 0;
            // 分析文本部分
            if (!r.text.isEmpty()) {
                TokenStream ts = null;
                try {
                    ts = mIKAnalyzer.tokenStream("", new StringReader(r.text));
                    //获取词元文本属性
                    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
                    ts.reset();
                    StringBuilder sb = new StringBuilder();
                    boolean finish = false;
                    while (!finish) {
                        String wordString;
                        if(ts.incrementToken()){
                            wordString = term.toString();
                            if(sb.length() > 0){
                                sb.append(wordString);
                                wordString = sb.toString();
                                sb = new StringBuilder();
                            } else if(wordString.length() < 2){ // 合并单字
                                sb.append(wordString);
                                continue;
                            }
                        } else if(sb.length() > 0){
                            finish = true;
                            wordString = sb.toString();
                        } else break;

                        Word word = mDict.getWord(wordString);
                        if(word != null){
                            for(Emotion e : word.emotions){
                                double te = e.getConfidence() * e.getStrength();
                                switch(e.getPolarity()){
                                    case Emotion.EMOTION_POL_COMMENDATORY:
                                        break;
                                    case Emotion.EMOTION_POL_DEROGATORY:
                                        te = -te;
                                        break;
                                    default:
                                        te = 0;
                                }
                                emo += te;
                            }
                        }
                    }
                    //关闭TokenStream（关闭StringReader）
                    ts.end();   // Perform end-of-stream operations, e.g. set the final offset.
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    //释放TokenStream的所有资源
                    if (ts != null) {
                        try {
                            ts.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            // 分析表情部分
            for(String emS : r.emotions){
                Word ew = mDict.getEmotionWord(emS);
                if(ew != null){
                    for(Emotion e : ew.emotions){
                        double te = e.getConfidence() * e.getStrength();
                        switch(e.getPolarity()){
                            case Emotion.EMOTION_POL_COMMENDATORY:
                                break;
                            case Emotion.EMOTION_POL_DEROGATORY:
                                te = -te;
                                break;
                            default:
                                te = 0;
                        }
                        emo += te;
                    }
                }
            }

            // 合并本次转发结果
            if(total == 0)total = emo;
            else if(total * emo >= 0){
                total *= 0.7;
                total += emo;
            }else{
                total *= 0.7;
                total += emo;
            }
        }

        return total;
    }

}
