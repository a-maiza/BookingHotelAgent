# 🏨 BookingHotelAgent

**Vrai agent IA** de réservation hôtelière (Spring Boot + LangChain4j + OpenAI) : le LLM orchestre lui-même les appels aux outils métier pour gérer la disponibilité, le devis, la réservation et l'envoi d'email.

---

## 🎯 Objectif du projet

Permettre une conversation naturelle de réservation :
1. Comprendre la demande utilisateur (message libre).
2. Extraire les champs structurés (ville, dates, chambre, etc.).
3. Compléter l'état de session progressivement.
4. Vérifier la disponibilité, calculer un devis, confirmer la réservation.
5. Envoyer l'email de confirmation.

---

## 🧠 Architecture

```text
Client → POST /api/agent/chat
           → AgentOrchestrator
               1. Charger/créer BookingRequestState (DB)
               2. BookingRequestParser → extraire champs structurés → merge state
               3. Construire message enrichi (contexte session + message utilisateur)
               4. BookingAgent.chat(sessionId, enrichedMessage)   ← LangChain4j AiServices
                    LLM décide autonomement :
                    ├── checkAvailability(...)    @Tool AvailabilityTool
                    ├── quote(...)                @Tool PricingTool
                    ├── createBooking(...)        @Tool BookingTool  (+ idempotence)
                    ├── sendBookingConfirmation() @Tool EmailTool
                    └── getPolicyInfo(...)        @Tool PolicyTool   (RAG vectoriel)
                    └── réponse texte finale
               5. Mapper réponse → AgentResponse { sessionId, status, payload, message }
```

Le résultat API contient :
- `sessionId`
- `status` (`MISSING_INFO`, `QUOTE_READY`, `BOOKING_CONFIRMED`, etc.)
- `payload`
- `message`

---

## 🤖 Pattern Agent IA (LangChain4j AiServices)

`BookingAgent` est une interface Java annotée `@SystemMessage`. `LlmConfig` la câble via `AiServices.builder()` :

```java
AiServices.builder(BookingAgent.class)
    .chatModel(chatModel)
    .tools(availabilityTool, pricingTool, bookingTool, emailTool, policyTool)
    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(30))
    .build();
```

**Fonctionnement interne d'un seul appel `chat()` :**

```text
1. Message + définitions JSON de tous les @Tool → envoyés au LLM
2. LLM répond avec un "tool_call"  (ex: checkAvailability)
3. LangChain4j exécute la méthode Java @Tool correspondante
4. Résultat ajouté au contexte de conversation
5. LLM rappelé → décide : autre tool ? ou réponse finale ?
   └── boucle jusqu'à réponse texte finale
```

Chaque méthode `@Tool` est introspectée automatiquement : son nom, sa description et ses paramètres sont convertis en définition de fonction JSON envoyée au LLM. Le LLM décide **lui-même** quand et avec quels arguments appeler chaque outil.

La mémoire de conversation est isolée par session via `@MemoryId String sessionId` (fenêtre de 30 messages).

---

## 📁 Structure du projet

```text
src/main/java/com/cirta/bookinghotelagent
├── api/                 # Contrôleurs REST + DTO API
├── ai/
│   ├── agent/           # BookingAgent (interface AiServices)
│   ├── structured/      # BookingRequestParser, BookingRequestState
│   └── tools/           # AvailabilityTool, PricingTool, BookingTool, EmailTool, PolicyTool
├── config/              # LlmConfig (ChatModel + AiServices bean), PolicyRagConfig
├── domain/              # Modèles métier (Booking, Quote, etc.)
├── integration/         # AmadeusClient (OAuth2 + hotel APIs)
├── rag/                 # PolicyIngestor (démarrage), PolicyRetriever (recherche sémantique)
├── repo/                # Entités + repositories JPA
└── service/
    ├── agent/           # AgentOrchestrator
    ├── BookingSessionStateStore.java
    └── BookingIdempotencyStore.java
```

---

## ⚙️ Prérequis

- Java 21
- Maven (ou `./mvnw`)
- Docker + Docker Compose
- Une clé OpenAI
- (Optionnel pour envoi réel) un SMTP accessible

---

## 🐘 PostgreSQL avec Docker Compose

Le projet est configuré pour PostgreSQL + **pgvector** avec le fichier `docker-compose.yml`.

### 1) Démarrer PostgreSQL

```bash
docker compose up -d postgres
```

### 2) Vérifier l'état

```bash
docker compose ps
docker compose logs -f postgres
```

Configuration par défaut du conteneur :

- DB : `bookinghotel`
- User : `user`
- Password : `password`
- Port local : `5432`

### 3) Vérifier l'extension pgvector

```bash
docker compose exec postgres psql -U user -d bookinghotel -c "\dx"
```

Tu dois voir l'extension `vector` dans la liste.

---

## 🔧 Configuration applicative

