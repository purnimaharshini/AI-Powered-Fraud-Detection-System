package com.frauddetection.online;

import com.frauddetection.online.dto.DashboardDto;
import com.frauddetection.online.service.ModelPredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ModelPredictionServiceTest {

    @Autowired
    private ModelPredictionService modelPredictionService;

    @Test
    void predictsWithinExpectedRange() {
        DashboardDto.PredictionResponse response = modelPredictionService.predict(
                new DashboardDto.PredictionRequest(
                        12_500,
                        "Debit",
                        "Online",
                        34,
                        "Engineer",
                        25,
                        3,
                        8_000,
                        2.0
                )
        );

        assertThat(response.probability()).isBetween(0.0, 1.0);
        assertThat(response.riskScore()).isBetween(0.0, 100.0);
        assertThat(response.factors()).isNotEmpty();
    }
}
