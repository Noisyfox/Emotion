package org.foxteam.noisyfox.Emotion.EmotionDict;

import libsvm.*;

import java.io.IOException;
import java.util.Vector;

/**
 * Created by Noisyfox on 14-3-3.
 * SVM 分类器
 */
public class Categorizer {

    private svm_parameter param = new svm_parameter();
    private svm_problem prob = null;
    private svm_model model = null;
    private Vector<Double> vy = new Vector<Double>();
    private Vector<svm_node[]> vx = new Vector<svm_node[]>();

    public Categorizer() {
        // default values
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.RBF;
        param.degree = 3;
        param.gamma = 0.5;    // 1/num_features
        param.coef0 = 0;
        param.nu = 0.5;
        param.cache_size = 100;
        param.C = 1;
        param.eps = 1e-3;
        param.p = 0.1;
        param.shrinking = 0;
        param.probability = 0;
        param.nr_weight = 0;
        param.weight_label = new int[0];
        param.weight = new double[0];
    }

    public void training(double v1, double v2, double target) {
        vy.addElement(target);

        svm_node[] x = new svm_node[2];
        x[0] = new svm_node();
        x[0].index = 1;
        x[0].value = v1;
        x[1] = new svm_node();
        x[1].index = 2;
        x[1].value = v2;
        vx.addElement(x);
    }

    public void commitTraining() {
        prob = new svm_problem();
        prob.l = vy.size();
        prob.x = new svm_node[prob.l][];
        for (int i = 0; i < prob.l; i++)
            prob.x[i] = vx.elementAt(i);
        prob.y = new double[prob.l];
        for (int i = 0; i < prob.l; i++)
            prob.y[i] = vy.elementAt(i);

        long time = System.currentTimeMillis();
        model = svm.svm_train(prob, param);
        String error_msg = svm.svm_check_parameter(prob, param);

        if (error_msg != null) {
            System.err.print("ERROR: " + error_msg + "\n");
            model = null;
            return;
        }

        time = System.currentTimeMillis() - time;

        System.out.println("SVM training finished! Using " + time + " milliseconds.");
    }

    public void reset() {
        vy.clear();
        vx.clear();

        prob = null;
        model = null;
    }

    public double categorize(double v1, double v2) {
        if (model == null) throw new IllegalStateException();

        svm_node[] x = new svm_node[2];
        x[0] = new svm_node();
        x[0].index = 1;
        x[0].value = v1;
        x[1] = new svm_node();
        x[1].index = 2;
        x[1].value = v2;

        return svm.svm_predict(model, x);
    }

    public void dumpModel(String path) throws IOException {
        if (model == null) throw new IllegalStateException();

        svm.svm_save_model(path, model);
    }

    public void importModel(String path) throws IOException {
        reset();

        model = svm.svm_load_model(path);

        for (int i = 0; i < model.l; i++) {
            svm_node[] x = model.SV[i];
            training(x[0].value, x[1].value, model.sv_coef[0][i]);
        }
    }

    /*
    private static final double n = 0.01;
    private double[] w = {0, 0, 0};

    public void training(double v1, double v2, double target) {
        if(v1 != v1 || v2 != v2 || target != target
                || w[0] != w[0] || w[1] != w[1] || w[2] != w[2] ){
            w[0] += 0;
        }


        double y = categorize(v1, v2);
        double d = target - y;
        double k = n * d;

        w[0] += k;
        w[1] += k * v1;
        w[2] += k * v2;

        //System.out.printf("%f %f %f %f %f %f\n", v1, v2, target, w[0], w[1], w[2]);
    }

    public double categorize(double v1, double v2) {
        return w[0] + v1 * w[1] + v2 * w[2];
    }
    */
}
