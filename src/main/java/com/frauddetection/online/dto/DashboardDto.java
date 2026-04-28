package com.frauddetection.online.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public final class DashboardDto {

    private DashboardDto() {
    }

    public record OptionsResponse(
            List<String> customerIds,
            List<String> transactionTypes,
            List<String> locations,
            List<String> devices,
            String minDate,
            String maxDate
    ) {
    }

    public record MetricSummary(
            long totalTransactions,
            double totalDebit,
            double totalCredit,
            double averageAmount,
            double maxAmount,
            long suspiciousCount,
            double suspiciousPercentage
    ) {
    }

    public record Verdict(
            String label,
            String tone,
            int score,
            String summary
    ) {
    }

    public record CountPoint(
            String label,
            long count
    ) {
    }

    public record TimeSeriesPoint(
            String timestamp,
            double amount,
            String transactionType,
            boolean suspicious
    ) {
    }

    public record HeatmapPoint(
            String weekday,
            int hour,
            long count
    ) {
    }

    public record OverviewResponse(
            MetricSummary metrics,
            String primaryDevice,
            String primaryLocation,
            boolean highVariance,
            Verdict verdict,
            double riskScore,
            List<String> indicators,
            List<TimeSeriesPoint> timeSeries,
            List<CountPoint> amountDistribution,
            List<CountPoint> locationBreakdown,
            List<CountPoint> merchantCategoryBreakdown,
            List<HeatmapPoint> heatmap,
            long suspiciousTransactions,
            String takeoverAlert
    ) {
    }

    public record TransactionView(
            String transactionId,
            String customerId,
            String timestamp,
            double amount,
            String transactionType,
            String location,
            String device,
            String merchantCategory,
            boolean suspicious
    ) {
    }

    public record TransactionListResponse(
            long totalMatched,
            List<TransactionView> transactions
    ) {
    }

    public record PredictionFormResponse(
            List<String> transactionTypes,
            List<String> channels,
            List<String> occupations
    ) {
    }

    public record PredictionRequest(
            @JsonAlias("amount") @NotNull @Positive double transactionAmount,
            @NotBlank String transactionType,
            @NotBlank String channel,
            @NotNull @Min(18) @Max(100) int customerAge,
            @NotBlank String customerOccupation,
            @NotNull @PositiveOrZero double transactionDuration,
            @NotNull @Min(1) @Max(10) int loginAttempts,
            @NotNull @PositiveOrZero double accountBalance,
            @PositiveOrZero Double minutesSinceLastTransaction
    ) {
    }

    public record PredictionResponse(
            boolean fraud,
            double probability,
            double riskScore,
            String verdict,
            List<String> factors,
            Boolean notificationAttempted,
            Boolean notificationSent,
            String notificationMessage
    ) {
    }
}
