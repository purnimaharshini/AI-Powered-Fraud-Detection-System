package com.frauddetection.online.service;

import com.frauddetection.online.dto.DashboardDto;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ModelPredictionService {

    private static final Map<String, Integer> TRANSACTION_TYPE_MAP = Map.of(
            "debit", 0,
            "credit", 1
    );

    private static final Map<String, Integer> CHANNEL_MAP = Map.of(
            "atm", 0,
            "online", 1,
            "branch", 2
    );

    private static final Map<String, Integer> OCCUPATION_MAP = Map.of(
            "doctor", 0,
            "engineer", 1,
            "student", 2,
            "salaried", 3
    );

    private final JavaRandomForestModel randomForestModel;

    public ModelPredictionService(JavaRandomForestModel randomForestModel) {
        this.randomForestModel = randomForestModel;
    }

    public DashboardDto.PredictionFormResponse predictionForm() {
        return new DashboardDto.PredictionFormResponse(
                List.of("Debit", "Credit"),
                List.of("ATM", "Online", "Branch"),
                List.of("Doctor", "Engineer", "Student", "Salaried")
        );
    }

    public DashboardDto.PredictionResponse predict(DashboardDto.PredictionRequest request) {
        double[] features = new double[]{
                request.transactionAmount(),
                encode(request.transactionType(), TRANSACTION_TYPE_MAP, "transaction type"),
                encode(request.channel(), CHANNEL_MAP, "channel"),
                request.customerAge(),
                encode(request.customerOccupation(), OCCUPATION_MAP, "occupation"),
                request.transactionDuration(),
                request.loginAttempts(),
                request.accountBalance()
        };

        JavaRandomForestModel.ForestPrediction prediction = randomForestModel.predict(features);
        double probability = round(prediction.fraudProbability());
        double riskScore = round(probability * 100.0);
        String verdict = riskScore >= 70 ? "High Risk"
                : riskScore >= 35 ? "Moderate Risk"
                : "Low Risk";

        return new DashboardDto.PredictionResponse(
                probability >= 0.5,
                probability,
                riskScore,
                verdict,
                buildFactors(request, probability),
                null,
                null,
                null
        );
    }

    private List<String> buildFactors(DashboardDto.PredictionRequest request, double probability) {
        List<String> factors = new ArrayList<>();

        if (request.transactionAmount() >= 10_000) {
            factors.add("High transaction amount materially increases exposure.");
        }
        if ("debit".equals(normalize(request.transactionType()))) {
            factors.add("Debit transactions carry the highest observed fraud rate in the historical dataset.");
        }
        if ("online".equals(normalize(request.channel()))) {
            factors.add("Online channel activity is historically the riskiest of the supported channels.");
        }
        if (request.loginAttempts() >= 3) {
            factors.add("Multiple login attempts indicate potential access friction or credential abuse.");
        }
        if (request.transactionDuration() <= 30) {
            factors.add("Very short transaction duration can be consistent with automated behavior.");
        }
        if (request.accountBalance() < request.transactionAmount()) {
            factors.add("Requested amount exceeds the available account balance.");
        }
        if (request.minutesSinceLastTransaction() != null && request.minutesSinceLastTransaction() < 5) {
            factors.add("Back-to-back transactions in a short interval raise the velocity risk profile.");
        }
        if (factors.isEmpty()) {
            factors.add(probability >= 0.5
                    ? "The model detected a fraud-like combination of encoded transaction signals."
                    : "The current input profile looks consistent with lower-risk historical patterns.");
        }

        return factors;
    }

    private int encode(String rawValue, Map<String, Integer> options, String fieldName) {
        Integer encoded = options.get(normalize(rawValue));
        if (encoded == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported " + fieldName + ": " + rawValue);
        }
        return encoded;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
