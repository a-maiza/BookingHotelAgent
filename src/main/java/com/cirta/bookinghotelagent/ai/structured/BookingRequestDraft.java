package com.cirta.bookinghotelagent.ai.structured;

import dev.langchain4j.model.output.structured.Description;

public record BookingRequestDraft(
        @Description("Ville de destination, ex: Paris")
        String city,

        @Description("Date d'arrivée au format YYYY-MM-DD")
        String checkIn,

        @Description("Date de départ au format YYYY-MM-DD (strictement après checkIn)")
        String checkOut,

        @Description("Type de chambre. Valeurs possibles: DOUBLE ou SUITE")
        String roomType,

        @Description("Nombre de voyageurs (entier positif)")
        Integer guests,

        @Description("Budget cible par nuit en EUR (optionnel)")
        Double budgetPerNight,

        @Description("Nom du client (optionnel mais requis pour envoyer une confirmation)")
        String guestFullName,

        @Description("Email du client (optionnel mais requis pour envoyer une confirmation)")
        String email,

        @Description("Vrai si l'utilisateur demande explicitement de réserver maintenant (ex: 'réserve', 'confirme')")
        Boolean wantsToBookNow
) {}
