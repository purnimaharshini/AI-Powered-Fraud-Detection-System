package com.frauddetection.online.controller;

import com.frauddetection.online.dto.DashboardDto;
import com.frauddetection.online.service.AuthService;
import com.frauddetection.online.service.FraudAlertEmailService;
import com.frauddetection.online.service.ModelPredictionService;
import com.frauddetection.online.service.TransactionAnalyticsService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TransactionAnalyticsService analyticsService;
    private final ModelPredictionService modelPredictionService;
    private final AuthService authService;
    private final FraudAlertEmailService fraudAlertEmailService;

    public DashboardController(
            TransactionAnalyticsService analyticsService,
            ModelPredictionService modelPredictionService,
            AuthService authService,
            FraudAlertEmailService fraudAlertEmailService
    ) {
        this.analyticsService = analyticsService;
        this.modelPredictionService = modelPredictionService;
        this.authService = authService;
        this.fraudAlertEmailService = fraudAlertEmailService;
    }

    @GetMapping("/options")
    public DashboardDto.OptionsResponse options() {
        return analyticsService.loadOptions();
    }

    @GetMapping("/overview")
    public DashboardDto.OverviewResponse overview(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String device
    ) {
        return analyticsService.buildOverview(customerId, startDate, endDate, transactionType, location, device);
    }

    @GetMapping("/transactions")
    public DashboardDto.TransactionListResponse transactions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String device,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return analyticsService.loadTransactionViews(customerId, startDate, endDate, transactionType, location, device, limit);
    }

    @GetMapping("/prediction-form")
    public DashboardDto.PredictionFormResponse predictionForm() {
        return modelPredictionService.predictionForm();
    }

    @PostMapping("/predict")
    public DashboardDto.PredictionResponse predict(
            @Valid @RequestBody DashboardDto.PredictionRequest request,
            HttpSession session
    ) {
        var authenticatedUser = authService.requireAuthenticatedUser(session);
        DashboardDto.PredictionResponse prediction = modelPredictionService.predict(request);
        if (!prediction.fraud()) {
            return prediction;
        }

        FraudAlertEmailService.NotificationOutcome notificationOutcome = fraudAlertEmailService.sendFraudAlert(
                authenticatedUser,
                request,
                prediction
        );

        return new DashboardDto.PredictionResponse(
                prediction.fraud(),
                prediction.probability(),
                prediction.riskScore(),
                prediction.verdict(),
                prediction.factors(),
                true,
                notificationOutcome.sent(),
                notificationOutcome.message()
        );
    }
}
