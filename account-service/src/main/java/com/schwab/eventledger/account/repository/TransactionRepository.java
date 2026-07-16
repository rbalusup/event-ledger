package com.schwab.eventledger.account.repository;

import com.schwab.eventledger.account.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByEventId(String eventId);

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
