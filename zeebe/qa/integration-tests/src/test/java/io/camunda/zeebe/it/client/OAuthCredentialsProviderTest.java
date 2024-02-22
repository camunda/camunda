/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.CredentialsProvider;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
final class OAuthCredentialsProviderTest {
  private static final int ADMIN_PORT = 4445;
  private static final int PUBLIC_PORT = 4444;
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private final GenericContainer<?> oauthContainer =
      new GenericContainer<>(DockerImageName.parse("oryd/hydra:v1.11"))
          .withEnv("DSN", "memory")
          .withExposedPorts(ADMIN_PORT, PUBLIC_PORT)
          .waitingFor(new HostPortWaitStrategy())
          .withCommand("serve all --dangerous-force-http")
          .withNetwork(NETWORK);

  @BeforeEach
  void beforeEach() throws IOException, InterruptedException, URISyntaxException {
    ensureOAuthClientExists();
  }

  @AfterAll
  static void afterAll() {
    NETWORK.close();
  }

  @Test
  void shouldFetchValidOAuthToken(@TempDir final Path cacheDir)
      throws IOException, InterruptedException {
    // given
    final var credentialsProvider =
        CredentialsProvider.newCredentialsProviderBuilder()
            .audience("zeebe")
            .clientId("zeebe")
            .clientSecret("secret")
            .credentialsCachePath(cacheDir.resolve("cache").toString())
            .authorizationServerUrl(getTokenEndpoint())
            .build();
    final var metadata = new Metadata();
    final var authHeaderKey = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    // when
    credentialsProvider.applyCredentials(metadata);

    // then
    final var authHeaderValue = metadata.get(authHeaderKey);
    assertThat(authHeaderValue).as("should have authenticated against the provider").isNotNull();
    assertThatAuthorizationHeaderIsValid(authHeaderValue);
  }

  private void assertThatAuthorizationHeaderIsValid(final String authHeaderValue)
      throws IOException, InterruptedException {
    final HttpResponse<byte[]> response = validateToken(authHeaderValue);
    final Map<String, Object> payload =
        new ObjectMapper().readValue(response.body(), new TypeReference<>() {});

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(payload).containsEntry("active", true);
  }

  private HttpResponse<byte[]> validateToken(final String authHeaderValue)
      throws IOException, InterruptedException {
    final var httpClient = HttpClient.newHttpClient();
    final var token = authHeaderValue.replace("Bearer ", "");
    final var requestBody = BodyPublishers.ofString("token=" + token);
    final var request =
        HttpRequest.newBuilder(getAdminEndpoint("oauth2/introspect"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(requestBody)
            .build();

    return httpClient.send(request, BodyHandlers.ofByteArray());
  }

  private void ensureOAuthClientExists() throws IOException, InterruptedException {
    final var httpClient = HttpClient.newHttpClient();
    final var zeebeOauthClient =
        """
        {
          "client_id": "zeebe", "client_secret": "secret", "client_name": "zeebe",
          "grant_types": ["client_credentials"], "audience": ["zeebe"], "response_types": ["code"],
          "token_endpoint_auth_method": "client_secret_post"
        }
        """;
    final var request =
        HttpRequest.newBuilder(getAdminEndpoint("clients"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(zeebeOauthClient))
            .build();
    final var response = httpClient.send(request, BodyHandlers.discarding());

    assertThat(response.statusCode())
        .as("should return 201 when successfully creating OAuth2 client")
        .isEqualTo(201);
  }

  private String getTokenEndpoint() {
    return String.format(
        "http://%s:%d/oauth2/token",
        oauthContainer.getHost(), oauthContainer.getMappedPort(PUBLIC_PORT));
  }

  private URI getAdminEndpoint(final String path) {
    final var endpoint =
        String.format(
            "http://%s:%d/%s",
            oauthContainer.getHost(), oauthContainer.getMappedPort(ADMIN_PORT), path);
    return URI.create(endpoint);
  }
}
