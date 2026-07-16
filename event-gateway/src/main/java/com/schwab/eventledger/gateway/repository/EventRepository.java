package com.schwab.eventledger.gateway.repository;

import com.schwab.eventledger.gateway.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
