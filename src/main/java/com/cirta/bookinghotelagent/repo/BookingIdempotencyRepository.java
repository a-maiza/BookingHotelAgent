package com.cirta.bookinghotelagent.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingIdempotencyRepository extends JpaRepository<BookingIdempotencyEntity, String> {
}
