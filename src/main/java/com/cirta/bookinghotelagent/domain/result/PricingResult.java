package com.cirta.bookinghotelagent.domain.result;

import com.cirta.bookinghotelagent.domain.Quote;

public record PricingResult(
        Status status,
        String message,
        Quote quote
) {
    public enum Status { OK, INVALID_INPUT, ERROR }
}
