package com.cirta.bookinghotelagent.domain.result;

public record EmailSendResult(
        Status status,
        String message
) {
    public enum Status { OK, INVALID_INPUT, ERROR }
}
