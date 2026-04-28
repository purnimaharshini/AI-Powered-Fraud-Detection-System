package com.frauddetection.online.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.IntStream;

@Component
public class JavaRandomForestModel {

    private final ForestModel forestModel;

    public JavaRandomForestModel(ObjectMapper objectMapper, ResourceLoader resourceLoader) throws IOException {
        try (InputStream inputStream = resourceLoader.getResource("classpath:model/fraud_model.json").getInputStream()) {
            this.forestModel = objectMapper.readValue(inputStream, ForestModel.class);
        }
    }

    public ForestPrediction predict(double[] features) {
        double[] probabilities = new double[forestModel.classes().length];

        for (TreeModel tree : forestModel.trees()) {
            int node = 0;
            while (tree.childrenLeft()[node] != -1 && tree.childrenRight()[node] != -1) {
                int featureIndex = tree.featureIndices()[node];
                node = features[featureIndex] <= tree.thresholds()[node]
                        ? tree.childrenLeft()[node]
                        : tree.childrenRight()[node];
            }

            double[] leafProbabilities = tree.classProbabilities()[node];
            for (int i = 0; i < leafProbabilities.length; i++) {
                probabilities[i] += leafProbabilities[i];
            }
        }

        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= forestModel.trees().length;
        }

        int winningIndex = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[winningIndex]) {
                winningIndex = i;
            }
        }

        int fraudClassIndex = IntStream.range(0, forestModel.classes().length)
                .filter(index -> forestModel.classes()[index] == 1)
                .findFirst()
                .orElse(probabilities.length - 1);

        return new ForestPrediction(
                forestModel.classes()[winningIndex],
                probabilities[fraudClassIndex],
                Arrays.copyOf(probabilities, probabilities.length)
        );
    }

    public record ForestPrediction(int predictedClass, double fraudProbability, double[] probabilities) {
    }

    public record ForestModel(String[] featureNames, int[] classes, TreeModel[] trees) {
    }

    public record TreeModel(
            int[] childrenLeft,
            int[] childrenRight,
            int[] featureIndices,
            double[] thresholds,
            double[][] classProbabilities
    ) {
    }
}
