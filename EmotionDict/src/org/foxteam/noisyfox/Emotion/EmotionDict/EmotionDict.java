package org.foxteam.noisyfox.Emotion.EmotionDict;

import org.foxteam.noisyfox.Emotion.Core.Emotion;
import org.foxteam.noisyfox.Emotion.Core.IDict;
import org.foxteam.noisyfox.Emotion.Core.Word;

import java.io.*;
import java.util.*;

/**
 * Created by Noisyfox on 14-2-26.
 * 情感词词典
 */
public class EmotionDict implements IDict {
    private static final Comparator<Emotion> emotionComparator = new Comparator<Emotion>() {
        @Override
        public int compare(Emotion o1, Emotion o2) {
            return o2.getStrength() - o1.getStrength();
        }
    };

    private static final String PROP_KEY_ENCODE = "EmotionDict.encode";
    private static final String PROP_KEY_COMMONDICT_PATH = "EmotionDict.CommonDict.path";
    private static final String PROP_KEY_COMMONDICT_MODEL_PATH = "EmotionDict.CommonDict.Model.path";
    private static final String PROP_KEY_COMMONDICT_MODEL_AUTOSAVE = "EmotionDict.CommonDict.Model.autoSave";
    private static final String PROP_KEY_COMMONDICT_MODEL_AUTOREFRESH = "EmotionDict.CommonDict.Model.autoRefresh";
    private static final String PROP_KEY_EMOTIONDICT_PATH = "EmotionDict.EmotionDict.path";

    private String mProp_dictEncode = "UTF-8";
    private String mProp_commonDict_path = null;
    private String mProp_commonDict_model_path = null;
    private boolean mProp_commonDict_model_autoSave = false;
    private boolean mProp_commonDict_model_autoRefresh = false;
    private String mProp_emotionDict_path = null;

    public void setFileEncode(String encode) {
        mProp_dictEncode = encode;
    }

    public void loadDefaultDict() throws IOException {
        InputStream in = getClass().getResourceAsStream("/Default.properties");
        if (in != null) {
            loadDictFromPropStream(in);
        } else {
            throw new IOException();
        }
        in.close();
    }

    public void loadDictFromProp(String propFile) throws IOException {
        InputStream is = new FileInputStream(propFile);
        loadDictFromPropStream(is);
        is.close();
    }

    private void loadDictFromPropStream(InputStream in) throws IOException {
        Properties prop = new Properties();
        prop.load(in);

        mProp_dictEncode = prop.getProperty(PROP_KEY_ENCODE, mProp_dictEncode);
        mProp_commonDict_path = prop.getProperty(PROP_KEY_COMMONDICT_PATH, mProp_commonDict_path);
        mProp_commonDict_model_path = prop.getProperty(PROP_KEY_COMMONDICT_MODEL_PATH, mProp_commonDict_model_path);
        mProp_commonDict_model_autoSave = prop.getProperty(PROP_KEY_COMMONDICT_MODEL_AUTOSAVE, mProp_commonDict_model_autoSave ? "1" : "0").equals("1");
        mProp_commonDict_model_autoRefresh = prop.getProperty(PROP_KEY_COMMONDICT_MODEL_AUTOREFRESH, mProp_commonDict_model_autoRefresh ? "1" : "0").equals("1");
        mProp_emotionDict_path = prop.getProperty(PROP_KEY_EMOTIONDICT_PATH, mProp_emotionDict_path);

        loadDict(mProp_commonDict_path, mProp_commonDict_model_path);
        loadEmotionDict(mProp_emotionDict_path);
    }

    long mCharacterCount_positive = 0;// 褒义词表中的总字数
    long mCharacterCount_negative = 0;// 贬义词表中的总字数
    HashMap<Character, EmotionCalc> mEmotionMap_character = new HashMap<Character, EmotionCalc>(); //单字情感值表
    HashMap<String, Word> mEmotionMap_assuming = new HashMap<String, Word>(); //猜测词情感值表
    HashMap<String, Word> mEmotionMap_static = new HashMap<String, Word>(); //可信词情感值表
    List<Word> mAllWords_static = new ArrayList<Word>();
    Categorizer mCategorizer = new Categorizer();

