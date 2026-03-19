/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Shared Keycloak Testcontainer for OIDC integration tests. Uses the singleton container pattern to
 * avoid starting multiple Keycloak instances across test classes.
 */
final class KeycloakTestSupport {

  static final String CLIENT_ID = "gatekeeper-test-client";
  static final String CLIENT_SECRET = "test-secret";

  static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer().withRealmImportFile("keycloak/gatekeeper-test-realm.json");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    KEYCLOAK.start();
  }

  private KeycloakTestSupport() {}

  static void configureOidc(final DynamicPropertyRegistry registry) {
    final var issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/gatekeeper-test";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add("camunda.security.authentication.oidc.client-id", () -> CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.client-secret", () -> CLIENT_SECRET);
  }

  static String obtainAccessToken(final String username, final String password) throws Exception {
    final var tokenUri =
        KEYCLOAK.getAuthServerUrl() + "/realms/gatekeeper-test/protocol/openid-connect/token";
    final var body =
        "grant_type=password&client_id=%s&client_secret=%s&username=%s&password=%s"
            .formatted(CLIENT_ID, CLIENT_SECRET, username, password);

    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    try (var client = HttpClient.newHttpClient()) {
      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      final JsonNode json = MAPPER.readTree(response.body());
      return json.get("access_token").asText();
    }
  }
}
