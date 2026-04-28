package com.frauddetection.online.service;

import com.frauddetection.online.domain.TransactionRecord;
import com.frauddetection.online.dto.DashboardDto;
import com.frauddetection.online.repository.TransactionRecordRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionAnalyticsService {

    private final TransactionRecordRepository repository;

    public TransactionAnalyticsService(TransactionRecordRepository repository) {
        this.repository = repository;
    }

    public DashboardDto.OptionsResponse loadOptions() {
        TransactionRecord minRecord = repository.findFirstByOrderByTimestampAsc();
        TransactionRecord maxRecord = repository.findFirstByOrderByTimestampDesc();

        return new DashboardDto.OptionsResponse(
                repository.findDistinctCustomerIds(),
                repository.findDistinctTransactionTypes(),
                repository.findDistinctLocations(),
                repository.findDistinctDevices(),
                minRecord == null ? null : minRecord.getTimestamp().toLocalDate().toString(),
                maxRecord == null ? null : maxRecord.getTimestamp().toLocalDate().toString()
        );
    }

    public DashboardDto.OverviewResponse buildOverview(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String location,
            String device
    ) {
        List<TransactionRecord> transactions = loadTransactions(customerId, startDate, endDate, transactionType, location, device);
        DashboardDto.MetricSummary metrics = metrics(transactions);
        RiskSnapshot riskSnapshot = riskSnapshot(transactions, metrics);
        String takeoverAlert = detectAccountTakeover(transactions);

        return new DashboardDto.OverviewResponse(
                metrics,
                modeValue(transactions, TransactionRecord::getDevice).orElse("N/A"),
                modeValue(transactions, TransactionRecord::getLocation).orElse("N/A"),
                metrics.averageAmount() > 0 && standardDeviation(transactions) > metrics.averageAmount(),
                riskSnapshot.verdict(),
                riskSnapshot.riskScore(),
                riskSnapshot.indicators(),
                buildTimeSeries(transactions),
                buildHistogram(transactions),
                buildBreakdown(transactions, TransactionRecord::getLocation),
                buildBreakdown(transactions, TransactionRecord::getMerchantCategory),
                buildHeatmap(transactions),
                transactions.stream().filter(TransactionRecord::isSuspicious).count(),
                takeoverAlert   // ✅ NEW FIELD
        ); 
    }

    private String detectAccountTakeover(List<TransactionRecord> transactions) {

        if (transactions == null || transactions.size() < 2) return null;

        // Sort properly (IMPORTANT)
        List<TransactionRecord> sorted = transactions.stream()
                .sorted(Comparator.comparing(TransactionRecord::getTimestamp))
                .toList();

        TransactionRecord latest = sorted.get(sorted.size() - 1);

        // Previous behavior (exclude latest)
        List<TransactionRecord> previous = sorted.subList(0, sorted.size() - 1);

        long sameDeviceCount = previous.stream()
                .filter(t -> t.getDevice().equals(latest.getDevice()))
                .count();

        long sameLocationCount = previous.stream()
                .filter(t -> t.getLocation().equals(latest.getLocation()))
                .count();

        // 🔥 KEY LOGIC: Rare usage detection
        boolean rareDevice = sameDeviceCount < 2;
        boolean rareLocation = sameLocationCount < 2;

        if (rareDevice && rareLocation) {
            return "⚠ Account Takeover Detected";
        }

        return null;
    }
    public DashboardDto.TransactionListResponse loadTransactionViews(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String location,
            String device,
            int limit
    ) {
        List<TransactionRecord> matches = loadTransactions(customerId, startDate, endDate, transactionType, location, device);
        List<DashboardDto.TransactionView> items = matches.stream()
                .sorted(Comparator.comparing(TransactionRecord::isSuspicious).reversed()
                        .thenComparing(TransactionRecord::getTimestamp, Comparator.reverseOrder()))
                .limit(limit)
                .map(this::toTransactionView)
                .toList();

        return new DashboardDto.TransactionListResponse(matches.size(), items);
    }

    private List<TransactionRecord> loadTransactions(
            String customerId,
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String location,
            String device
    ) {
        TransactionRecord minRecord = repository.findFirstByOrderByTimestampAsc();
        TransactionRecord maxRecord = repository.findFirstByOrderByTimestampDesc();

        if (minRecord == null || maxRecord == null) {
            return List.of();
        }

        String resolvedCustomerId = customerId == null || customerId.isBlank()
                ? repository.findDistinctCustomerIds().stream().findFirst().orElseThrow()
                : customerId;
        LocalDate resolvedStart = startDate == null ? minRecord.getTimestamp().toLocalDate() : startDate;
        LocalDate resolvedEnd = endDate == null ? maxRecord.getTimestamp().toLocalDate() : endDate;

        List<TransactionRecord> base = repository.findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
                resolvedCustomerId,
                resolvedStart.atStartOfDay(),
                resolvedEnd.plusDays(1).atStartOfDay()
        );

        return base.stream()
                .filter(transaction -> matchesFilter(transaction.getTransactionType(), transactionType))
                .filter(transaction -> matchesFilter(transaction.getLocation(), location))
                .filter(transaction -> matchesFilter(transaction.getDevice(), device))
                .toList();
    }

    private boolean matchesFilter(String value, String filter) {
        return filter == null || filter.isBlank() || "All".equalsIgnoreCase(filter) || Objects.equals(value, filter);
    }

    private DashboardDto.MetricSummary metrics(List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) {
            return new DashboardDto.MetricSummary(0, 0, 0, 0, 0, 0, 0);
        }

        DoubleSummaryStatistics amountStats = transactions.stream()
                .mapToDouble(TransactionRecord::getAmount)
                .summaryStatistics();

        double totalDebit = transactions.stream()
                .filter(transaction -> "debit".equalsIgnoreCase(transaction.getTransactionType()))
                .mapToDouble(TransactionRecord::getAmount)
                .sum();
        double totalCredit = transactions.stream()
                .filter(transaction -> "credit".equalsIgnoreCase(transaction.getTransactionType()))
                .mapToDouble(TransactionRecord::getAmount)
                .sum();
        long suspiciousCount = transactions.stream().filter(TransactionRecord::isSuspicious).count();

        return new DashboardDto.MetricSummary(
                transactions.size(),
                round(totalDebit),
                round(totalCredit),
                round(amountStats.getAverage()),
                round(amountStats.getMax()),
                suspiciousCount,
                round((suspiciousCount * 100.0) / transactions.size())
        );
    }

    private RiskSnapshot riskSnapshot(List<TransactionRecord> transactions, DashboardDto.MetricSummary metrics) {
        List<String> indicators = new ArrayList<>();
        int score = 0;

        if (transactions.isEmpty()) {
            return new RiskSnapshot(
                    new DashboardDto.Verdict(
                            "No activity in the selected range",
                            "neutral",
                            0,
                            "There are no transactions for this customer in the selected filter window."
                    ),
                    0,
                    indicators
            );
        }

        double mean = transactions.stream().mapToDouble(TransactionRecord::getAmount).average().orElse(0);
        double stdDev = standardDeviation(transactions);
        long unusualAmountCount = stdDev > 0
                ? transactions.stream().filter(transaction -> transaction.getAmount() > mean + (3 * stdDev)).count()
                : 0;
        if (unusualAmountCount > 0) {
            indicators.add(unusualAmountCount + " transaction(s) exceed the customer's mean plus three standard deviations.");
            score += 1;
        }

        if (transactions.size() > 5) {
            List<Integer> hours = transactions.stream().map(transaction -> transaction.getTimestamp().getHour()).sorted().toList();
            double q02 = percentile(hours, 0.02);
            double q98 = percentile(hours, 0.98);
            long offHourCount = transactions.stream()
                    .filter(transaction -> transaction.getTimestamp().getHour() < q02 || transaction.getTimestamp().getHour() > q98)
                    .count();
            if (offHourCount > 0) {
                indicators.add(offHourCount + " transaction(s) occurred outside the customer's typical time window.");
                score += 1;
            }
        }

        List<TransactionRecord> sorted = transactions.stream()
                .sorted(Comparator.comparing(TransactionRecord::getTimestamp))
                .toList();
        for (int index = 1; index < sorted.size(); index++) {
            TransactionRecord current = sorted.get(index);
            TransactionRecord previous = sorted.get(index - 1);
            long minutes = Duration.between(previous.getTimestamp(), current.getTimestamp()).toMinutes();
            if (minutes >= 0 && minutes < 30 && !Objects.equals(previous.getLocation(), current.getLocation())) {
                indicators.add("Multiple locations were accessed within a 30-minute window.");
                score += 2;
                break;
            }
        }

        if (metrics.suspiciousCount() > 0) {
            indicators.add(metrics.suspiciousCount() + " historical transaction(s) are explicitly labeled suspicious in the dataset.");
            score += 2;
        }

        DashboardDto.Verdict verdict = score == 0
                ? new DashboardDto.Verdict(
                        "Account appears normal",
                        "success",
                        score,
                        "Behavior stays within the customer's normal historical range."
                )
                : score <= 2
                ? new DashboardDto.Verdict(
                        "Moderate risk behavior",
                        "warning",
                        score,
                        "Some behavioral anomalies are present and merit analyst review."
                )
                : new DashboardDto.Verdict(
                        "High risk - multiple suspicious indicators",
                        "danger",
                        score,
                        "Several anomaly signals overlap and suggest elevated fraud pressure."
                );

        return new RiskSnapshot(verdict, Math.min(score * 25.0, 100.0), indicators);
    }

    private List<DashboardDto.TimeSeriesPoint> buildTimeSeries(List<TransactionRecord> transactions) {
        return transactions.stream()
                .map(transaction -> new DashboardDto.TimeSeriesPoint(
                        transaction.getTimestamp().toString(),
                        round(transaction.getAmount()),
                        transaction.getTransactionType(),
                        transaction.isSuspicious()
                ))
                .toList();
    }

    private List<DashboardDto.CountPoint> buildHistogram(List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        double min = transactions.stream().mapToDouble(TransactionRecord::getAmount).min().orElse(0);
        double max = transactions.stream().mapToDouble(TransactionRecord::getAmount).max().orElse(0);
        if (Double.compare(min, max) == 0) {
            return List.of(new DashboardDto.CountPoint(formatCurrency(min), transactions.size()));
        }

        int bins = Math.min(8, transactions.size());
        double width = (max - min) / bins;
        long[] counts = new long[bins];
        for (TransactionRecord transaction : transactions) {
            int index = (int) Math.min(bins - 1, Math.floor((transaction.getAmount() - min) / width));
            counts[index]++;
        }

        List<DashboardDto.CountPoint> histogram = new ArrayList<>(bins);
        for (int index = 0; index < bins; index++) {
            double start = min + (index * width);
            double end = index == bins - 1 ? max : start + width;
            histogram.add(new DashboardDto.CountPoint(
                    formatCurrency(start) + " - " + formatCurrency(end),
                    counts[index]
            ));
        }

        return histogram;
    }

    private List<DashboardDto.CountPoint> buildBreakdown(
            List<TransactionRecord> transactions,
            Function<TransactionRecord, String> classifier
    ) {
        return transactions.stream()
                .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new DashboardDto.CountPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardDto.HeatmapPoint> buildHeatmap(List<TransactionRecord> transactions) {
        Map<String, Long> counts = transactions.stream()
                .collect(Collectors.groupingBy(
                        transaction -> key(transaction.getTimestamp().getDayOfWeek(), transaction.getTimestamp().getHour()),
                        Collectors.counting()
                ));

        List<DashboardDto.HeatmapPoint> points = new ArrayList<>(7 * 24);
        for (DayOfWeek day : DayOfWeek.values()) {
            String label = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            for (int hour = 0; hour < 24; hour++) {
                points.add(new DashboardDto.HeatmapPoint(label, hour, counts.getOrDefault(key(day, hour), 0L)));
            }
        }
        return points;
    }

    private String key(DayOfWeek dayOfWeek, int hour) {
        return dayOfWeek.name() + ":" + hour;
    }

    private Optional<String> modeValue(List<TransactionRecord> transactions, Function<TransactionRecord, String> classifier) {
        return transactions.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private double standardDeviation(List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }
        double mean = transactions.stream().mapToDouble(TransactionRecord::getAmount).average().orElse(0);
        double variance = transactions.stream()
                .mapToDouble(transaction -> Math.pow(transaction.getAmount() - mean, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private double percentile(List<Integer> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }

        double index = percentile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double weight = index - lowerIndex;
        return (sortedValues.get(lowerIndex) * (1 - weight)) + (sortedValues.get(upperIndex) * weight);
    }

    private DashboardDto.TransactionView toTransactionView(TransactionRecord transaction) {
        return new DashboardDto.TransactionView(
                transaction.getTransactionId(),
                transaction.getCustomerId(),
                transaction.getTimestamp().toString(),
                round(transaction.getAmount()),
                transaction.getTransactionType(),
                transaction.getLocation(),
                transaction.getDevice(),
                transaction.getMerchantCategory(),
                transaction.isSuspicious()
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatCurrency(double value) {
        return String.format(Locale.ENGLISH, "₹%,.0f", value);
    }

    private record RiskSnapshot(DashboardDto.Verdict verdict, double riskScore, List<String> indicators) {
    }
}
