package com.cirta.bookinghotelagent.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSessionStateRepository extends JpaRepository<BookingSessionStateEntity, String> {
}
