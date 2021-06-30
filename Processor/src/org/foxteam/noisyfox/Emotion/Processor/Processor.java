package org.foxteam.noisyfox.Emotion.Processor;

import com.mongodb.BasicDBObject;
import org.foxteam.noisyfox.Emotion.Core.IAnalyzer;
import wbgrab.IStatus;
import wbgrab.IStatusProvider;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Noisyfox on 14-3-7.
 * 微博分析任务处理器
 */
public class Processor {

    private final IAnalyzer mAnalyzer;
    private final IStatusProvider mStatusProvider;
    private final Object mSyncObj = new Object();

    private int mMaxJob = 0;
    private HashMap<String, Job> mJobsStarted = new HashMap<String, Job>();
    private List<Job> mJobsWaiting = new LinkedList<Job>();

    public Processor(IAnalyzer analyzer, IStatusProvider statusProvider) {
        if (analyzer == null || statusProvider == null) throw new IllegalArgumentException();

        mAnalyzer = analyzer;
        mStatusProvider = statusProvider;
    }

    public void startJob(String topicTag, boolean unAnalyzedOnly) {
        Iterable<IStatus> status = mStatusProvider.getStatus(mStatusProvider.getTopicTag(topicTag), unAnalyzedOnly);

        for (IStatus s : status) {
            double r = mAnalyzer.analyze(s.getText());
            BasicDBObject analyzeResult = new BasicDBObject();
            analyzeResult.append("analyzerResult_polarity", r);
            s.setAnalyzeResult(analyzeResult);
            s.save();
        }
    }

    public void startJobAsychronous(String topicTag, boolean unAnalyzedOnly) {
        synchronized (mSyncObj) {
            Job j = new Job(topicTag, unAnalyzedOnly);
            mJobsWaiting.add(j);
            doJobs();
        }
    }

    /**
     * 清空等待中的任务
     */
    public void clearJobs(){
        synchronized (mSyncObj) {
            mJobsWaiting.clear();
        }
    }

    private void doJobs() {
        synchronized (mSyncObj) {
            while ((mMaxJob <= 0 || mJobsStarted.size() < mMaxJob) && !mJobsWaiting.isEmpty()) {
                Iterator<Job> jobI = mJobsWaiting.iterator();
                while (true) {
                    if (jobI.hasNext()) {
                        Job jobN = jobI.next();
                        if (!mJobsStarted.containsKey(jobN.mTopicTag)) {
                            jobI.remove();
                            jobN.start();
                            mJobsStarted.put(jobN.mTopicTag, jobN);
                            break;
                        }
                    } else {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 设置最多同时进行的任务数
     *
     * @param max 最多同时进行的任务数，小于等于0则为不限制
     *            改变该值对当前已启动任务无影响
     */
    public void setMaxJobs(int max) {
        synchronized (mSyncObj) {
            mMaxJob = max;
            doJobs();
        }
    }

    public void waitFor() throws InterruptedException {
        synchronized (mSyncObj) {
            while (!mJobsStarted.isEmpty()) {
                mSyncObj.wait();
            }
        }
    }

    private void onJobDone(String topicTag) {
        synchronized (mSyncObj) {
            mJobsStarted.remove(topicTag);
            doJobs();
            mSyncObj.notifyAll();
        }
    }

    private class Job extends Thread {
        private final String mTopicTag;
        private final boolean mUnAnalyzedOnly;

        public Job(String topicTag, boolean unAnalyzedOnly) {
            mTopicTag = topicTag;
            mUnAnalyzedOnly = unAnalyzedOnly;
        }

        @Override
        public void run() {
            startJob(mTopicTag, mUnAnalyzedOnly);
            onJobDone(mTopicTag);
        }
    }

}
