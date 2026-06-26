/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * IT-3b — logical-tenant scoping within physical tenants.
 *
 * <p>AC: with multi-tenancy enabled cluster-wide, a logical tenant {@code T} (and the data assigned
 * to it) is created only inside physical tenant tenantb; a tenant-scoped read for {@code T} returns
 * that data via the tenantb path and excludes it via the tenanta path. This shows logical tenants
 * are themselves contained within a physical tenant's own schema — a logical tenant id created in
 * one PT does not resolve in another.
 *
 * <p>Multi-tenancy is a cluster-wide toggle (not per-PT); the per-PT containment comes from each
 * PT's independent schema.
 *
 * <p>RDBMS-H2 only — Elasticsearch/OpenSearch variants are skipped because per-PT secondary-storage
 * schema init (#51996) and the per-PT writer (#51736) are not yet available, so non-default PTs
 * have no ES/OS indices to read from.
 */
@ZeebeIntegration
final class PhysicalTenantLogicalTenantScopingIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String ADMIN_PASSWORD = "ptadmin";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant(TENANT_A, Storage.rdbmsH2(TENANT_A))
          .withTenant(TENANT_B, Storage.rdbmsH2(TENANT_B))
          .build();

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER = configure();

  private static CamundaClient tenantAAdmin;
  private static CamundaClient tenantBAdmin;

  private static TestStandaloneBroker configure() {
    final TestStandaloneBroker broker =
        TENANTS.configure(
            new TestStandaloneBroker()
                .withAuthorizationsEnabled()
                .withAuthenticationMethod(AuthenticationMethod.BASIC)
                .withMultiTenancyEnabled());
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_A, ADMIN_PASSWORD);
    TENANTS.seedBasicAuthAdminUser(broker, TENANT_B, ADMIN_PASSWORD);
    return broker;
  }

  @BeforeAll
  static void startBroker() {
    BROKER.start();
    tenantAAdmin = TENANTS.newBasicAuthAdminClientBuilder(BROKER, TENANT_A, ADMIN_PASSWORD).build();
    tenantBAdmin = TENANTS.newBasicAuthAdminClientBuilder(BROKER, TENANT_B, ADMIN_PASSWORD).build();
    // wait for per-PT admin users to become ready (avoids transient post-startup 401s)
    for (final CamundaClient admin : new CamundaClient[] {tenantAAdmin, tenantBAdmin}) {
      Awaitility.await("per-PT admin can authenticate")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () -> assertThat(admin.newUsersSearchRequest().send().join().items()).isNotNull());
    }
  }

  @AfterAll
  static void closeClients() {
    if (tenantAAdmin != null) {
      tenantAAdmin.close();
    }
    if (tenantBAdmin != null) {
      tenantBAdmin.close();
    }
  }

  @Test
  void shouldScopeLogicalTenantDataToItsPhysicalTenant() {
    // given — a logical tenant T created only in tenantb, with the tenantb admin assigned to it
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String logicalTenant = "logical-" + suffix;
    final String processId = "scoped-" + suffix;
    tenantBAdmin.newCreateTenantCommand().tenantId(logicalTenant).name(logicalTenant).send().join();
    tenantBAdmin
        .newAssignUserToTenantCommand()
        .username(TENANTS.adminUsername(TENANT_B))
        .tenantId(logicalTenant)
        .send()
        .join();

    // and — a process deployed under logical tenant T via the tenantb path
    Awaitility.await("process deployed under logical tenant T via tenantb")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        tenantBAdmin
                            .newDeployResourceCommand()
                            .addProcessModel(
                                Bpmn.createExecutableProcess(processId)
                                    .startEvent()
                                    .endEvent()
                                    .done(),
                                processId + ".bpmn")
                            .tenantId(logicalTenant)
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    // then — a tenant-scoped read for T returns the data via the tenantb path
    Awaitility.await("logical-tenant-scoped read returns the data via tenantb")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var ids =
                  tenantBAdmin
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.tenantId(logicalTenant))
                      .send()
                      .join()
                      .items()
                      .stream()
                      .map(p -> p.getProcessDefinitionId())
                      .toList();
              assertThat(ids)
                  .as("logical tenant T's data is visible via its own physical tenant (tenantb)")
                  .contains(processId);
            });

    // and — the same logical-tenant-scoped read via the tenanta path excludes the data:
    // the logical tenant id T was created only in tenantb's schema, so it does not resolve here
    // assert the exclusion holds continuously over a short window, so a late-arriving leak fails
    Awaitility.await("logical tenant T's data stays absent via tenanta")
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () -> {
              final var idsViaA =
                  tenantAAdmin
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.tenantId(logicalTenant))
                      .send()
                      .join()
                      .items()
                      .stream()
                      .map(p -> p.getProcessDefinitionId())
                      .toList();
              assertThat(idsViaA)
                  .as("logical tenant T's data must NOT be visible via a different PT (tenanta)")
                  .doesNotContain(processId);
            });
  }
}
