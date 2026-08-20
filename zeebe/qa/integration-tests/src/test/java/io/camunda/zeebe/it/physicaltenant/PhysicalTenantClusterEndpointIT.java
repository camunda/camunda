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
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import feign.FeignException;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PhysicalTenantInfo;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code GET /actuator/cluster} reports per-physical-tenant state (issue #59825) and
 * can be scoped to a single physical tenant via the {@code physicalTenant} query parameter.
 *
 * <p>Assertions are made directly on {@link
 * io.camunda.zeebe.management.cluster.GetTopologyResponse} with plain AssertJ rather than through
 * the shared {@code ClusterActuatorAssert}: its {@code brokerHasPartition}/{@code
 * brokerDoesNotHavePartition} helpers match on partition id alone, which is unsafe here — partition
 * ids restart at 1 per physical tenant, so a broker legitimately reports "partition 1" once per
 * tenant it belongs to, and those helpers cannot tell the two apart.
 *
 * <p>All four tests are read-only, so a single two-physical-tenant broker is shared across them
 * (see {@link PhysicalTenantsITHelper}'s static-broker pattern) instead of booting a fresh one per
 * test.
 */
@ZeebeIntegration
final class PhysicalTenantClusterEndpointIT {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configureStatic(new TestStandaloneBroker().withUnauthenticatedAccess());

  private final ClusterActuator actuator = ClusterActuator.of(BROKER);

  @BeforeAll
  static void setUp() {
    TENANTS.refreshSecondaryStorage(BROKER);
  }

  @Test
  void shouldReportBothPhysicalTenantsWhenUnscoped() {
    // when - then: querying without a physicalTenant parameter on a broker serving more than one
    // physical tenant reports every known physical tenant's own state; the legacy single-tenant
    // fields are absent because none of them could describe more than one tenant at once, and that
    // includes each broker's own version.
    await("both physical tenants are reported without a physicalTenant parameter")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = actuator.getTopology();

              assertThat(topology.getPhysicalTenants())
                  .extracting(PhysicalTenantInfo::getId)
                  .containsExactlyInAnyOrder(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TENANT_A);
              assertThat(topology.getVersion()).isNull();
              assertThat(topology.getLastChange()).isNull();
              assertThat(topology.getPendingChange()).isNull();
              assertThat(topology.getRouting()).isNull();

              // an absent scalar decodes to null, unlike an absent array, so this does observe the
              // omitted per-broker version on the wire and not a client-side default.
              assertThat(topology.getBrokers())
                  .allSatisfy(broker -> assertThat(broker.getVersion()).isNull())
                  .allSatisfy(broker -> assertThat(broker.getLastUpdatedAt()).isNotNull());

              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerState::getPartitions)
                  .extracting(PartitionState::getId, PartitionState::getPhysicalTenant)
                  .contains(
                      tuple(PARTITION_ID, PhysicalTenantsITHelper.DEFAULT_TENANT_ID),
                      tuple(PARTITION_ID, TENANT_A));
            });
  }

  @Test
  void shouldScopeToDefaultPhysicalTenant() {
    // when - then: scoping to the default tenant reports only its partitions, the legacy
    // single-tenant fields are populated from that tenant alone, and physicalTenants lists it.
    await("default tenant is reported when scoped")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = actuator.getTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);

              assertThat(topology.getPhysicalTenants())
                  .extracting(PhysicalTenantInfo::getId)
                  .containsExactly(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
              assertThat(topology.getVersion()).isNotNull();
              assertThat(topology.getRouting()).isNotNull();
              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerState::getPartitions)
                  .extracting(PartitionState::getPhysicalTenant)
                  .containsOnly(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
            });
  }

  @Test
  void shouldScopeToTenantA() {
    // when - then: scoping to tenant-a reports only its partitions, leaving the default tenant's
    // partitions out, and both the legacy single-tenant fields and physicalTenants describe
    // tenant-a alone.
    await("tenant-a is reported when scoped")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = actuator.getTopology(TENANT_A);

              assertThat(topology.getPhysicalTenants())
                  .extracting(PhysicalTenantInfo::getId)
                  .containsExactly(TENANT_A);
              assertThat(topology.getVersion()).isNotNull();
              assertThat(topology.getRouting()).isNotNull();
              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerState::getPartitions)
                  .extracting(PartitionState::getPhysicalTenant)
                  .containsOnly(TENANT_A);
            });
  }

  @Test
  void shouldReturn404ForUnknownPhysicalTenant() {
    // when - then
    assertThatThrownBy(() -> actuator.getTopology("does-not-exist"))
        .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
        .extracting(FeignException::status)
        .isEqualTo(404);
  }
}
