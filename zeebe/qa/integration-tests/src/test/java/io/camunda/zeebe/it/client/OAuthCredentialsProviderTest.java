/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.CredentialsProvider.CredentialsApplier;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
  private static final GenericContainer<?> OAUTH_CONTAINER =
      new GenericContainer<>(DockerImageName.parse("oryd/hydra:v1.11"))
          .withEnv("DSN", "memory")
          .withEnv("STRATEGIES_ACCESS_TOKEN", "jwt")
          .withExposedPorts(ADMIN_PORT, PUBLIC_PORT)
          .waitingFor(new HostPortWaitStrategy())
          .withCommand("serve all --dangerous-force-http")
          .withNetwork(NETWORK);

  private final TestCredentialsApplier applier = new TestCredentialsApplier();
  private final Identity identity =
      new Identity(
          new IdentityConfiguration(
              getAuthUrl(""), getAuthUrl(""), "zeebe", "zeebe", "zeebe", Type.AUTH0.name(), false));

  @BeforeAll
  static void beforeAll() throws IOException, InterruptedException {
    ensureOAuthClientExists();
  }

  @AfterAll
  static void afterAll() {
    NETWORK.close();
  }

  @Test
  void shouldFetchValidOAuthToken(@TempDir final Path cacheDir) throws IOException {
    // given
    final var credentialsProvider =
        CredentialsProvider.newCredentialsProviderBuilder()
            .audience("zeebe")
            .clientId("zeebe")
            .clientSecret("secret")
            .credentialsCachePath(cacheDir.resolve("cache").toString())
            .authorizationServerUrl(getTokenEndpoint())
            .build();

    // when
    credentialsProvider.applyCredentials(applier);

    // then
    assertThat(applier.header()).isEqualTo("Authorization");
    assertThat(applier.tokenType()).isEqualTo("Bearer");
    identity.authentication().verifyToken(applier.token());
  }

  private static void ensureOAuthClientExists() throws IOException, InterruptedException {
    final var httpClient = HttpClient.newHttpClient();
    final var zeebeOauthClient =
        """
        {
          "client_id": "zeebe", "client_secret": "secret", "client_name": "zeebe",
          "grant_types": ["client_credentials"], "audience": ["zeebe"], "response_types": ["code"],
          "token_endpoint_auth_method": "client_secret_post", "access_token_strategy": "jwt"
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
    return getAuthUrl("oauth2/token");
  }

  private String getAuthUrl(final String path) {
    return String.format(
        "http://%s:%d/%s",
        OAUTH_CONTAINER.getHost(), OAUTH_CONTAINER.getMappedPort(PUBLIC_PORT), path);
  }

  private static URI getAdminEndpoint(final String path) {
    final var endpoint =
        String.format(
            "http://%s:%d/%s",
            OAUTH_CONTAINER.getHost(), OAUTH_CONTAINER.getMappedPort(ADMIN_PORT), path);
    return URI.create(endpoint);
  }

  private static final class TestCredentialsApplier implements CredentialsApplier {
    private String key;
    private String value;

    @Override
    public void put(final String key, final String value) {
      this.key = key;
      this.value = value;
    }

    private String token() {
      return value.replaceFirst("^Bearer ", "");
    }

    private String tokenType() {
      return value.split(" ")[0];
    }

    private String header() {
      return key;
    }
  }
}
