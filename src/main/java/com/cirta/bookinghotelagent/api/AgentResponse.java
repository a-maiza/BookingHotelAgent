package com.cirta.bookinghotelagent.api;

public record AgentResponse(
        String sessionId,
        AgentStatus status,
        Object payload,
        String message
) {}
