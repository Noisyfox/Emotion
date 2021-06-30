package org.foxteam.noisyfox.Emotion.EmotionDict.Util;

import java.util.Scanner;

/**
 * Created by Noisyfox on 14-2-28.
 * 一个小转换器
 */
public class Converter {

    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);

        StringBuilder sb1 = new StringBuilder(), sb2 = new StringBuilder();

        sb1.append("{");
        sb2.append("{");

        while (s.hasNext()) {
            sb1.append("\"");
            sb1.append(s.next());
            sb1.append("\",");
            sb2.append(s.nextDouble());
            sb2.append(',');
        }

        sb1.append("}");
        sb2.append("}");

        System.out.println(sb1.toString());
        System.out.println(sb2.toString());
    }

}
