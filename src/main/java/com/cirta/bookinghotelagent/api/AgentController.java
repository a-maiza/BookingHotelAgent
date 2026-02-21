package com.cirta.bookinghotelagent.api;

import com.cirta.bookinghotelagent.service.agent.AgentOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody ChatRequest req) {
        return orchestrator.handle(req.sessionId(), req.message());
    }
}
