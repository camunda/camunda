/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Regression coverage for camunda#58640: {@code SecretServices} consulted a single,
 * default-physical-tenant-pinned {@code AuthorizationChecker} for every physical tenant's
 * secret-reveal requests, so a {@code SECRET:REVEAL} grant that existed only in *another* physical
 * tenant's own authorization storage incorrectly authorized reveals via a tenant whose own
 * authorization data held no such grant. Sibling of camunda#58441 ({@code DocumentServices}); see
 * {@link io.camunda.it.client.MultiPhysicalTenantDocumentAuthorizationIT} for the equivalent
 * documents-side coverage.
 *
 * <p>Unlike the document endpoints, {@code POST /v2/secrets/resolve} has no fluent {@code
 * CamundaClient} command yet, and a denied reference is reported as an {@code ACCESS_DENIED} entry
 * in the response body on HTTP 200 -- never a thrown exception -- so this issues raw HTTP calls
 * (mirroring {@code io.camunda.it.auth.SecretAuthorizationIT}) and polls the parsed JSON body
 * instead of an exception, unlike the document IT's {@code awaitForbidden}.
 *
 * <p>RDBMS only, matching the multi-physical-tenant QA harness's current capability (see {@link
 * io.camunda.it.auth.MultiPhysicalTenantAuthorizationIT} for the same restriction).
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class MultiPhysicalTenantSecretAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  static MultiPhysicalTenantClients ptClients;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String RESTRICTED_PASSWORD = "restricted";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  // Resolvable by the mock backend (see SecretServices#MOCK_RESOLVABLE_REFERENCES).
  private static final String GRANTED_REFERENCE = "camunda.secrets.token";

  private static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void shouldDenySecretRevealWhenGrantExistsOnlyInAnotherPhysicalTenant() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final String username = createUserWithForeignTenantGrant("secret-reveal");

    try (final CamundaClient restrictedInA = restrictedClient(TENANT_A, username)) {
      awaitAccessDenied(
          "tenantb's own SECRET:REVEAL grant for this identity must not authorize revealing a"
              + " secret via tenanta -- only tenanta's own authorization data may",
          GRANTED_REFERENCE,
          () -> resolve(restrictedInA, username, List.of(GRANTED_REFERENCE)));

      // positive control -- granting REVEAL in tenanta's own storage flips the same request to
      // allowed, proving the prior denial wasn't vacuous.
      grant(tenantAAdmin, username);
      awaitResolved(
          "granting REVEAL in tenanta itself authorizes the reveal",
          GRANTED_REFERENCE,
          "mock-value-for-token-in-tenant-" + TENANT_A,
          () -> resolve(restrictedInA, username, List.of(GRANTED_REFERENCE)));
    }
  }

  private static void awaitAccessDenied(
      final String reason, final String expectedReference, final HttpCall operation) {
    Awaitility.await(reason)
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () -> {
              final var response = operation.call();
              assertThat(response.statusCode()).isEqualTo(200);
              final var body = JSON.readTree(response.body());
              assertThat(body.get("resolved")).isEmpty();
              assertThat(body.get("errors")).hasSize(1);
              assertThat(body.get("errors").get(0).get("reference").asText())
                  .isEqualTo(expectedReference);
              assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo("ACCESS_DENIED");
            });
  }

  private static void awaitResolved(
      final String reason,
      final String expectedReference,
      final String expectedValue,
      final HttpCall operation) {
    Awaitility.await(reason)
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response = operation.call();
              assertThat(response.statusCode()).isEqualTo(200);
              final var body = JSON.readTree(response.body());
              assertThat(body.get("errors")).isEmpty();
              assertThat(body.get("resolved")).hasSize(1);
              assertThat(body.get("resolved").get(0).get("reference").asText())
                  .isEqualTo(expectedReference);
              assertThat(body.get("resolved").get(0).get("value").asText())
                  .isEqualTo(expectedValue);
            });
  }

  @FunctionalInterface
  private interface HttpCall {
    HttpResponse<String> call() throws Exception;
  }

  /**
   * Issues a raw HTTP call to {@code POST /v2/secrets/resolve} authenticated as the given user. The
   * endpoint has no fluent Java client method yet (see {@code SecretAuthorizationIT}).
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

  private static URI createUri(final CamundaClient client, final String path) {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return URI.create(base + separator + path);
  }

  private static String basicAuthentication(final String username) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString(
                (username + ":" + RESTRICTED_PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  // --- helpers -------------------------------------------------------------------------

  private String createUserWithForeignTenantGrant(final String usernamePrefix) {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);
    final String username = usernamePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(tenantAAdmin, username);
    createRestrictedUserNamed(tenantBAdmin, username);
    grant(tenantBAdmin, username);
    return username;
  }

  private CamundaClient restrictedClient(final String tenantId, final String username) {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final java.net.URI restAddress = java.net.URI.create(base + "/physical-tenants/" + tenantId);
    return BROKER
        .newClientBuilder()
        .physicalTenantId(tenantId)
        .preferRestOverGrpc(true)
        // the REST address already carries the /physical-tenants/<id> prefix, so opt out of the
        // client's auto-prefixing to avoid a doubled path
        .prefixPhysicalTenantPath(false)
        .restAddress(restAddress)
        .grpcAddress(BROKER.grpcAddress())
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password(RESTRICTED_PASSWORD)
                .build())
        .build();
  }

  private static void createRestrictedUserNamed(final CamundaClient admin, final String username) {
    admin
        .newCreateUserCommand()
        .username(username)
        .password(RESTRICTED_PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
    Awaitility.await("restricted user '" + username + "' exists in its PT")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        admin
                            .newUsersSearchRequest()
                            .filter(f -> f.username(username))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }

  private static void grant(final CamundaClient admin, final String username) {
    admin
        .newCreateAuthorizationCommand()
        .ownerId(username)
        .ownerType(OwnerType.USER)
        .resourceId("*")
        .resourceType(ResourceType.SECRET)
        .permissionTypes(PermissionType.REVEAL)
        .send()
        .join();
  }
}
