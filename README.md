# 🏨 Hotel Agent – AI Reservation System (Spring Boot + LangChain4j)

Un agent intelligent de réservation d’hôtel construit avec :

- Java 21
- Spring Boot 4
- LangChain4j
- OpenAI
- SMTP réel (email confirmation)

Ce projet démontre une architecture production-ready avec :

- ✅ Extraction structurée (JSON Schema)
- ✅ Orchestration backend déterministe
- ✅ Gestion d’état par session
- ✅ Tools métier (availability, pricing, booking, email)
- ✅ Envoi réel d’email via SMTP
- ❌ Aucune hallucination de réservation

---

# 🎯 Objectif

Créer un agent capable de :

1. Comprendre une demande utilisateur libre
2. Extraire les informations nécessaires
3. Valider les données
4. Vérifier la disponibilité
5. Calculer un devis
6. Créer une réservation
7. Envoyer un email de confirmation

Tout en gardant le contrôle total côté backend.

---

# 🧠 Architecture

User → REST API → Structured Parser (LLM)  
            ↓  
      BookingRequestDraft (JSON)  
            ↓  
      BookingRequestState (session)  
            ↓  
      AgentOrchestrator  
            ↓  
Availability → Quote → Booking → Email

---

# 📂 Structure du projet

com.example.hotelagent

- api/
    - AgentController.java
    - ChatRequest.java
    - ChatResponse.java

- ai/
    - structured/
        - BookingRequestDraft.java
        - BookingRequestState.java
        - BookingRequestParser.java

- service/
    - AgentOrchestrator.java

- tools/
    - AvailabilityTool.java
    - PricingTool.java
    - BookingTool.java
    - EmailTool.java

- domain/
    - Availability.java
    - Quote.java
    - Booking.java

---

# 🚀 Fonctionnement

## 1️⃣ Extraction structurée (LLM)

Le modèle OpenAI ne réserve rien directement.

Il convertit le message utilisateur en JSON structuré conforme à un schéma strict.

Exemple :

{
"city": "Paris",
"checkIn": "2026-03-12",
"checkOut": "2026-03-14",
"roomType": "DOUBLE",
"guests": 2,
"budgetPerNight": 180,
"guestFullName": "Maiza Abdeldjalil",
"email": "moi@example.com",
"wantsToBookNow": true
}

Aucune logique métier n’est confiée au LLM.

---

## 2️⃣ Orchestration backend

AgentOrchestrator décide :

- quelles données manquent
- quand poser une question
- quand appeler les tools
- quand envoyer l’email

Cela évite :

- ❌ hallucination de prix
- ❌ fausse référence de réservation
- ❌ faux email envoyé

---

## 3️⃣ Tools métier

### 🔹 AvailabilityTool
Vérifie la disponibilité (inventaire en mémoire pour la démo).

### 🔹 PricingTool
Calcule le prix par nuit, les taxes et le total.

### 🔹 BookingTool
Crée la réservation et génère une référence unique.

### 🔹 EmailTool
Envoie un email SMTP réel avec confirmation.

---

# ⚙️ Configuration

## Variables d’environnement

export OPENAI_API_KEY="sk-..."
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="your@email.com"
export SMTP_PASSWORD="app_password"

⚠️ Si Gmail : utiliser un App Password.

---

# 🧪 Test API

## Requête complète

curl -X POST http://localhost:8080/api/agent/chat \
-H "Content-Type: application/json" \
-d '{
"sessionId": "demo-1",
"message": "Je veux une chambre double à Paris du 2026-03-12 au 2026-03-14 pour 2 personnes, budget 180€/nuit. Réserve au nom de Maiza Abdeldjalil et envoie à moi@example.com"
}'

Comportement attendu :

- Vérification disponibilité
- Calcul du devis
- Création de la réservation
- Envoi email
- Retour de la référence + récapitulatif

---

## Requête progressive

{
"sessionId": "demo-2",
"message": "Je veux réserver à Paris"
}

Réponse :

"Quelle est ta date d’arrivée (YYYY-MM-DD) ?"

L’état est conservé par session.

---

# 🛡 Pourquoi cette architecture est robuste

Approche classique :
- LLM pilote tout
- Risque d’hallucination
- Réponses non structurées
- Difficile à tester

Approche utilisée ici :
- Backend pilote tout
- Contrôle total
- JSON Schema strict
- Workflow déterministe et testable

---

# 📈 Évolutions possibles

- 🔄 Persistance DB (Postgres + JPA)
- 🔁 Idempotence des réservations
- 🧾 Historique utilisateur
- 📚 RAG (policies PDF : annulation, check-in)
- 🏨 Intégration API réelle (Amadeus, RapidAPI)
- 📊 Observabilité (logs tool calls + tracing)

---

# 🧩 Concepts LangChain4j utilisés

- ChatLanguageModel
- JSON Schema Structured Output
- ResponseFormat(JSON)
- SystemMessage strict
- Tool abstraction
- Session state backend

---

# 🎓 Ce que ce projet démontre

✔️ Utilisation propre de LangChain4j en production  
✔️ Prévention des hallucinations LLM  
✔️ Combinaison IA + logique métier Java  
✔️ Architecture agent scalable et maintenable

---

# 📌 Stack technique

- Java 21
- Spring Boot 4
- LangChain4j 1.11.x
- OpenAI GPT-4.1-mini
- SMTP réel

---

# 👨‍💻 Auteur

Projet expérimental de montée en compétence sur :

- Agents IA
- Orchestration backend
- Structured outputs
- Architecture IA robuste