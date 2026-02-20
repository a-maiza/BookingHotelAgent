package com.cirta.bookinghotelagent.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record Guest(
        @NotBlank String fullName,
        @Email String email
) {
}
