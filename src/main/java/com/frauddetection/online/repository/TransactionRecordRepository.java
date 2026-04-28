package com.frauddetection.online.repository;

import com.frauddetection.online.domain.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            String customerId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );

    @Query("select distinct t.customerId from TransactionRecord t order by t.customerId")
    List<String> findDistinctCustomerIds();

    @Query("select distinct t.transactionType from TransactionRecord t order by t.transactionType")
    List<String> findDistinctTransactionTypes();

    @Query("select distinct t.location from TransactionRecord t order by t.location")
    List<String> findDistinctLocations();

    @Query("select distinct t.device from TransactionRecord t order by t.device")
    List<String> findDistinctDevices();

    @Query("select distinct t.merchantCategory from TransactionRecord t order by t.merchantCategory")
    List<String> findDistinctMerchantCategories();

    TransactionRecord findFirstByOrderByTimestampAsc();

    TransactionRecord findFirstByOrderByTimestampDesc();
}
