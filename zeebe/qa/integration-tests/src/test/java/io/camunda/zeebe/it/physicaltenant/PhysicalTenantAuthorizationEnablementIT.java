/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * IT-6 — per-physical-tenant authorization enablement.
 *
 * <p>AC: a PT with {@code authorizations.enabled=false} allows an operation without any grant,
 * while a distinct authorization-enabled PT denies the same ungranted operation (403). This proves
 * authorization enablement is configured per physical tenant (via #55792's per-PT engine security
 * config). Note: multi-tenancy enablement is cluster-wide, not per-PT — this AC is specifically
 * about per-PT <em>authorization</em> enablement.
 *
 * <p>Two PTs are enough: {@code tenant-off} (authz disabled) and {@code tenant-on} (authz enabled),
 * plus the mandatory {@code default} PT.
 *
 * <p>RDBMS-H2 only — Elasticsearch/OpenSearch variants are skipped because per-PT secondary-storage
 * schema init (#51996) and the per-PT writer (#51736) are not yet available, so non-default PTs
 * have no ES/OS indices to authorize against.
 */
@ZeebeIntegration
final class PhysicalTenantAuthorizationEnablementIT {

  private static final String TENANT_OFF = "tenantoff";
  private static final String TENANT_ON = "tenanton";
  private static final String ADMIN_PASSWORD = "ptadmin";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant(TENANT_OFF, Storage.rdbmsH2(TENANT_OFF))
          .withTenant(TENANT_ON, Storage.rdbmsH2(TENANT_ON))
          .build();

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER = configure();

  private static CamundaClient tenantOffAdmin;
  private static CamundaClient tenantOnAdmin;

  private static TestStandaloneBroker configure() {
    final TestStandaloneBroker broker =
        TENANTS.configure(
            new TestStandaloneBroker()
                .withAuthorizationsEnabled()
                .withAuthenticationMethod(AuthenticationMethod.BASIC));
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_OFF, ADMIN_PASSWORD);
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_ON, ADMIN_PASSWORD);
    // disable authorization checks for tenant-off only (per-PT engine security config)
    broker.withPtConfig(TENANT_OFF, c -> c.getSecurity().getAuthorizations().setEnabled(false));
    return broker;
  }

  @BeforeAll
  static void startBroker() {
    BROKER.start().awaitCompleteTopology();
    tenantOffAdmin =
        TENANTS.newBasicAuthAdminClientBuilder(BROKER, TENANT_OFF, ADMIN_PASSWORD).build();
    tenantOnAdmin =
        TENANTS.newBasicAuthAdminClientBuilder(BROKER, TENANT_ON, ADMIN_PASSWORD).build();
  }

  @AfterAll
  static void closeClients() {
    if (tenantOffAdmin != null) {
      tenantOffAdmin.close();
    }
    if (tenantOnAdmin != null) {
      tenantOnAdmin.close();
    }
  }

  @Test
  void shouldEnforceAuthorizationPerTenantEnablement() {
    // given — an ungranted restricted user in each PT
    final String userOff = createRestrictedUser(tenantOffAdmin, "off");
    final String userOn = createRestrictedUser(tenantOnAdmin, "on");

    // then — via the authz-DISABLED PT, the ungranted user can deploy (no grant required)
    try (final CamundaClient offClient = restrictedClient(TENANT_OFF, userOff)) {
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
    try (final CamundaClient onClient = restrictedClient(TENANT_ON, userOn)) {
      assertThatThrownBy(() -> deploy(onClient))
          .as("authz-enabled PT denies ungranted operations")
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("status: 403")
          .hasMessageContaining("FORBIDDEN");
    }
  }

  private static String createRestrictedUser(final CamundaClient admin, final String prefix) {
    final String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    // immediately after startup the per-PT admin user may not yet be initialized in its RDBMS
    // schema, so the admin's own request can transiently 401 — wait until it can authenticate
    Awaitility.await("per-PT admin can authenticate")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(admin.newUsersSearchRequest().send().join().items()).isNotNull());
    admin
        .newCreateUserCommand()
        .username(username)
        .password("restricted")
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

  private static CamundaClient restrictedClient(final String tenantId, final String username) {
    return TENANTS
        .newBasicAuthAdminClientBuilder(BROKER, tenantId, ADMIN_PASSWORD)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password("restricted")
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
