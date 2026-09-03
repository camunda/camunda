/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Pins how the assembled application treats a percent-encoded spelling of a configured physical
 * tenant, end to end: through CSL's scoped security chains, real credential validation, and the
 * authentication converter that resolves memberships.
 *
 * <p>Two path parsers meet on this route. CSL selects a scoped chain with {@code
 * http.securityMatcher(String...)}, which matches the parsed, decoded path, while {@code
 * PhysicalTenantFilter} derives the tenant id the application acts on from the request path. If the
 * filter reads the encoded form, {@code /physical-tenants/tenant%61/...} is routed as the
 * configured tenant {@code tenanta} but acted on as {@code tenant%61} — an id no configuration
 * matches, and one a caller varies freely without changing how the request is routed.
 *
 * <p>The catch-all that rejects unconfigured tenants with 404 does not cover this: it too matches
 * on the decoded path, where this request is a valid tenant's. What stops it is one layer further
 * in — per-physical-tenant credential resolution, which finds no tenant under the encoded id and
 * answers 401. Authentication therefore fails before anything downstream acts on the mismatched id.
 *
 * <p>That ordering is what this test pins. It is load-bearing and not self-evident from either
 * layer alone: the security chains admit the request, and the filter hands on an id no
 * configuration matches, so only the credential step keeps the two from meeting.
 */
@MultiDbTest
@MultiDbPhysicalTenants(PhysicalTenantEncodedPathRoutingIT.TENANT_ID)
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class PhysicalTenantEncodedPathRoutingIT {

  static final String TENANT_ID = "tenanta";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  static MultiPhysicalTenantClients ptClients;

  /** {@code tenanta} with its last character written as {@code %61}. */
  private static final String ENCODED_TENANT_ID = "tenant%61";

  private static final String ME_ENDPOINT = "/v2/authentication/me";
  private static final String PASSWORD = "encoded-path-probe";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  @Test
  void shouldNotServeAPercentEncodedTenantPath() throws Exception {
    // given — a user that exists in the configured tenant, so the request gets past authentication
    // and reaches membership resolution rather than stopping at a 401
    final var username = createUser();

    // when — the same endpoint and the same credentials, spelled two ways
    final var plain = get("/physical-tenants/" + TENANT_ID + ME_ENDPOINT, username);
    final var encoded = get("/physical-tenants/" + ENCODED_TENANT_ID + ME_ENDPOINT, username);

    // then — the plain spelling is the baseline: authenticated and served
    assertThat(plain.statusCode())
        .as("a configured tenant's own path must authenticate and serve")
        .isEqualTo(200);

    // and — the encoded spelling is refused. It reached the tenant's chain, since the catch-all
    // would have answered 404, but the credentials could not be resolved under an id no tenant
    // matches. A 200 would mean it was served as some tenant; a 5xx would mean the mismatched id
    // travelled past authentication and failed somewhere downstream on an unresolvable store.
    assertThat(encoded.statusCode())
        .as("an encoded spelling of a configured tenant must not be served")
        .isEqualTo(401);
  }

  private String createUser() {
    final CamundaClient admin = ptClients.admin(TENANT_ID);
    final var username = "probe-" + UUID.randomUUID().toString().substring(0, 8);
    admin
        .newCreateUserCommand()
        .username(username)
        .password(PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
    Awaitility.await("user '" + username + "' exists")
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
    return username;
  }

  private static HttpResponse<String> get(final String rawPath, final String username)
      throws Exception {
    final var base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final var credentials =
        Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes());
    // A raw java.net.http request: a CamundaClient builds the prefix itself and would normalize the
    // encoding away, which is the very thing under test.
    try (final var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create(base + rawPath))
              .header("Authorization", "Basic " + credentials)
              .GET()
              .build(),
          BodyHandlers.ofString());
    }
  }
}
