package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.rag.PolicyRetriever;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PolicyTool {

    private final PolicyRetriever policyRetriever;

    public PolicyTool(PolicyRetriever policyRetriever) {
        this.policyRetriever = policyRetriever;
    }

    @Tool("Répond aux questions sur les politiques de l'hôtel : annulation, check-in/out, paiement, modification, remboursement.")
    public String getPolicyInfo(String question) {
        return policyRetriever.retrieve(question);
    }
}
