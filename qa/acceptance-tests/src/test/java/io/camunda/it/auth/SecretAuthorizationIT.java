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
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * End-to-end authorization coverage for {@code POST /v2/secrets/resolve} and {@code POST
 * /v2/secrets/list} against a real authorization-enabled broker. Unlike {@code
 * SecretControllerTest} and {@code SecretServicesTest} (which mock the authorization stack), this
 * exercises the real per-reference {@code SECRET:REVEAL} / {@code SECRET:READ} checks so the
 * security guarantee is proven, not assumed.
 *
 * <p>The broker runs with a real file-based secret store (#58497), so a resolved reference carries
 * the value that is actually on disk and the listing is the store's own enumeration. The gateway
 * path answers without a broker round-trip, so no resolved value can reach state or exported
 * records; the response body is the only place a value appears, which the tests below pin down.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class SecretAuthorizationIT {

  private static final String TOKEN_VALUE = "token-file-value";
  private static final String OTHER_VALUE = "a-file-value";

  // Written before the broker starts, since the broker's store is configured to read this
  // directory. Declared above BROKER so its initializer runs first.
  private static final Path SECRETS_DIRECTORY = createSecretsDirectory();

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUnifiedConfig(
              config -> {
                final var store = new FileStore();
                store.setPath(SECRETS_DIRECTORY.toString());
                config.getSecrets().getStores().getFile().put("it", store);
              });

  private static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  // Held by the store (one file per secret, the file name being the bare secret name).
  private static final String GRANTED_REFERENCE = "camunda.secrets.token";
  private static final String OTHER_REFERENCE = "camunda.secrets.a";
  // No file of that name exists, so a wildcard-authorized lookup misses.
  private static final String UNKNOWN_REFERENCE = "camunda.secrets.doesnotexist";

  // Under a physical tenant, secret authorization is resolved against root storage, so grants
  // created for these test users are not visible in the tenant context and every authorized call
  // is denied (same class of limitation as https://github.com/camunda/camunda/issues/58393). Tests
  // that assert a granted or wildcard caller is authorized cannot pass there yet and are disabled
  // in that mode; the unauthenticated and no-grant tests remain tenant-agnostic and still run.
  private static final String PHYSICAL_TENANT_PROPERTY = "test.integration.camunda.physical-tenant";
  private static final String PHYSICAL_TENANT_DISABLED_REASON =
      "Secret authorization is resolved against root storage under a physical tenant, so grants for "
          + "the test users are not visible in the tenant context; see "
          + "https://github.com/camunda/camunda/issues/58393";

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

  /**
   * Creates the store's directory and writes one file per secret. {@code tls.crt} is deliberately
   * among them: the file store accepts that name but it cannot form a {@code
   * camunda.secrets.<name>} reference, so the listing has to leave it out.
   */
  private static Path createSecretsDirectory() {
    try {
      final var directory = Files.createTempDirectory("secret-authorization-it-");
      Files.writeString(directory.resolve("token"), TOKEN_VALUE, StandardCharsets.UTF_8);
      Files.writeString(directory.resolve("a"), OTHER_VALUE, StandardCharsets.UTF_8);
      Files.writeString(directory.resolve("b"), "b-file-value", StandardCharsets.UTF_8);
      Files.writeString(directory.resolve("tls.crt"), "certificate", StandardCharsets.UTF_8);
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create the secrets directory", e);
    }
  }

  @AfterAll
  static void deleteSecretsDirectory() throws IOException {
    try (final var entries = Files.walk(SECRETS_DIRECTORY)) {
      entries.sorted(Comparator.reverseOrder()).forEach(SecretAuthorizationIT::delete);
    }
  }

  private static void delete(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to delete " + path, e);
    }
  }

  @Test
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
  void shouldResolveReferenceWhenAuthorizedOnThatReference(
      @Authenticated(REVEAL_USER) final CamundaClient client) throws Exception {
    // when a user with SECRET:REVEAL on the reference resolves it
    final var response = resolve(client, REVEAL_USER, List.of(GRANTED_REFERENCE));

    // then it succeeds with the value the store holds on disk, and no errors
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(references(body.get("resolved"))).containsExactly(GRANTED_REFERENCE);
    assertThat(body.get("resolved").get(0).get("value").asText()).isEqualTo(TOKEN_VALUE);
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
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
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
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
  void shouldResolveAnyReferenceWithWildcardGrant(
      @Authenticated(WILDCARD_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:REVEAL:* resolves a reference it was not explicitly granted
    final var response = resolve(client, WILDCARD_USER, List.of(OTHER_REFERENCE));

    // then the wildcard grant authorizes it and it resolves to that secret's own stored value
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(references(body.get("resolved"))).containsExactly(OTHER_REFERENCE);
    assertThat(body.get("resolved").get(0).get("value").asText()).isEqualTo(OTHER_VALUE);
    assertThat(body.get("errors")).isEmpty();
  }

  @Test
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
  void shouldReportNotFoundForAuthorizedUnknownReference(
      @Authenticated(WILDCARD_USER) final CamundaClient client) throws Exception {
    // when a wildcard-authorized user resolves a reference the store does not hold
    final var response = resolve(client, WILDCARD_USER, List.of(UNKNOWN_REFERENCE));

    // then it is NOT_FOUND rather than ACCESS_DENIED, exercising the third error code through the
    // full stack (authorization granted, store lookup misses)
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
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
  void shouldListAllReferencesWithWildcardReadGrant(
      @Authenticated(WILDCARD_READ_USER) final CamundaClient client) throws Exception {
    // when a user granted SECRET:READ:* lists references
    final var response = list(client, WILDCARD_READ_USER);

    // then every secret the store holds is returned as a reference, names only. The store's
    // tls.crt is left out: it cannot form a valid reference, so it would only offer the caller
    // something resolve() and any BPMN expression reject.
    assertThat(response.statusCode()).isEqualTo(200);
    final var body = read(response.body());
    assertThat(referenceNames(body.get("references")))
        .containsExactlyInAnyOrder(GRANTED_REFERENCE, OTHER_REFERENCE, "camunda.secrets.b");
  }

  @Test
  @DisabledIfSystemProperty(
      named = PHYSICAL_TENANT_PROPERTY,
      matches = ".+",
      disabledReason = PHYSICAL_TENANT_DISABLED_REASON)
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
