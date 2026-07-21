/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.READ;
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
 * End-to-end authorization coverage for {@code POST /v2/secrets/resolve} and {@code POST
 * /v2/secrets/list} against a real authorization-enabled broker. Unlike {@code
 * SecretControllerTest} and {@code SecretServicesTest} (which mock the authorization stack), this
 * exercises the real per-reference {@code SECRET:REVEAL} / {@code SECRET:READ} checks so the
 * security guarantee is proven, not assumed.
 *
 * <p>The secret backend is mocked in Phase 1 (#56567, #56568): references in the service's mock
 * allow-list ({@code camunda.secrets.token} etc.) resolve to a placeholder value or are listed;
 * authorization is real.
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
  // Not in SecretServices#MOCK_RESOLVABLE_REFERENCES, so a wildcard-authorized lookup misses.
  private static final String UNKNOWN_REFERENCE = "camunda.secrets.doesnotexist";

  private static final String REVEAL_USER = "revealUser";
  private static final String WILDCARD_USER = "wildcardUser";
  private static final String NO_PERMISSION_USER = "noPermissionUser";
  private static final String READ_USER = "readUser";
  private static final String WILDCARD_READ_USER = "wildcardReadUser";

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

  @UserDefinition
  private static final TestUser READ_USER_DEF =
      new TestUser(
          READ_USER,
          "password",
          // Granted READ on GRANTED_REFERENCE only — not on OTHER_REFERENCE, and no REVEAL.
          List.of(new Permissions(SECRET, READ, List.of(GRANTED_REFERENCE))));

  @UserDefinition
  private static final TestUser WILDCARD_READ_USER_DEF =
      new TestUser(
          WILDCARD_READ_USER,
          "password",
          // Granted READ on all references via the "*" wildcard.
          List.of(new Permissions(SECRET, READ, List.of("*"))));

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
  void shouldReportNotFoundForAuthorizedUnknownReference(
      @Authenticated(WILDCARD_USER) final CamundaClient client) throws Exception {
    // when a wildcard-authorized user resolves a reference the mock backend does not know
    final var response = resolve(client, WILDCARD_USER, List.of(UNKNOWN_REFERENCE));

    // then it is NOT_FOUND rather than ACCESS_DENIED, exercising the third error code through the
    // full stack (authorization granted, mock lookup misses)
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(body.get("resolved")).isEmpty();
    assertThat(body.get("errors")).hasSize(1);
    assertThat(body.get("errors").get(0).get("reference").asText()).isEqualTo(UNKNOWN_REFERENCE);
    assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo("NOT_FOUND");
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

  @Test
  void shouldListAllReferencesWithWildcardReadGrant(
      @Authenticated(WILDCARD_READ_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:READ:* lists references
    final var response = list(client, WILDCARD_READ_USER);

    // then every reference the mock backend knows is returned, names only
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(referenceNames(body.get("references")))
        .containsExactlyInAnyOrder(GRANTED_REFERENCE, OTHER_REFERENCE, "camunda.secrets.b");
  }

  @Test
  void shouldListOnlyAuthorizedReferenceWithScopedReadGrant(
      @Authenticated(READ_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:READ on GRANTED_REFERENCE only lists references
    final var response = list(client, READ_USER);

    // then only the granted reference is returned, even though the backend knows more
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(referenceNames(body.get("references"))).containsExactly(GRANTED_REFERENCE);
  }

  @Test
  void shouldReturnEmptyListWhenNoReadGrant(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when an authenticated user without any SECRET grant lists references
    final var response = list(client, NO_PERMISSION_USER);

    // then nothing is listed, even though the backend knows references
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(body.get("references")).isEmpty();
  }

  @Test
  void shouldNotAuthorizeListingWithRevealOnlyGrant(
      @Authenticated(REVEAL_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:REVEAL only (no SECRET:READ) lists references
    final var response = list(client, REVEAL_USER);

    // then the listing is empty: REVEAL does not imply READ
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(body.get("references")).isEmpty();
  }

  @Test
  void shouldRejectUnauthenticatedListRequest(
      @Authenticated(WILDCARD_READ_USER) final CamundaClient client) throws Exception {
    // when the list endpoint is called without credentials — the injected client is used only to
    // discover the broker's REST base address; the request below carries no Authorization header
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/secrets/list"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{}"))
            .build();
    final var response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());

    // then it is rejected as unauthorized
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void shouldNotAllowDownstreamCachingOfListedReferences(
      @Authenticated(WILDCARD_READ_USER) final CamundaClient client) throws Exception {
    // Reference names are metadata, not values, but the response must still not be cached by an
    // intermediary proxy or browser, consistent with every other authenticated endpoint. We do not
    // set cache headers on the controller: they are expected from the shared Spring Security filter
    // chain (Cache-Control: no-cache, no-store, max-age=0, must-revalidate).

    // when a listing response is returned
    final var response = list(client, WILDCARD_READ_USER);

    // then it forbids downstream storage
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control"))
        .as("list responses must not be cached downstream")
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

  /**
   * Issues a raw HTTP call to {@code POST /v2/secrets/list} authenticated as the given user. The
   * endpoint has no fluent Java client method yet.
   */
  private static HttpResponse<String> list(final CamundaClient client, final String username)
      throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/secrets/list"))
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuthentication(username))
            .POST(BodyPublishers.ofString("{}"))
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

  private static List<String> referenceNames(final JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
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
