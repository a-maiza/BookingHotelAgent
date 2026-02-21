# 🏨 BookingHotelAgent

Agent de réservation d’hôtel **piloté par backend** (Spring Boot + LangChain4j + OpenAI), avec gestion de session, devis, réservation et envoi d’email.

Ce projet montre une approche robuste :
- le **LLM extrait** des informations structurées,
- le backend **orchestré** décide des étapes métier,
- les tools métier réalisent les actions (dispo, prix, booking, email),
- l’API retourne un statut explicite à chaque étape.

---

## 🎯 Objectif du projet

Permettre une conversation naturelle de réservation, tout en gardant le contrôle côté serveur :
1. Comprendre la demande utilisateur (message libre).
2. Extraire les champs structurés (ville, dates, chambre, etc.).
3. Compléter l’état de session progressivement.
4. Vérifier la disponibilité.
5. Calculer un devis.
6. Confirmer/réaliser la réservation.
7. Envoyer l’email de confirmation.

---

## 🧠 Architecture (vue simple)

`Client -> /api/agent/chat -> AgentOrchestrator`

L’orchestrateur enchaîne :
- `BookingRequestParser` (LLM, extraction structurée),
- `BookingSessionStateStore` (persist/restore état de session),
- tools métier :
  - `AvailabilityTool`,
  - `PricingTool`,
  - `BookingTool`,
  - `EmailTool`.

Le résultat est retourné sous forme de :
- `sessionId`
- `status` (`MISSING_INFO`, `QUOTE_READY`, `BOOKING_CONFIRMED`, etc.)
- `payload`
- `message`

---

## 📁 Structure du projet

```text
src/main/java/com/cirta/bookinghotelagent
├── api/                 # Contrôleurs REST + DTO API
├── ai/
│   ├── structured/      # Parsing structuré de la demande utilisateur
│   └── tools/           # Wrappers tools utilisés par l’agent
├── config/              # Configuration Spring / LLM / H2
├── domain/              # Modèles métier (Booking, Quote, etc.)
├── rag/                 # Ingestion/retrieval de policies (RAG)
├── repo/                # Entités + repositories JPA
└── service/
    ├── agent/           # Orchestration métier principale
    └── ...              # Services utilitaires
```

Fichiers clés :
- `AgentController` : endpoint REST `/api/agent/chat`.
- `AgentOrchestrator` : logique de décision étape par étape.
- `application.yaml` : datasource H2, mail, clé OpenAI, logs.

---

## ⚙️ Prérequis

- Java 21
- Maven (ou `./mvnw`)
- Une clé OpenAI
- (Optionnel pour envoi réel) un SMTP accessible

---

## 🔧 Configuration

Variables d’environnement recommandées :

```bash
export OPENAI_API_KEY="sk-..."
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="votre@email.com"
export SMTP_PASSWORD="mot_de_passe_app"
```

> Si vous n’avez pas de SMTP réel, l’application peut démarrer mais l’étape d’email peut échouer selon votre configuration.

---

## ▶️ Lancement du projet

### 1) Compiler
```bash
./mvnw clean package
```

### 2) Démarrer
```bash
./mvnw spring-boot:run
```

Par défaut, l’API écoute sur :
- `http://localhost:8080`

H2 Console (activée) :
- `http://localhost:8080/h2-console`

---

## 🧪 Exemples d’appels cURL

### A. Conversation initiale (informations incomplètes)

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-1",
    "message": "Je veux réserver un hôtel à Paris"
  }'
```

Réponse attendue (exemple) :
- `status: "MISSING_INFO"`
- `message: "Quelle est ta date d’arrivée (YYYY-MM-DD) ?"` (ou autre question manquante)

---

### B. Continuer la même session

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-1",
    "message": "Arrivée le 2026-03-12, départ le 2026-03-14, chambre double pour 2 personnes, nom Karim Benali"
  }'
```

L’agent poursuit la collecte et peut retourner :
- `MISSING_INFO` (si encore des champs manquent),
- `QUOTE_READY` (devis prêt si l’utilisateur n’a pas demandé de réserver immédiatement),
- `EMAIL_REQUIRED` (si réservation demandée sans email).

---

### C. Demande complète avec réservation immédiate

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-2",
    "message": "Je veux une chambre DOUBLE à Paris du 2026-03-12 au 2026-03-14 pour 2 personnes, nom Karim Benali, email karim@example.com, je confirme la réservation maintenant"
  }'
```

Réponse attendue (si dispo + SMTP OK) :
- `status: "BOOKING_CONFIRMED"`
- `payload`: détail de réservation (référence, montants, etc.)
- `message`: confirmation finale.

---

### D. Tester un cas de dates invalides

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "demo-3",
    "message": "Réserve à Lyon du 2026-05-10 au 2026-05-08, chambre DOUBLE pour 2 personnes, nom Nora Saidi"
  }'
```

Réponse attendue :
- `status: "INVALID_DATES"`
- message demandant une date de départ valide.

---

## 🧾 Statuts API possibles

- `MISSING_INFO` : il manque des informations.
- `INVALID_DATES` : check-out <= check-in.
- `NO_AVAILABILITY` : aucune chambre dispo.
- `QUOTE_READY` : devis calculé, en attente de confirmation.
- `EMAIL_REQUIRED` : email nécessaire avant finalisation.
- `BOOKING_CONFIRMED` : réservation créée + email envoyé.
- `ERROR` : erreur technique.

---

## ✅ Pourquoi cette approche est fiable

- Le LLM n’exécute pas d’action métier sensible.
- La logique de décision est déterministe côté backend.
- Les étapes sont traçables via des statuts API.
- L’état de conversation est maintenu par `sessionId`.

---

## 🔭 Pistes d’amélioration

- Passage de H2 vers PostgreSQL.
- Idempotence forte des réservations.
- Historique multi-sessions par utilisateur.
- RAG enrichi (politiques annulation, check-in/check-out, etc.).
- Monitoring plus avancé (traces tools + latence LLM).
