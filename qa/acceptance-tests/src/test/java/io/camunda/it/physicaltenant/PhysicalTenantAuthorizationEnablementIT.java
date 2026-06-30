/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

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
 *
 * <p>RDBMS only — Elasticsearch/OpenSearch variants are skipped because per-PT secondary-storage
 * schema init and the per-PT writer are not yet available, so non-default PTs have no ES/OS indices
 * to authorize against.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenantoff", "tenanton"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "RDBMS_.*",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class PhysicalTenantAuthorizationEnablementIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          // disable authorization checks for tenant-off only (per-PT engine security config).
          // Composes with the extension's per-PT storage/admin stamping because withPtConfig
          // merges into the same per-PT Camunda config via computeIfAbsent. The literal must match
          // TENANT_OFF_ID below (a forward reference to that constant is not allowed here).
          .withPtConfig("tenantoff", c -> c.getSecurity().getAuthorizations().setEnabled(false));

  static MultiPhysicalTenantClients ptClients;

  private static final String TENANT_OFF_ID = "tenantoff";
  private static final String TENANT_ON_ID = "tenanton";
  private static final String RESTRICTED_PASSWORD = "restricted";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

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
