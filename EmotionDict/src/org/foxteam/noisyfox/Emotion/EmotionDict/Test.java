package org.foxteam.noisyfox.Emotion.EmotionDict;

import org.foxteam.noisyfox.Emotion.Core.Word;

import java.io.IOException;
import java.util.Scanner;

/**
 * Created by Noisyfox on 14-3-1.
 */
public class Test {
    public static void main(String[] args) {
        EmotionDict ed = new EmotionDict();
        try {
            /*
            File modelF = new File("I:\\Documents\\情感分析\\词典\\情感词汇本体\\model.svm");
            if(modelF.exists()){
                ed.loadDict("I:\\Documents\\情感分析\\词典\\情感词汇本体\\情感词汇本体-UTF8.csv", modelF.getAbsolutePath());
            }else {
                ed.loadDict("I:\\Documents\\情感分析\\词典\\情感词汇本体\\情感词汇本体-UTF8.csv");
                ed.saveModel(modelF.getAbsolutePath());
            }
            ed.loadEmotionDict("I:\\Documents\\情感分析\\表情-UTF8.csv");
            */
            ed.loadDefaultDict();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Scanner in = new Scanner(System.in);

        while(true){
            Word w = ed.getWord(in.nextLine());
            w = null;
        }
    }
}