Variables d'environnement principales :

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/bookinghotel"
export SPRING_DATASOURCE_USERNAME="user"
export SPRING_DATASOURCE_PASSWORD="password"

export OPENAI_API_KEY="sk-..."
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="votre@email.com"
export SMTP_PASSWORD="mot_de_passe_app"

export AMADEUS_API_KEY="..."
export AMADEUS_API_SECRET="..."
export AMADEUS_BASE_URL="https://test.api.amadeus.com"
```

> Si vous n'avez pas de SMTP réel, l'application peut démarrer mais l'étape d'email peut échouer selon votre configuration.

---

## ▶️ Lancement du projet

### 1) Compiler

```bash
mvn clean package
```

### 2) Démarrer l'application

```bash
mvn spring-boot:run
```

Par défaut, l'API écoute sur :

- `http://localhost:8080`

---

## 🌍 Intégration Amadeus Self-Service (Hotel APIs)

L'agent peut utiliser Amadeus (environnement test) pour :

- vérifier les disponibilités via `GET /v1/reference-data/locations/hotels/by-city` puis `GET /v3/shopping/hotel-offers?hotelIds=...` ;
- créer une réservation via `POST /v2/booking/hotel-orders` ;
- récupérer automatiquement le token OAuth2 via `POST /v1/security/oauth2/token`.

Si `AMADEUS_API_KEY` / `AMADEUS_API_SECRET` ne sont pas fournis, l'application bascule sur le fallback local (inventaire en mémoire) pour continuer à fonctionner en mode démo.

---

## 🧪 Exemples d'appels cURL

### A. Conversation initiale (informations incomplètes)

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-1",
    "message": "Je veux réserver un hôtel à Paris"
  }'
```

### B. Continuer la même session

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-1",
    "message": "Arrivée le 2026-03-12, départ le 2026-03-14, chambre double pour 2 personnes, nom Karim Benali"
  }'
```

### C. Demande complète avec réservation immédiate

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-2",
    "message": "Je veux une chambre DOUBLE à Paris du 2026-03-12 au 2026-03-14 pour 2 personnes, nom Karim Benali, email karim@example.com, je confirme la réservation maintenant"
  }'
```

### D. Tester un cas de dates invalides

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-3",
    "message": "Réserve à Lyon du 2026-05-10 au 2026-05-08, chambre DOUBLE pour 2 personnes, nom Nora Saidi"
  }'
```

---

## 🧾 Statuts API possibles

- `MISSING_INFO`
- `INVALID_DATES`
- `NO_AVAILABILITY`
- `OFFER_SELECTION_REQUIRED`
- `QUOTE_READY`
- `EMAIL_REQUIRED`
- `BOOKING_CONFIRMED`
- `POLICY_INFO`
- `ERROR`

---

## 🔁 Idempotence de réservation

Pour éviter le double booking lors des retries réseau ou double-clic,
`BookingTool.createBooking()` calcule une clé SHA-256 à partir du devis + guest info et interroge `BookingIdempotencyStore` avant d'exécuter :

- Si une réservation identique est déjà finalisée → l'API renvoie la réservation existante.
- Si une réservation identique est déjà en cours → l'API refuse un second traitement concurrent.

---

## 📚 RAG policies (annulation, règles)

Le projet embarque une base de politiques hôtelières dans `src/main/resources/policies/hotel-policies.md` indexée via RAG vectoriel.

`PolicyTool` est exposé au LLM comme un `@Tool` ordinaire. Le LLM appelle lui-même `getPolicyInfo(question)` dès qu'il détecte une question sur les politiques (annulation, remboursement, check-in/out, modification, paiement).

Exemple :

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "policy-1",
    "message": "Quelle est votre politique d'\''annulation ?"
  }'
```

## 🧠 RAG vectoriel (LangChain4j + PGVector)

- Ingestion du fichier `policies/hotel-policies.md` au démarrage.
- Découpage par sections markdown (fallback par taille de chunks).
- Embeddings OpenAI (`text-embedding-3-small`) stockés dans PostgreSQL via `pgvector` (`policy_embeddings`).
- Récupération sémantique par similarité (`top-k=4`, `min-score=0.70`).

L'ingestion est **idempotente** : un hash SHA-256 du markdown est stocké en DB (`rag_ingestion_state`) et l'index n'est reconstruit que si le contenu change.

Exemples de tests policy :

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"policy-cancel-1","message":"Quelle est votre politique d'\''annulation ?"}'

curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"policy-checkin-1","message":"C'\''est quoi les règles de check-in/check-out ?"}'
```

---

## 🧩 Choisir une offre Amadeus

Quand des offres Amadeus sont trouvées, l'API peut renvoyer `status = OFFER_SELECTION_REQUIRED` avec la liste d'`offerId` dans le payload disponibilité.
Ensuite, envoie un message contenant l'`offerId` choisi (ex: `Je choisis l'offre XXX`) pour que le devis et la réservation utilisent cette même offre.
