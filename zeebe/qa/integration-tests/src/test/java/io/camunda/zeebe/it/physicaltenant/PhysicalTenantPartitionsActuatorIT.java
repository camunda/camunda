/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the broker-local {@code partitions} actuator's {@code physicalTenant} query
 * parameter scopes both read and write operations to the given physical tenant's partitions,
 * leaving other physical tenants unaffected, while omitting the parameter still targets the default
 * physical tenant (today's behavior).
 */
@ZeebeIntegration
final class PhysicalTenantPartitionsActuatorIT {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private final PartitionsActuator actuator = PartitionsActuator.of(broker);

  @Test
  void shouldPauseProcessingOnTargetedPhysicalTenantOnly() {
    // given - both physical tenants' partitions are processing before any operation
    await("both physical tenants report a processing partition")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(
                      actuator
                          .query(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
                          .get(PARTITION_ID)
                          .streamProcessorPhase())
                  .isEqualTo("PROCESSING");
              assertThat(actuator.query(TENANT_A).get(PARTITION_ID).streamProcessorPhase())
                  .isEqualTo("PROCESSING");
            });

    // when - pausing processing on the default physical tenant only
    actuator.pauseProcessing(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);

    // then - the default tenant's partition is paused...
    await("default tenant partition is paused")
        .untilAsserted(
            () ->
                assertThat(
                        actuator
                            .query(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
                            .get(PARTITION_ID)
                            .streamProcessorPhase())
                    .isEqualTo("PAUSED"));
    // and - tenant A's partition is unaffected
    assertThat(actuator.query(TENANT_A).get(PARTITION_ID).streamProcessorPhase())
        .isEqualTo("PROCESSING");

    // cleanup - resume the default tenant's processing
    actuator.resumeProcessing(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    await("default tenant partition resumes processing")
        .untilAsserted(
            () ->
                assertThat(
                        actuator
                            .query(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
                            .get(PARTITION_ID)
                            .streamProcessorPhase())
                    .isEqualTo("PROCESSING"));
  }

  @Test
  void shouldReturnNodeScopedStatusForEveryPhysicalTenantWithoutParameter() {
    // when - querying without a physicalTenant parameter on a node with more than one physical
    // tenant, per ADR 003 D3 this is Node-scoped: every known physical tenant's status is
    // returned, keyed by physical tenant ID (partition IDs alias across physical tenant groups, so
    // a flat map could not represent all of them)
    await("both physical tenants are reported without a physicalTenant parameter")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var statusByTenant = actuator.queryByTenant();
              assertThat(statusByTenant)
                  .containsOnlyKeys(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TENANT_A);
              assertThat(statusByTenant.get(PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
                  .containsKey(PARTITION_ID);
              assertThat(statusByTenant.get(TENANT_A)).containsKey(PARTITION_ID);
            });
  }
}
