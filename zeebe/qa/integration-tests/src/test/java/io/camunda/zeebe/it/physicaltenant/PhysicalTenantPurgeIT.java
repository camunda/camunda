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
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code cluster/purge} actuator's {@code physicalTenant} query parameter tears
 * down and re-bootstraps only the named tenant's partitions: after purging one tenant, its
 * processes are gone and it serves commands again on empty state, while another tenant keeps
 * everything it had.
 */
@ZeebeIntegration
final class PhysicalTenantPurgeIT {

  private static final String TENANT_A = "tenanta";
  private static final String PROCESS_ID = "purge-process";

  // both tenants run broker-only (no secondary storage); declaring tenant A starts a second,
  // fully isolated partition group for it
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
  }

  @Test
  void shouldPurgeOnlyTheTargetedPhysicalTenant() {
    // given - the same process deployed to, and runnable in, both physical tenants
    deploy(defaultClient);
    deploy(tenantAClient);
    assertThat(createInstance(defaultClient)).isPositive();
    assertThat(createInstance(tenantAClient)).isPositive();

    // when - purging tenant A only
    ClusterActuator.of(broker).purge(false, TENANT_A);

    // then - tenant A loses its process
    await("tenant A no longer knows its process")
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(() -> assertProcessNotFound(tenantAClient));

    // and - tenant A serves commands again on empty state
    deploy(tenantAClient);
    assertThat(createInstance(tenantAClient)).isPositive();

    // and - the default tenant kept its own process throughout
    assertThat(createInstance(defaultClient)).isPositive();
  }

  @Test
  void shouldPurgeEveryPhysicalTenantWhenNoneIsGiven() {
    // given - the same process deployed to, and runnable in, both physical tenants
    deploy(defaultClient);
    deploy(tenantAClient);
    assertThat(createInstance(defaultClient)).isPositive();
    assertThat(createInstance(tenantAClient)).isPositive();

    // when - purging without naming a physical tenant
    ClusterActuator.of(broker).purge(false);

    // then - every tenant loses its process, keeping the whole-cluster meaning of a purge
    await("no physical tenant knows its process anymore")
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () -> {
              assertProcessNotFound(tenantAClient);
              assertProcessNotFound(defaultClient);
            });
  }

  /**
   * Asserts that the tenant this client targets no longer has the process. NOT_FOUND, rather than
   * any failure, is asserted so that a partition that is merely unavailable mid-purge does not end
   * the wait early.
   */
  private void assertProcessNotFound(final CamundaClient client) {
    assertThatThrownBy(() -> createInstance(client))
        .isInstanceOf(ClientStatusException.class)
        .satisfies(
            t -> assertThat(((ClientStatusException) t).getStatusCode()).isEqualTo(Code.NOT_FOUND));
  }

  /**
   * Deploys the process to the given tenant, retrying until it lands: a tenant's partition group
   * may need a moment to elect a leader, both after startup and after a purge.
   */
  private void deploy(final CamundaClient client) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    await("deployment succeeds")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, PROCESS_ID + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  private long createInstance(final CamundaClient client) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