    private class EmotionCalc {
        long positive = 0;
        long negative = 0;

        double fp = 0;
        double fn = 0;
    }

    private class CompositeWord {
        String prefix = null; // 复合前缀
        String lastWord = null; // 基础词

        double composite_factor = 1; //复合情感因子
    }

    public void resetDict() {
        mCharacterCount_positive = 0;
        mCharacterCount_negative = 0;
        mEmotionMap_character.clear();
        mEmotionMap_assuming.clear();
        mEmotionMap_static.clear();
        mAllWords_static.clear();
        mCategorizer.reset();

        mEmotionMap_emotion.clear();
    }

    public void saveDict(String dictFile) {

    }

    public void saveModel(String modelFile) throws IOException {
        mCategorizer.dumpModel(modelFile);
    }

    public void loadDict(String dictFile) throws IOException {
        loadDict(dictFile, null);
    }

    public void loadDict(String dictFile, String modelFile) throws IOException {
        mProp_commonDict_path = dictFile;
        mProp_commonDict_model_path = modelFile;

        if (dictFile == null) return;

        // 1.从文件中读取出确信的情感词
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(dictFile), mProp_dictEncode));
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) continue;

                try {
                    Word w = parseWord(line);
                    Collections.sort(w.emotions, emotionComparator);

                    if (mEmotionMap_static.containsKey(w.word)) {
                        Word old_w = mEmotionMap_static.get(w.word);

                        //mEmotionMap_static.put(w.word, w);
                    } else {
                        mAllWords_static.add(w);
                        mEmotionMap_static.put(w.word, w);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Unexpected word definition: \"" + line + "\", ignored.");
                }
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }

        // 2.统计单字情感度表，并且处理部分复合情感词
        for (Word w : mAllWords_static) {
            CompositeWord cw = analysisCompositeWord(w.word);
            if (cw == null) {// 基础情感词
                putCharacterMap(w);
            } else {
                Word postWord = searchWord(cw.lastWord);
                if (postWord == null) {
                    postWord = new Word();
                    postWord.word = cw.lastWord;
                    postWord.wordPart = w.wordPart;
                    postWord.meaningCount = w.meaningCount;
                    postWord.meaning = w.meaning;

                    outer:
                    for (Emotion e : w.emotions) {
                        Emotion ae = getAdjustedEmotion(e, 1.0 / cw.composite_factor);
                        for (Emotion ee : postWord.emotions) {
                            if (ae.getEmotion() == ee.getEmotion()) {
                                if (ae.getConfidence() > ee.getConfidence()) {
                                    ee.setConfidence(ae.getConfidence());
                                    ee.setPolarity(ae.getPolarity());
                                    ee.setStrength(ae.getStrength());
                                }
                                continue outer;
                            }
                        }
                        postWord.emotions.add(ae);
                    }
                    Collections.sort(postWord.emotions, emotionComparator);
                    mEmotionMap_assuming.put(cw.lastWord, postWord);
                    putCharacterMap(postWord);
                }
            }
        }

        Set<Map.Entry<Character, EmotionCalc>> allCharacter = mEmotionMap_character.entrySet();
        for (Map.Entry<Character, EmotionCalc> c : allCharacter) {
            EmotionCalc ec = c.getValue();
            ec.fp = (double) ec.positive / (double) mCharacterCount_positive;
            ec.fn = (double) ec.negative / (double) mCharacterCount_negative;
        }

        if (modelFile != null && new File(mProp_commonDict_model_path).exists()) {// 从文件中读取分类器数据
            mCategorizer.importModel(modelFile);
        } else {
            // 3.训练分类器
            for (Word w : mAllWords_static) {
                //if (w.word.equals("道不拾遗")) {
                //    int a = 1;
                //}
                double po = 0, ne = 0;
                char[] cs;
                CompositeWord cw = analysisCompositeWord(w.word);
                double t = 0;
                if (cw == null) {// 基础情感词
                    for (Emotion e : w.emotions) {
                        double strength = e.getStrength() / 9D;
                        switch (e.getPolarity()) {
                            case Emotion.EMOTION_POL_NEUTRAL:
                            case Emotion.EMOTION_POL_AMPHOTERIC:
                                break;
                            case Emotion.EMOTION_POL_COMMENDATORY:
                                t += strength;
                                break;
                            case Emotion.EMOTION_POL_DEROGATORY:
                                t -= strength;
                                break;
                        }
                    }
                    cs = w.word.toCharArray();
                } else {
                    for (Emotion e : w.emotions) {
                        Emotion ae = getAdjustedEmotion(e, 1.0 / cw.composite_factor);
                        double strength = ae.getStrength() / 9D;
                        switch (ae.getPolarity()) {
                            case Emotion.EMOTION_POL_NEUTRAL:
                            case Emotion.EMOTION_POL_AMPHOTERIC:
                                break;
                            case Emotion.EMOTION_POL_COMMENDATORY:
                                t += strength;
                                break;
                            case Emotion.EMOTION_POL_DEROGATORY:
                                t -= strength;
                                break;
                        }
                    }
                    cs = cw.lastWord.toCharArray();
                }

                for (char c : cs) {
                    EmotionCalc ec = mEmotionMap_character.get(c);
                    if (ec != null) {
                        double fm = ec.fn + ec.fp;
                        if (fm != 0) {
                            po += ec.fp / (ec.fp + ec.fn);
                            ne += ec.fn / (ec.fp + ec.fn);
                        }
                    }
                }

                if (t >= 0) t = 1;
                else t = -1;

                mCategorizer.training(po, ne, t);
            }
            mCategorizer.commitTraining();
        }

        if (mProp_commonDict_model_autoSave && mProp_commonDict_model_path != null) {
            saveModel(mProp_commonDict_model_path);
        }
    }

    /*
    快乐(PA)   -->   悲伤(NB)   1
    安心(PE)   -->    慌(NI)   0.8
    尊敬(PD)   -->   憎恶(ND)   0.5
    赞扬(PH)   -->   贬责(NN)   1
    相信(PG)   -->   怀疑(NL)   1
    喜爱(PB)   -->   憎恶(ND)   0.9
    祝愿(PK)   -->   贬责(NN)   0.7
    愤怒(NA)   -->   安心(PE)   0.7
    悲伤(NB)   -->   快乐(PA)   0.8
    失望(NJ)   -->   快乐(PA)    0.7
     疚(NH)   -->   安心(PE)    0.8
     思(PF)   -->   安心(PE)    0.7
     慌(NI)   -->   安心(PE)    0.8
    恐惧(NC)   -->   安心(PE)    0.7
     羞(NG)   -->   快乐(PA)   0.5
    烦闷(NE)   -->   快乐(PA)    0.7
    憎恶(ND)   -->   喜爱(PB)   0.9
    贬责(NN)   -->   赞扬(PH)   1
    妒忌(NK)   -->   喜爱(PB)   0.7
    怀疑(NL)   -->   相信(PG)   0.8
    惊奇(PC)   -->   安心(PE)   0.8
     */
    private static final String[] EMOTION_OPPOSITE_MAP_FROM_R = {"PA", "PE", "PD", "PH", "PG", "PB", "PK", "NA", "NB", "NJ", "NH", "PF", "NI", "NC", "NG", "NE", "ND", "NN", "NK", "NL", "PC"};
    private static final String[] EMOTION_OPPOSITE_MAP_TO_R = {"NB", "NI", "ND", "NN", "NL", "ND", "NN", "PE", "PA", "PA", "PE", "PE", "PE", "PE", "PA", "PA", "PB", "PH", "PB", "PG", "PE"};
    private static final int[] EMOTION_OPPOSITE_MAP_FROM;
    private static final int[] EMOTION_OPPOSITE_MAP_TO;
    private static final double[] EMOTION_OPPOSITE_MAP_FACTOR = {1, 0.8, 0.5, 1, 1, 0.9, 0.7, 0.7, 0.8, 0.7, 0.8, 0.7, 0.8, 0.7, 0.5, 0.7, 0.9, 1, 0.7, 0.8, 0.8};

    static {
        EMOTION_OPPOSITE_MAP_FROM = new int[EMOTION_OPPOSITE_MAP_FROM_R.length];
        EMOTION_OPPOSITE_MAP_TO = new int[EMOTION_OPPOSITE_MAP_TO_R.length];

        for (int i = 0; i < EMOTION_OPPOSITE_MAP_FROM_R.length; i++) {
            EMOTION_OPPOSITE_MAP_FROM[i] = Emotion.parseEmotion(EMOTION_OPPOSITE_MAP_FROM_R[i]);
        }
        for (int i = 0; i < EMOTION_OPPOSITE_MAP_TO_R.length; i++) {
            EMOTION_OPPOSITE_MAP_TO[i] = Emotion.parseEmotion(EMOTION_OPPOSITE_MAP_TO_R[i]);
        }
    }

    private Emotion getAdjustedEmotion(Emotion emotion, double factor) {
        Emotion ne = new Emotion();
        if (factor >= 0) { // 近义
            ne.setEmotion(emotion.getEmotion());
            ne.setPolarity(emotion.getPolarity());
            int strength = (int) (emotion.getStrength() * factor);
            if (strength > 9) strength = 9;
            ne.setStrength(strength);
            double confidence = normalDist(factor) * emotion.getConfidence();
            ne.setConfidence(confidence);
        } else { // 反义
            factor = 00D - factor;
            int er = emotion.getEmotion();
            double eFactor = 1D;
            for (int i = 0; i < EMOTION_OPPOSITE_MAP_FROM.length; i++) {
                if (er == EMOTION_OPPOSITE_MAP_FROM[i]) {
                    er = EMOTION_OPPOSITE_MAP_TO[i];
                    eFactor = EMOTION_OPPOSITE_MAP_FACTOR[i];
                    break;
                }
            }
            factor *= eFactor;
            ne.setEmotion(er);
            int strength = (int) (emotion.getStrength() * factor);
            if (strength > 9) strength = 9;
            ne.setStrength(strength);
            double confidence = normalDist(factor) * emotion.getConfidence();
            ne.setConfidence(confidence);
            int pol = Emotion.EMOTION_POL_NEUTRAL;
            switch (emotion.getPolarity()) {
                case Emotion.EMOTION_POL_NEUTRAL:
                    pol = Emotion.EMOTION_POL_AMPHOTERIC;
                    break;
                case Emotion.EMOTION_POL_COMMENDATORY:
                    pol = Emotion.EMOTION_POL_DEROGATORY;
                    break;
                case Emotion.EMOTION_POL_DEROGATORY:
                    pol = Emotion.EMOTION_POL_COMMENDATORY;
                    break;
                case Emotion.EMOTION_POL_AMPHOTERIC:
                    pol = Emotion.EMOTION_POL_NEUTRAL;
                    break;
            }
            ne.setPolarity(pol);
        }

        return ne;
    }

    /**
     * 正态分布计算可信度
     *
     * @param x 情感因子
     * @return 可信度因子
     */
    private static double normalDist(double x) {
        return Math.exp(-(x - 1) * (x - 1) * Math.PI) * 0.4 + 0.5;
    }

    /**
     * 将一个情感词的每个字追加到单字情感值表中
     *
     * @param w 情感词
     */
    private void putCharacterMap(Word w) {
        char[] ca = w.word.toCharArray();
        for (char c : ca) {
            EmotionCalc ec = mEmotionMap_character.get(c);
            if (ec == null) {
                ec = new EmotionCalc();
                mEmotionMap_character.put(c, ec);
            }
            for (Emotion em : w.emotions) {
                switch (em.getPolarity()) {
                    case Emotion.EMOTION_POL_NEUTRAL:
                        continue;
                    case Emotion.EMOTION_POL_COMMENDATORY:
                        ec.positive++;
                        mCharacterCount_positive++;
                        break;
                    case Emotion.EMOTION_POL_DEROGATORY:
                        ec.negative++;
                        mCharacterCount_negative++;
                        break;
                    case Emotion.EMOTION_POL_AMPHOTERIC:
                        ec.positive++;
                        ec.negative++;
                        mCharacterCount_positive++;
                        mCharacterCount_negative++;
                        break;
                }
            }
        }
    }

    private static final String[] COMP_OPPOSITE = {"不"};
    private static final double[] COMP_OPPOSITE_FACTOR = {-1};
    private static final String[] COMP_FIX = {"有点儿", "非常", "十分", "极其", "格外", "分外", "更加", "越发", "有点", "稍微", "几乎", "略微", "过于", "尤其", "更", "很", "最", "极", "太", "越", "稍", "超",};
    private static final double[] COMP_FIX_FACTOR = {0.7, 1.5, 1.3, 1.5, 1.5, 1.5, 1.1, 1.1, 0.7, 0.5, 0.9, 0.5, 1.3, 1.3, 1.1, 1.3, 1.5, 1.5, 1.3, 1.1, 0.5, 1.5,};

    /**
     * 拆分复合词
     *
     * @param word 待拆分词
     * @return 若输入的词是一个复合词，则返回拆分后的复合词元，否则返回 null
     */
    private CompositeWord analysisCompositeWord(String word) {

        String wStr = word;

        if (wStr.length() <= 1) return null;

        StringBuilder sb = new StringBuilder();

        boolean prefixFound = true;
        boolean oppositeFound = false;
        //boolean fixFound = false;
        double factor = 1;

        while (prefixFound && wStr.length() > 0) {
            prefixFound = false;

            // 否定词
            for (int i = 0; i < COMP_OPPOSITE.length; i++) {
                if (wStr.startsWith(COMP_OPPOSITE[i])) {
                    prefixFound = true;
                    oppositeFound = true;
                    sb.append(COMP_OPPOSITE[i]);
                    wStr = wStr.substring(COMP_OPPOSITE[i].length());

                    factor *= COMP_OPPOSITE_FACTOR[i];
                    break;
                }
            }

            // 程度副词
            for (int i = 0; i < COMP_FIX.length; i++) {
                if (wStr.startsWith(COMP_FIX[i])) {
                    prefixFound = true;
                    //fixFound = true;
                    sb.append(COMP_FIX[i]);
                    wStr = wStr.substring(COMP_FIX[i].length());

                    if (oppositeFound) {
                        factor *= -(2 - COMP_FIX_FACTOR[i]);
                    } else {
                        factor *= COMP_FIX_FACTOR[i];
                    }
                    break;
                }
            }
        }

        String prefix = sb.toString();

        if (prefix.isEmpty()) return null;

        if (factor > 1.5) factor = 1.5;
        else if (factor < -1.5) factor = -1.5;

        CompositeWord cw = new CompositeWord();
        cw.composite_factor = factor;
        cw.lastWord = wStr;
        cw.prefix = prefix;

        return cw;
    }

    private static String fix(String in) {
        in = in.replace("\"", "\"\"");
        if (in.contains(",")) {
            in = "\"" + in + "\"";
        }

        return in;
    }

    private static String generateLine(Word word) {
        StringBuilder sb = new StringBuilder();
        sb.append(fix(word.word));
        sb.append(',');
        sb.append(fix(Word.parseWordPart(word.wordPart)));
        sb.append(',');
        sb.append(fix(String.valueOf(word.meaningCount)));
        sb.append(',');
        sb.append(fix(String.valueOf(word.meaning)));
        for (Emotion e : word.emotions) {
            sb.append(',');
            sb.append(fix(Emotion.parseEmotion(e.getEmotion())));
            sb.append(',');
            sb.append(fix(String.valueOf(e.getStrength())));
            sb.append(',');
            sb.append(fix(String.valueOf(e.getPolarity())));
            sb.append(',');
            sb.append(fix(String.valueOf(e.getConfidence())));
        }
        sb.append(',');

        return sb.toString();
    }

    private static String[] split(String line) {
        char[] cs = line.toCharArray();
        StringBuilder sb = new StringBuilder();
        List<String> ss = new LinkedList<String>();

        for (int i = 0; i < cs.length; i++) {
            if (cs[i] == '\"') {
                for (int j = i + 1; j < cs.length; j++) {
                    if (cs[j] == '\"') {
                        if (j + 1 < cs.length) {
                            if (cs[j + 1] == '\"') {
                                j++;
                                sb.append('\"');
                            } else {
                                i = j;
                                break;
                            }
                        } else {
                            i = j;
                            break;
                        }
                    } else {
                        sb.append(cs[j]);
                    }
                }
            } else if (cs[i] == ',') {
                ss.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(cs[i]);
            }
        }
        ss.add(sb.toString().trim());

        String[] sa = new String[ss.size()];
        ss.toArray(sa);

        return sa;
    }

    private static double getNumber(String[] elements, int index, double defaultValue) {
        if (index > elements.length - 1
                || isEmptyString(elements[index])) return defaultValue;

        return Double.parseDouble(elements[index]);
    }

    public static Word parseWord(String line) {

        String[] elements = split(line);
        if (elements.length < 7 || isEmptyString(elements[0])) {
            throw new IllegalArgumentException();
        }

        Word word = new Word();
        word.word = elements[0];
        word.wordPart = Word.parseWordPart(elements[1]);
        word.meaningCount = (int) getNumber(elements, 2, 0);
        word.meaning = (int) getNumber(elements, 3, 0);
        for (int i = 4; i < elements.length && !isEmptyString(elements[i]); i += 4) {
            Emotion emotion = new Emotion();
            emotion.setEmotion(elements[i]);
            emotion.setStrength((int) getNumber(elements, i + 1, 1));
            emotion.setPolarity((int) getNumber(elements, i + 2, 0));
            emotion.setConfidence(getNumber(elements, i + 3, 1));
            word.emotions.add(emotion);
        }

        return word;
    }

    public Word searchWord(String word) {
        Word w = mEmotionMap_static.get(word);
        if (w != null) return w;

        w = mEmotionMap_assuming.get(word);

        return w;
    }

    @Override
    public Word getWord(String word) {
        Word w = searchWord(word);
        if (w != null) return w;

        CompositeWord cw = analysisCompositeWord(word);

        w = new Word();
        w.word = word;

        double factor = 1;
        if (cw != null) { // 复合情感词
            factor = cw.composite_factor;
            word = cw.lastWord;
        }

        char[] cs = word.toCharArray();

        double po = 0, ne = 0;
        for (char c : cs) {
            EmotionCalc ec = mEmotionMap_character.get(c);
            if (ec != null) {
                double fm = ec.fn + ec.fp;
                if (fm != 0) {
                    po += ec.fp / (ec.fp + ec.fn);
                    ne += ec.fn / (ec.fp + ec.fn);
                }
            }
        }

        double y1 = mCategorizer.categorize(po, ne);
        double y = y1 * factor;
        Emotion e = new Emotion();

        double confidence;
        if (po >= ne) {
            if (y1 >= 0) {
                confidence = 0.7;
            } else {
                confidence = 0.4;
            }
            if (po > 0) {
                confidence *= 1D - (ne / po);
            }
        } else {
            if (y1 < 0) {
                confidence = 0.7;
            } else {
                confidence = 0.4;
            }
            if (ne > 0) {
                confidence *= 1D - (po / ne);
            }
        }
        e.setConfidence(confidence);
        e.setPolarity(y >= 0 ? Emotion.EMOTION_POL_COMMENDATORY : Emotion.EMOTION_POL_DEROGATORY);
        int strength = (int) (confidence * 9D);
        if (strength > 9) strength = 9;
        e.setStrength(strength);

        w.emotions.add(e);

        mEmotionMap_assuming.put(w.word, w);
        return w;
    }

    HashMap<String, Word> mEmotionMap_emotion = new HashMap<String, Word>();// 表情符表

    public void loadEmotionDict(String emotionFile) throws IOException {
        mProp_emotionDict_path = emotionFile;
        if (emotionFile == null) return;

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(emotionFile), mProp_dictEncode));
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) continue;

                try {
                    Word w = parseWord(line);
                    Collections.sort(w.emotions, emotionComparator);

                    mEmotionMap_emotion.put(w.word, w);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Unexpected word definition: \"" + line + "\", ignored.");
                }
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public Word getEmotionWord(String emotion) {
        return mEmotionMap_emotion.get(emotion);
    }

    @Override
    public List<String> getWordsString() {
        List<String> ws = new ArrayList<String>(mAllWords_static.size() + 1);

        for (Word w : mAllWords_static) {
            ws.add(w.word);
        }

        return ws;
    }

    private static boolean isEmptyString(String s) {
        return s == null || s.isEmpty();
    }
}
