package com.cirta.bookinghotelagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class AmadeusClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;

    private String accessToken;
    private Instant expiresAt;

    public AmadeusClient(@Value("${app.amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
                         @Value("${app.amadeus.api-key:}") String apiKey,
                         @Value("${app.amadeus.api-secret:}") String apiSecret,
                         ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    public Optional<JsonNode> searchHotelOffersByCity(String cityCode, String checkInDate, String checkOutDate, int adults) {
        if (!enabled()) {
            return Optional.empty();
        }

        String token = getAccessToken();
        String hotelIds = resolveHotelIds(cityCode, token);
        if (hotelIds.isBlank()) {
            return Optional.empty();
        }

        String body = restClient.get()
                .uri(uri -> uri.path("/v3/shopping/hotel-offers")
                        .queryParam("hotelIds", hotelIds)
                        .queryParam("checkInDate", checkInDate)
                        .queryParam("checkOutDate", checkOutDate)
                        .queryParam("adults", adults)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        try {
            return Optional.ofNullable(objectMapper.readTree(body));
        } catch (Exception e) {
            throw new IllegalStateException("Réponse Amadeus invalide (hotel-offers)", e);
        }
    }

    public Optional<JsonNode> createHotelBooking(String hotelOfferId, String firstName, String lastName, String email) {
        if (!enabled()) {
            return Optional.empty();
        }

        String token = getAccessToken();
        String payload = """
                {
                  "data": {
                    "type": "hotel-order",
                    "guests": [
                      {
                        "tid": 1,
                        "title": "MR",
                        "firstName": "%s",
                        "lastName": "%s",
                        "phone": "+33679278416",
                        "email": "%s"
                      }
                    ],
                    "travelAgent": {
                      "contact": {
                        "email": "%s"
                      }
                    },
                    "roomAssociations": [
                      {
                        "guestReferences": [
                          {
                            "guestReference": "1"
                          }
                        ],
                        "hotelOfferId": "%s"
                      }
                    ],
                    "payment": {
                      "method": "CREDIT_CARD",
                      "paymentCard": {
                        "paymentCardInfo": {
                          "vendorCode": "VI",
                          "cardNumber": "4151289722471370",
                          "expiryDate": "2026-08",
                          "holderName": "%s %s"
                        }
                      }
                    }
                  }
                }
                """.formatted(
                        escape(firstName),
                        escape(lastName),
                        escape(email),
                        escape(email),
                        escape(hotelOfferId),
                        escape(firstName),
                        escape(lastName)
                );

        String body = restClient.post()
                .uri("/v2/booking/hotel-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            return Optional.ofNullable(objectMapper.readTree(body));
        } catch (Exception e) {
            throw new IllegalStateException("Réponse Amadeus invalide (hotel-orders)", e);
        }
    }

    private String resolveHotelIds(String cityCode, String token) {
        String body = restClient.get()
                .uri(uri -> uri.path("/v1/reference-data/locations/hotels/by-city")
                        .queryParam("cityCode", cityCode)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode data = json.path("data");
            if (!data.isArray()) {
                return "";
            }

            return StreamSupport.stream(data.spliterator(), false)
                    .map(item -> item.path("hotelId").asText(""))
                    .filter(id -> !id.isBlank())
                    .limit(20)
                    .collect(Collectors.joining(","));
        } catch (Exception e) {
            throw new IllegalStateException("Réponse Amadeus invalide (hotel by city)", e);
        }
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return accessToken;
        }

        String body = restClient.post()
                .uri("/v1/security/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + apiSecret)
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(body);
            accessToken = json.path("access_token").asText();
            long expiresIn = json.path("expires_in").asLong(1799);
            expiresAt = Instant.now().plusSeconds(expiresIn);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Token Amadeus manquant");
            }
            return accessToken;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de récupérer le token Amadeus", e);
        }
    }

    private static String escape(String v) {
        return v == null ? "" : v.replace("\"", "");
    }
}
