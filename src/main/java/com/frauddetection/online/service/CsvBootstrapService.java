package com.frauddetection.online.service;

import com.frauddetection.online.domain.TransactionRecord;
import com.frauddetection.online.repository.TransactionRecordRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvBootstrapService.class);
    private static final int BATCH_SIZE = 1_000;

    private final TransactionRecordRepository repository;
    private final ResourceLoader resourceLoader;
    private final boolean bootstrapEnabled;
    private final String csvPath;

    public CsvBootstrapService(
            TransactionRecordRepository repository,
            ResourceLoader resourceLoader,
            @Value("${app.bootstrap.enabled:true}") boolean bootstrapEnabled,
            @Value("${app.bootstrap.csv-path}") String csvPath
    ) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
        this.bootstrapEnabled = bootstrapEnabled;
        this.csvPath = csvPath;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!bootstrapEnabled || repository.count() > 0) {
            return;
        }

        Resource resource = resourceLoader.getResource(csvPath);
        if (!resource.exists()) {
            throw new IllegalStateException("CSV dataset not found at " + csvPath);
        }

        List<TransactionRecord> batch = new ArrayList<>(BATCH_SIZE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader);

            for (CSVRecord record : records) {
                batch.add(toEntity(record));
                if (batch.size() == BATCH_SIZE) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            repository.saveAll(batch);
        }

        log.info("Imported transaction dataset into MySQL: {} records", repository.count());
    }

    private TransactionRecord toEntity(CSVRecord record) {
        TransactionRecord entity = new TransactionRecord();
        entity.setTransactionId(record.get("transaction_id"));
        entity.setCustomerId(record.get("customer_id"));
        entity.setTimestamp(LocalDateTime.parse(record.get("timestamp").replace(" ", "T")));
        entity.setAmount(Double.parseDouble(record.get("amount")));
        entity.setTransactionType(record.get("transaction_type"));
        entity.setLocation(record.get("location"));
        entity.setDevice(record.get("device"));
        entity.setMerchantCategory(record.get("merchant_category"));
        entity.setSuspicious("1".equals(record.get("is_suspicious")));
        return entity;
    }
}
