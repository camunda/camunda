/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.REVEAL;
import static io.camunda.client.api.search.enums.ResourceType.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * End-to-end authorization coverage for {@code POST /v2/secrets/resolve} against a real
 * authorization-enabled broker. Unlike {@code SecretControllerTest} and {@code SecretServicesTest}
 * (which mock the authorization stack), this exercises the real per-reference {@code SECRET:REVEAL}
 * check so the security guarantee is proven, not assumed.
 *
 * <p>The secret backend is mocked in Phase 1 (#56567): references in the service's mock allow-list
 * ({@code camunda.secrets.token} etc.) resolve to a placeholder value; authorization is real.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class SecretAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  // Resolvable by the mock backend (see SecretServices#MOCK_RESOLVABLE_REFERENCES).
  private static final String GRANTED_REFERENCE = "camunda.secrets.token";
  private static final String OTHER_REFERENCE = "camunda.secrets.a";

  private static final String REVEAL_USER = "revealUser";
  private static final String WILDCARD_USER = "wildcardUser";
  private static final String NO_PERMISSION_USER = "noPermissionUser";

  @UserDefinition
  private static final TestUser REVEAL_USER_DEF =
      new TestUser(
          REVEAL_USER,
          "password",
          // Granted REVEAL on GRANTED_REFERENCE only — not on OTHER_REFERENCE.
          List.of(new Permissions(SECRET, REVEAL, List.of(GRANTED_REFERENCE))));

  @UserDefinition
  private static final TestUser WILDCARD_USER_DEF =
      new TestUser(
          WILDCARD_USER,
          "password",
          // Granted REVEAL on all references via the "*" wildcard.
          List.of(new Permissions(SECRET, REVEAL, List.of("*"))));

  @UserDefinition
  private static final TestUser NO_PERMISSION_USER_DEF =
      new TestUser(NO_PERMISSION_USER, "password", List.of());

  @Test
  void shouldResolveReferenceWhenAuthorizedOnThatReference(
      @Authenticated(REVEAL_USER) final CamundaClient client) throws Exception {
    // when a user with SECRET:REVEAL on the reference resolves it
    final var response = resolve(client, REVEAL_USER, List.of(GRANTED_REFERENCE));

    // then it succeeds with the (mocked) value and no errors
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(references(body.get("resolved"))).containsExactly(GRANTED_REFERENCE);
    assertThat(body.get("errors")).isEmpty();
  }

  @Test
  void shouldDenyReferenceWhenNotAuthorizedAtAll(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when an authenticated user without any SECRET grant resolves a reference
    final var response = resolve(client, NO_PERMISSION_USER, List.of(GRANTED_REFERENCE));

    // then the endpoint still returns 200 but the reference is ACCESS_DENIED, with no value leaked
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(body.get("resolved")).isEmpty();
    assertThat(body.get("errors")).hasSize(1);
    assertThat(body.get("errors").get(0).get("reference").asText()).isEqualTo(GRANTED_REFERENCE);
    assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  void shouldEnforceAuthorizationPerReferenceResourceId(
      @Authenticated(REVEAL_USER) final CamundaClient client) throws Exception {
    // when a user granted only on GRANTED_REFERENCE resolves a batch of both references
    final var response = resolve(client, REVEAL_USER, List.of(GRANTED_REFERENCE, OTHER_REFERENCE));

    // then only the granted reference resolves; the other is denied independently
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(references(body.get("resolved"))).containsExactly(GRANTED_REFERENCE);
    assertThat(body.get("errors")).hasSize(1);
    assertThat(body.get("errors").get(0).get("reference").asText()).isEqualTo(OTHER_REFERENCE);
    assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  void shouldResolveAnyReferenceWithWildcardGrant(
      @Authenticated(WILDCARD_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:REVEAL:* resolves a reference it was not explicitly granted
    final var response = resolve(client, WILDCARD_USER, List.of(OTHER_REFERENCE));

    // then the wildcard grant authorizes it and it resolves with no errors
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(references(body.get("resolved"))).containsExactly(OTHER_REFERENCE);
    assertThat(body.get("errors")).isEmpty();
  }

  @Test
  void shouldRejectUnauthenticatedRequest(@Authenticated(REVEAL_USER) final CamundaClient client)
      throws Exception {
    // when the resolve endpoint is called without credentials — the injected client is used only to
    // discover the broker's REST base address; the request below carries no Authorization header
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/secrets/resolve"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"references\": [\"" + GRANTED_REFERENCE + "\"]}"))
            .build();
    final var response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());

    // then it is rejected as unauthorized
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void shouldNotAllowDownstreamCachingOfResolvedSecrets(
      @Authenticated(REVEAL_USER) final CamundaClient client) throws Exception {
    // The response body carries secret values, so it must never be cached by an intermediary proxy
    // or browser. We do not set cache headers on the controller: they are expected from the shared
    // Spring Security filter chain (Cache-Control: no-cache, no-store, max-age=0, must-revalidate).

    // when a secret-bearing response is returned
    final var response = resolve(client, REVEAL_USER, List.of(GRANTED_REFERENCE));

    // then it forbids downstream storage
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control"))
        .as("resolve responses carry secret values and must not be cached downstream")
        .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
  }

  /**
   * Issues a raw HTTP call to {@code POST /v2/secrets/resolve} authenticated as the given user. The
   * endpoint has no fluent Java client method yet.
   */
  private static HttpResponse<String> resolve(
      final CamundaClient client, final String username, final List<String> references)
      throws Exception {
    final var body = JSON.writeValueAsString(Map.of("references", references));
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/secrets/resolve"))
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuthentication(username))
            .POST(BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

  private static JsonNode read(final String body) throws Exception {
    return JSON.readTree(body);
  }

  private static List<String> references(final JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false)
        .map(item -> item.get("reference").asText())
        .toList();
  }

  private static String basicAuthentication(final String username) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":password").getBytes(StandardCharsets.UTF_8));
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return new URI(base + separator + path);
  }
}
