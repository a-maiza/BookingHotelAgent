# 🏨 BookingHotelAgent

Agent de réservation d’hôtel **piloté par backend** (Spring Boot + LangChain4j + OpenAI), avec gestion de session, devis, réservation et envoi d’email.

---

## 🎯 Objectif du projet

Permettre une conversation naturelle de réservation tout en gardant le contrôle côté serveur :
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

Le résultat API contient :
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
├── config/              # Configuration Spring / LLM
├── domain/              # Modèles métier (Booking, Quote, etc.)
├── rag/                 # Ingestion/retrieval de policies (RAG)
├── repo/                # Entités + repositories JPA
└── service/
    ├── agent/           # Orchestration métier principale
    └── ...              # Services utilitaires
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

Le projet est maintenant configuré pour PostgreSQL avec le fichier `docker-compose.yml`.

### 1) Démarrer PostgreSQL

```bash
docker compose up -d postgres
```

### 2) Vérifier l’état

```bash
docker compose ps
docker compose logs -f postgres
```

Configuration par défaut du conteneur :
- DB : `bookinghotel`
- User : `booking_user`
- Password : `booking_password`
- Port local : `5432`

---

## 🔧 Configuration applicative

Variables d’environnement principales :

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/bookinghotel"
export SPRING_DATASOURCE_USERNAME="booking_user"
export SPRING_DATASOURCE_PASSWORD="booking_password"

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
mvn clean package
```

### 2) Démarrer l’application
```bash
mvn spring-boot:run
```

Par défaut, l’API écoute sur :
- `http://localhost:8080`

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
- `QUOTE_READY`
- `EMAIL_REQUIRED`
- `BOOKING_CONFIRMED`
- `ERROR`


---

## 🔁 Idempotence de réservation

Pour éviter le double booking lors des retries réseau ou double-clic,
la confirmation de réservation est protégée par une clé d'idempotence calculée à partir
du contexte de réservation (session + séjour + client).

- si une réservation identique est déjà finalisée, l'API renvoie la réservation existante ;
- si une réservation identique est déjà en cours, l'API évite un second traitement concurrent.
