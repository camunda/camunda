/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * IT-6 — per-physical-tenant authorization enablement, converted to the {@link MultiDbTest} +
 * {@link MultiDbPhysicalTenants} framework.
 *
 * <p>AC: a PT with {@code authorizations.enabled=false} allows an operation without any grant,
 * while a distinct authorization-enabled PT denies the same ungranted operation (403). This proves
 * authorization enablement is configured per physical tenant. Note: multi-tenancy enablement is
 * cluster-wide, not per-PT — this AC is specifically about per-PT <em>authorization</em>
 * enablement.
 *
 * <p>The per-PT authorization override is applied by the test on its own
 * {@code @MultiDbTestApplication} broker via {@code BROKER.withPtConfig(TENANT_OFF, ...)}, which
 * merges into the same per-PT config the framework stamps storage and the admin user into.
 */
@MultiDbTest
@MultiDbPhysicalTenants({
  PhysicalTenantAuthorizationEnablementIT.TENANT_OFF_ID,
  PhysicalTenantAuthorizationEnablementIT.TENANT_ON_ID
})
final class PhysicalTenantAuthorizationEnablementIT {

  // Declared before BROKER so the per-PT override below can reference TENANT_OFF_ID by name rather
  // than repeating the literal (these are compile-time constants, so the annotation can use them
  // too).
  static final String TENANT_OFF_ID = "tenantoff";
  static final String TENANT_ON_ID = "tenanton";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          // disable authorization checks for tenant-off only (per-PT engine security config).
          // Composes with the extension's per-PT storage/admin stamping because withPtConfig
          // merges into the same per-PT Camunda config via computeIfAbsent.
          .withPtConfig(TENANT_OFF_ID, c -> c.getSecurity().getAuthorizations().setEnabled(false));

  static MultiPhysicalTenantClients ptClients;

  private static final String RESTRICTED_PASSWORD = "restricted";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper JSON = new ObjectMapper();

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldEnforceAuthorizationPerTenantEnablement() {
    final CamundaClient tenantOffAdmin = ptClients.admin(TENANT_OFF_ID);
    final CamundaClient tenantOnAdmin = ptClients.admin(TENANT_ON_ID);

    // given — an ungranted restricted user in each PT
    final String userOff = createRestrictedUser(tenantOffAdmin, "off");
    final String userOn = createRestrictedUser(tenantOnAdmin, "on");

    // then — via the authz-DISABLED PT, the ungranted user can deploy (no grant required)
    try (final CamundaClient offClient = restrictedClient(TENANT_OFF_ID, userOff)) {
      Awaitility.await("ungranted user can deploy via the authz-disabled PT")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(deploy(offClient).getProcesses())
                      .as("authz-disabled PT allows ungranted operations")
                      .isNotEmpty());
    }

    // and — via the authz-ENABLED PT, the same ungranted operation is denied (403)
    try (final CamundaClient onClient = restrictedClient(TENANT_ON_ID, userOn)) {
      assertThatThrownBy(() -> deploy(onClient))
          .as("authz-enabled PT denies ungranted operations")
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("status: 403")
          .hasMessageContaining("FORBIDDEN");
    }
  }

  @Test
  void shouldReportTheTenantAuthorizationsFlagForOwnAuthorizations() throws Exception {
    // given — authorizations are on at the root and on tenant-on, and off on tenant-off
    final String userOff = createRestrictedUser(ptClients.admin(TENANT_OFF_ID), "off");
    final String userOn = createRestrictedUser(ptClients.admin(TENANT_ON_ID), "on");

    // when / then — each tenant reports its own flag, not the root one
    assertThat(ownAuthorizationsEnabled(TENANT_OFF_ID, userOff)).isFalse();
    assertThat(ownAuthorizationsEnabled(TENANT_ON_ID, userOn)).isTrue();
  }

  private boolean ownAuthorizationsEnabled(final String tenantId, final String username)
      throws Exception {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final String credentials =
        Base64.getEncoder()
            .encodeToString(
                (username + ":" + RESTRICTED_PASSWORD).getBytes(StandardCharsets.UTF_8));
    final var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    base
                        + "/physical-tenants/"
                        + tenantId
                        + "/v2/authentication/me/authorizations/search"))
            .header("Authorization", "Basic " + credentials)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(HttpURLConnection.HTTP_OK);
    return JSON.readTree(response.body()).path("authorizationsEnabled").asBoolean();
  }

  private static String createRestrictedUser(final CamundaClient admin, final String prefix) {
    final String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    admin
        .newCreateUserCommand()
        .username(username)
        .password(RESTRICTED_PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
    Awaitility.await("restricted user '" + username + "' exists")
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

  private CamundaClient restrictedClient(final String tenantId, final String username) {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final URI restAddress = URI.create(base + "/physical-tenants/" + tenantId);
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

  private static DeploymentEvent deploy(final CamundaClient client) {
    final String processId = Strings.newRandomValidBpmnId();
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn")
        .send()
        .join();
  }
}
