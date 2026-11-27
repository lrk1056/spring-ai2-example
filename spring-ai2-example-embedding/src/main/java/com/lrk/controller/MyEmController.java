package com.lrk.controller;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class MyEmController {

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @GetMapping("/emDemo")
    public String emDemo() {
        float[] textVector1 = openAiEmbeddingModel.embed("墨雨听风三万里，咫尺天涯一盏茶");
        float[] textVector2 = openAiEmbeddingModel.embed("八百里逐日听风，看尽苍茫海角");
        float[] textVector3 = openAiEmbeddingModel.embed("我喜欢吃苹果");
        System.out.println(textVector1.length + ":" + Arrays.toString(textVector1));
        System.out.println(textVector2.length + ":" + Arrays.toString(textVector2));
        System.out.println(textVector3.length + ":" + Arrays.toString(textVector3));
        double dist12 = euclideanDistance(textVector1, textVector2);
        double dist13 = euclideanDistance(textVector1, textVector3);
        System.out.println("textVector1 与 textVector2 欧氏距离: " + dist12);
        System.out.println("textVector1 与 textVector3 欧氏距离: " + dist13);
        return "ok";
    }

    /**
     * 计算两个向量的欧氏距离
     *
     * @param a 向量1
     * @param b 向量2
     * @return 欧氏距离
     */
    private static double euclideanDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        double sumSq = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sumSq += d * d;
        }
        return Math.sqrt(sumSq);
    }
}
