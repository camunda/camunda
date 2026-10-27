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

import feign.FeignException;
import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.management.cluster.RoutingState;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code PATCH /actuator/cluster/routing-state} can be scoped to a single physical
 * tenant via the {@code physicalTenant} query parameter (issue #59994), mirroring what #59825 did
 * for the read side ({@link PhysicalTenantClusterEndpointIT}).
 *
 * <p>Unlike that class, these tests write, so a fresh broker is used per test rather than sharing
 * one static instance across the class — a write in one test must not leak into another.
 *
 * <p>The default tenant and {@link #TENANT_A} are deliberately given different static partition
 * counts. This is not just cosmetic: it lets {@link
 * #shouldPickUpTheTargetTenantsOwnEngineStateOnABodylessWrite} tell whether a body-less write
 * fetched <em>tenant A's own</em> engine routing state or silently fell back to the default
 * tenant's. Before the fix, {@code BrokerClientPartitionScalingExecutor} sent every scaling request
 * (including the {@code GetScaleUpProgress} a body-less write uses to read the engine state)
 * without a partition group, so it always resolved against the default tenant regardless of which
 * tenant's executor sent it. With equal partition counts, that bug would have gone unnoticed here:
 * both tenants would report the same {@code AllPartitions} routing state whether or not the fetch
 * was scoped correctly.
 */
@ZeebeIntegration
final class PhysicalTenantRoutingStateIT {

  private static final String TENANT_A = "tenanta";
  private static final int TENANT_A_PARTITIONS_COUNT = 2;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe(purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withPtConfig(
                  TENANT_A,
                  camunda -> camunda.getCluster().setPartitionCount(TENANT_A_PARTITIONS_COUNT)));

  private final ClusterActuator actuator = ClusterActuator.of(broker);

  @Test
  void shouldPickUpTheTargetTenantsOwnEngineStateOnABodylessWrite() {
    // when — a body-less write scoped to tenant A must fetch tenant A's own engine routing state,
    // not the default tenant's; this is the case that exercises the executor fix directly, so it
    // is written and run before the other cases here
    await("tenant A accepts a body-less routing-state write")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> actuator.patchRoutingState(false, TENANT_A));

    // then — the resulting routing state reflects tenant A's own partition count, not the default
    // tenant's (which has a different count precisely so the two cannot be confused)
    await("tenant A's routing state reflects its own partition count")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var routing = actuator.getTopology(TENANT_A).getRouting();
              assertThat(routing).isNotNull();
              assertThat(routing.getRequestHandling())
                  .asInstanceOf(InstanceOfAssertFactories.type(RequestHandlingAllPartitions.class))
                  .extracting(RequestHandlingAllPartitions::getPartitionCount)
                  .isEqualTo(TENANT_A_PARTITIONS_COUNT);
            });
  }

  @Test
  void shouldScopeWriteToOnlyTheRequestedPhysicalTenant() {
    // given — the default tenant's routing state before tenant A is touched
    final var defaultBefore = awaitRoutingState(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    // a partition count that neither tenant already has, so the write is observable regardless of
    // which value each tenant started with
    final var tenantARoutingState = routingState(5);

    // when
    await("tenant A accepts the scoped routing-state write")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> actuator.patchRoutingState(tenantARoutingState, false, TENANT_A));

    // then — tenant A's routing state changed to the requested value (the applier assigns its own
    // version on write, so only requestHandling/messageCorrelation are compared here), and the
    // default tenant's is left exactly as it was, version included
    await("tenant A's routing state reflects the write")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var routing = actuator.getTopology(TENANT_A).getRouting();
              assertThat(routing.getRequestHandling())
                  .isEqualTo(tenantARoutingState.getRequestHandling());
              assertThat(routing.getMessageCorrelation())
                  .isEqualTo(tenantARoutingState.getMessageCorrelation());
            });
    assertThat(actuator.getTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID).getRouting())
        .isEqualTo(defaultBefore);
  }

  @Test
  void shouldApplyUnscopedWriteToTheDefaultPhysicalTenant() {
    // given — tenant A's routing state before the default tenant is touched
    final var tenantABefore = awaitRoutingState(TENANT_A);
    final var defaultRoutingState = routingState(5);

    // when — no physicalTenant parameter is given
    await("the default tenant accepts the unscoped routing-state write")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> actuator.patchRoutingState(defaultRoutingState, false));

    // then — the default tenant's routing state changed; tenant A's is untouched, version included
    await("the default tenant's routing state reflects the write")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var routing =
                  actuator.getTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID).getRouting();
              assertThat(routing.getRequestHandling())
                  .isEqualTo(defaultRoutingState.getRequestHandling());
              assertThat(routing.getMessageCorrelation())
                  .isEqualTo(defaultRoutingState.getMessageCorrelation());
            });
    assertThat(actuator.getTopology(TENANT_A).getRouting()).isEqualTo(tenantABefore);
  }

  @Test
  void shouldReturn404ForUnknownPhysicalTenant() {
    // when - then
    assertThatThrownBy(() -> actuator.patchRoutingState(routingState(1), false, "does-not-exist"))
        .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
        .extracting(FeignException::status)
        .isEqualTo(404);
  }

  private RoutingState awaitRoutingState(final String physicalTenantId) {
    final var routing = new RoutingState[1];
    await("physical tenant '%s' has a routing state".formatted(physicalTenantId))
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var current = actuator.getTopology(physicalTenantId).getRouting();
              assertThat(current).isNotNull();
              routing[0] = current;
            });
    return routing[0];
  }

  /**
   * A routing state body to PATCH with. The version is irrelevant: {@code
   * UpdateRoutingStateApplier} always assigns its own version on write ({@code previousVersion +
   * 1}), ignoring whatever the request body carries.
   */
  private RoutingState routingState(final int partitionCount) {
    return new RoutingState()
        .requestHandling(new RequestHandlingAllPartitions(partitionCount).strategy("AllPartitions"))
        .messageCorrelation(new MessageCorrelationHashMod("HashMod", partitionCount))
        .version(0L);
  }
}
