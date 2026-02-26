# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Start the database first (required)
docker compose up -d postgres

# Build
mvn clean package

# Run
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=BookingHotelAgentApplicationTests
```

The app starts on `http://localhost:8080`. The Spring context test (`contextLoads`) requires a running PostgreSQL instance and a valid `OPENAI_API_KEY`.

## Required Environment Variables

| Variable | Required | Notes |
|---|---|---|
| `OPENAI_API_KEY` | Yes | Used for GPT and embeddings |
| `SPRING_DATASOURCE_URL` | No | Defaults to `jdbc:postgresql://localhost:5432/bookinghotel` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | No | Defaults to `user` / `password` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | No | Email sending; app starts without them but email step will fail |
| `AMADEUS_API_KEY` / `AMADEUS_API_SECRET` | No | Falls back to in-memory inventory if absent |

## Architecture

This is a hotel booking chatbot with a single REST endpoint:

```
POST /api/agent/chat  { sessionId, message }  →  { sessionId, status, payload, message }
```

### Request Flow

```
AgentController
  → AgentOrchestrator
      1. Load/create BookingRequestState from DB (session persistence)
      2. Parse user message via BookingRequestParser (LLM structured extraction)
      3. Merge parsed fields into state, save state
      4. Call BookingAgent.chat(sessionId, enrichedMessage)  ← LangChain4j AiServices
           LLM autonomously calls @Tool methods:
           - AvailabilityTool.checkAvailability(...)
           - PricingTool.quote(...)
           - BookingTool.createBooking(...)   ← includes idempotency
           - EmailTool.sendBookingConfirmationEmail(...)
           - PolicyTool.getPolicyInfo(...)    ← RAG retrieval
      5. Map LLM response text → AgentResponse with AgentStatus
```

### Key Design Points

**True AI Agent via LangChain4j AiServices**: `BookingAgent` is an interface annotated with `@SystemMessage`. `LlmConfig` wires it with `AiServices.builder()`, binding all `@Tool`-annotated components and providing per-session `MessageWindowChatMemory` (keyed by `sessionId`).

**Session state**: `BookingRequestState` (city, dates, roomType, guests, guestFullName, email, selectedOfferId) is persisted in PostgreSQL via `BookingSessionStateStore`. It is merged incrementally with each turn and injected into the enriched user message so the LLM has full context for tool parameters.

**Structured extraction**: `BookingRequestParser` uses the LLM with a JSON Schema response format to extract booking fields from free-text messages. This runs before the AiServices call to accumulate state.

**Idempotency**: `BookingTool.createBooking()` computes a SHA-256 fingerprint from the quote + guest info, checks `BookingIdempotencyStore` for duplicates, claims the key before executing, and marks it completed (or releases on failure).

**RAG for policies**: At startup, `PolicyIngestor` loads `src/main/resources/policies/hotel-policies.md`, splits it, embeds it via OpenAI (`text-embedding-3-small`), and stores vectors in PostgreSQL pgvector (`policy_embeddings` table). Ingestion is idempotent via SHA-256 hash tracked in `rag_ingestion_state`. `PolicyRetriever` does semantic search (top-k=4, min-score=0.70) and is exposed to the LLM as `PolicyTool`.

**Amadeus integration**: `AmadeusClient` handles OAuth2 token management and hotel search/offer/booking APIs. All tools fall back to in-memory inventory or local booking references when Amadeus is disabled.

**DDL**: Hibernate runs `ddl-auto: update` — tables are created/updated automatically on startup. No migration scripts.

### AgentStatus values returned by the API

`MISSING_INFO`, `INVALID_DATES`, `NO_AVAILABILITY`, `OFFER_SELECTION_REQUIRED`, `QUOTE_READY`, `EMAIL_REQUIRED`, `BOOKING_CONFIRMED`, `POLICY_INFO`, `ERROR`

### Package layout

```
com.cirta.bookinghotelagent
├── api/                  # AgentController, ChatRequest, AgentResponse, AgentStatus
├── ai/
│   ├── agent/            # BookingAgent (AiServices interface)
│   ├── structured/       # BookingRequestParser, BookingRequestState, BookingRequestDraft
│   └── tools/            # AvailabilityTool, PricingTool, BookingTool, EmailTool, PolicyTool
├── config/               # LlmConfig (ChatModel + AiServices bean), PolicyRagConfig
├── domain/               # Booking, Quote, Guest, RoomType, result DTOs
├── integration/          # AmadeusClient (OAuth2 + hotel APIs)
├── rag/                  # PolicyIngestor (startup), PolicyRetriever (semantic search)
├── repo/                 # JPA entities + repositories
└── service/
    ├── agent/            # AgentOrchestrator
    ├── BookingSessionStateStore.java
    └── BookingIdempotencyStore.java
```
