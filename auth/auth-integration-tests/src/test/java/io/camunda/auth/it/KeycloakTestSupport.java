/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

/** Shared infrastructure for Keycloak integration tests. */
final class KeycloakTestSupport {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private KeycloakTestSupport() {}

  /** Creates a Keycloak testcontainer with optimized startup settings. */
  static KeycloakContainer createKeycloak() {
    return new KeycloakContainer()
        .withStartupTimeout(Duration.ofMinutes(5))
        .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1");
  }

  /**
   * Creates a Keycloak realm with the given clients using the admin API.
   *
   * @param keycloak the running Keycloak container
   * @param realmName the realm name to create
   * @param clients the client representations to register in the realm
   */
  static void createRealm(
      final KeycloakContainer keycloak,
      final String realmName,
      final List<ClientRepresentation> clients) {
    final RealmRepresentation realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(clients);

    try (Keycloak admin = keycloak.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  /**
   * Creates a confidential client representation with service account enabled.
   *
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @return the configured client representation
   */
  static ClientRepresentation createConfidentialClient(
      final String clientId, final String clientSecret) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setSecret(clientSecret);
    client.setEnabled(true);
    client.setServiceAccountsEnabled(true);
    client.setPublicClient(false);
    client.setDirectAccessGrantsEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    return client;
  }

  /**
   * Acquires an access token from Keycloak via the client_credentials grant.
   *
   * @param tokenEndpoint the full token endpoint URL
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @return the access token string
   */
  static String acquireToken(
      final String tokenEndpoint, final String clientId, final String clientSecret) {
    final String body =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

    try {
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Token acquisition failed with status %d: %s"
                .formatted(response.statusCode(), response.body()));
      }
      final Map<String, Object> tokenResponse =
          OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
      return (String) tokenResponse.get("access_token");
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to acquire token from Keycloak", e);
    }
  }

  /** Builds the token endpoint URL for a given realm. */
  static String tokenEndpoint(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/token";
  }

  /** Builds the issuer URI for a given realm. */
  static String issuerUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName;
  }

  /** Builds the authorization endpoint URL for a given realm. */
  static String authorizationUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/auth";
  }

  /** Builds the JWK Set URI for a given realm. */
  static String jwkSetUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/certs";
  }
}
