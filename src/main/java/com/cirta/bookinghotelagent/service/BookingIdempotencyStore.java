package com.cirta.bookinghotelagent.service;

import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.repo.BookingIdempotencyEntity;
import com.cirta.bookinghotelagent.repo.BookingIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class BookingIdempotencyStore {

    public enum BookingRequestStatus {
        IN_PROGRESS,
        COMPLETED
    }

    private final BookingIdempotencyRepository repository;
    private final ObjectMapper mapper;

    public BookingIdempotencyStore(BookingIdempotencyRepository repository) {
        this.repository = repository;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findCompletedBooking(String idempotencyKey) {
        return repository.findById(idempotencyKey)
                .filter(e -> BookingRequestStatus.COMPLETED.name().equals(e.getStatus()))
                .map(BookingIdempotencyEntity::getBookingJson)
                .map(this::fromJson);
    }

    @Transactional
    public boolean claim(String idempotencyKey) {
        Instant now = Instant.now();
        BookingIdempotencyEntity entity = new BookingIdempotencyEntity(
                idempotencyKey,
                BookingRequestStatus.IN_PROGRESS.name(),
                null,
                now,
                now
        );

        try {
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Transactional
    public void markCompleted(String idempotencyKey, Booking booking) {
        BookingIdempotencyEntity entity = repository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key introuvable: " + idempotencyKey));

        entity.setStatus(BookingRequestStatus.COMPLETED.name());
        entity.setBookingJson(toJson(booking));
        entity.setUpdatedAt(Instant.now());

        repository.save(entity);
    }

    private String toJson(Booking booking) {
        try {
            return mapper.writeValueAsString(booking);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de sérialiser Booking", e);
        }
    }

    private Booking fromJson(String json) {
        try {
            return mapper.readValue(json, Booking.class);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de désérialiser Booking: " + json, e);
        }
    }
}
