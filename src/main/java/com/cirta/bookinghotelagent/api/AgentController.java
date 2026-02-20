package com.cirta.bookinghotelagent.api;

import com.cirta.bookinghotelagent.ai.AgentFactory;
import com.cirta.bookinghotelagent.service.AgentOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
//    private final AgentFactory agentFactory;
//
//    public AgentController(AgentFactory agentFactory) {
//        this.agentFactory = agentFactory;
//    }
    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }


    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
//        var agent = agentFactory.forSession(req.sessionId());
//        String answer = agent.chat(req.message());
//        return new ChatResponse(req.sessionId(), answer);
        String answer = orchestrator.handle(req.sessionId(), req.message());
        return new ChatResponse(req.sessionId(), answer);
    }
}
