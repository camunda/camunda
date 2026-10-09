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

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code PATCH /actuator/cluster} scopes a partition-count scale-up to a single
 * physical tenant via the {@code physicalTenant} query parameter (issue #60077), mirroring what
 * #59994 did for {@code PATCH /actuator/cluster/routing-state} ({@link
 * PhysicalTenantRoutingStateIT}).
 *
 * <p>Unlike the read-only {@link PhysicalTenantClusterEndpointIT}, these tests write, so a fresh
 * broker is used per test rather than sharing one static instance across the class.
 *
 * <p>The default tenant and {@link #TENANT_A} are deliberately given different static partition
 * counts. A wrong-group write — e.g. the request accidentally scaling the default group instead of
 * the named one — would otherwise be invisible: if both tenants started at the same partition
 * count, scaling the wrong one would still leave every observable count looking correct.
 */
@ZeebeIntegration
final class PhysicalTenantPartitionScalingIT {

  private static final String TENANT_A = "tenanta";
  private static final int DEFAULT_TENANT_PARTITIONS_COUNT = 1;
  private static final int TENANT_A_PARTITIONS_COUNT = 2;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
              Storage.none(),
              DEFAULT_TENANT_PARTITIONS_COUNT)
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  @TestZeebe(purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              // command redistribution retries back off exponentially up to 5 minutes by default;
              // a scaled-up partition receives pre-existing deployments through exactly that
              // mechanism, so without a short, flat backoff the hand-off assertion below would
              // need to out-wait the worst-case retry gap
              .withProcessingConfig(
                  processing -> {
                    final var distribution = processing.getEngine().getDistribution();
                    distribution.setRedistributionInterval(Duration.ofSeconds(1));
                    distribution.setMaxBackoffDuration(Duration.ofSeconds(1));
                  }));

  private final ClusterActuator actuator = ClusterActuator.of(broker);

  @Test
  void shouldScaleUpOnlyTheTargetedPhysicalTenant() {
    // given — the default tenant's partition count before tenant A is scaled
    TENANTS.awaitPartitionCount(
        actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID, DEFAULT_TENANT_PARTITIONS_COUNT);
    TENANTS.awaitPartitionCount(actuator, TENANT_A, TENANT_A_PARTITIONS_COUNT);

    // when — scale only tenant A up by one partition
    final var targetPartitionCount = TENANT_A_PARTITIONS_COUNT + 1;
    TENANTS.awaitAccepted(
        "tenant A accepts a scale-up scoped to it",
        () -> scalePartitions(targetPartitionCount, TENANT_A));

    // then — tenant A's partition count increased and its new partition really started for it;
    // the default tenant's is untouched
    TENANTS.awaitPartitionCount(actuator, TENANT_A, targetPartitionCount);
    awaitTopologyPartitionCount(TENANT_A, targetPartitionCount);
    assertThat(TENANTS.partitionCount(actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
        .isEqualTo(DEFAULT_TENANT_PARTITIONS_COUNT);
    assertTopologyPartitionCount(
        PhysicalTenantsITHelper.DEFAULT_TENANT_ID, DEFAULT_TENANT_PARTITIONS_COUNT);
  }

  @Test
  void shouldScaleUpOnlyTheDefaultPhysicalTenantWhenUnscoped() {
    // given
    TENANTS.awaitPartitionCount(
        actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID, DEFAULT_TENANT_PARTITIONS_COUNT);
    TENANTS.awaitPartitionCount(actuator, TENANT_A, TENANT_A_PARTITIONS_COUNT);

    // when — no physicalTenant parameter is given
    final var targetPartitionCount = DEFAULT_TENANT_PARTITIONS_COUNT + 1;
    TENANTS.awaitAccepted(
        "the default physical tenant accepts the unscoped scale-up",
        () -> scalePartitions(targetPartitionCount));

    // then — the default tenant's partition count increased and its new partition really started
    // for it; tenant A's is untouched
    TENANTS.awaitPartitionCount(
        actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID, targetPartitionCount);
    awaitTopologyPartitionCount(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, targetPartitionCount);
    assertThat(TENANTS.partitionCount(actuator, TENANT_A)).isEqualTo(TENANT_A_PARTITIONS_COUNT);
    assertTopologyPartitionCount(TENANT_A, TENANT_A_PARTITIONS_COUNT);
  }

  /**
   * Verifies that a partition added by a scale-up serves its own physical tenant's state, not the
   * default tenant's. A new partition receives existing deployments through bootstrap and
   * asynchronous redistribution, both of which are driven by broker requests (snapshot chunks,
   * scale-up and redistribution progress reads) that before #60103 carried no partition group and
   * were silently answered by the default tenant. A process deployed only to tenant A before the
   * scale-up is instantiable on the new partition only if that hand-off really happened within
   * tenant A — the decoy deployment on the default tenant gives a wrong-tenant hand-off concrete
   * state to leak.
   */
  @Test
  void shouldServeTheScaledUpPartitionFromItsOwnPhysicalTenant() {
    // given — a process deployed to tenant A, and a decoy deployed to the default tenant, before
    // tenant A is scaled up
    final var tenantAProcessId = "scale-up-bootstrap-tenanta";
    deploy(TENANT_A, tenantAProcessId);
    deploy(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, "scale-up-bootstrap-default");

    // when — tenant A is scaled up by one partition
    final var targetPartitionCount = TENANT_A_PARTITIONS_COUNT + 1;
    TENANTS.awaitAccepted(
        "tenant A accepts a scale-up scoped to it",
        () -> scalePartitions(targetPartitionCount, TENANT_A));
    TENANTS.awaitPartitionCount(actuator, TENANT_A, targetPartitionCount);
    awaitTopologyPartitionCount(TENANT_A, targetPartitionCount);

    // then — an instance of tenant A's process can be created on the new partition; round-robin
    // request routing cycles through all of tenant A's partitions, so retrying until an instance
    // key decodes to the new partition proves that partition knows the deployment. NOT_FOUND
    // rejections are retried: the new partition only learns existing deployments through
    // asynchronous redistribution after its bootstrap
    try (final var client = TENANTS.newClientBuilder(broker, TENANT_A).build()) {
      await("tenant A's new partition %d runs its own processes".formatted(targetPartitionCount))
          .atMost(Duration.ofMinutes(3))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final long processInstanceKey =
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(tenantAProcessId)
                        .latestVersion()
                        .send()
                        .join()
                        .getProcessInstanceKey();
                assertThat(Protocol.decodePartitionId(processInstanceKey))
                    .isEqualTo(targetPartitionCount);
              });
    }
  }

  private void deploy(final String physicalTenantId, final String processId) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(processId))
            .endEvent()
            .done();
    try (final var client = TENANTS.newClientBuilder(broker, physicalTenantId).build()) {
      TENANTS.deploy(client, process, processId);
    }
  }

  private void scalePartitions(final int targetPartitionCount, final String physicalTenant) {
    actuator.patchCluster(
        TENANTS.toPartitionCount(targetPartitionCount), false, false, physicalTenant);
  }

  private void scalePartitions(final int targetPartitionCount) {
    actuator.patchCluster(TENANTS.toPartitionCount(targetPartitionCount), false, false);
  }

  /**
   * Asserts against the client-facing, physical-tenant-scoped topology that {@code
   * physicalTenantId} really runs {@code expectedCount} partitions, each with a leader, as a client
   * sees it, rather than only the cluster configuration's routing state that {@link
   * PhysicalTenantsITHelper#partitionCount} reads.
   */
  private void awaitTopologyPartitionCount(final String physicalTenantId, final int expectedCount) {
    try (final var client = TENANTS.newClientBuilder(broker, physicalTenantId).build()) {
      await("physical tenant '%s' runs %d partitions".formatted(physicalTenantId, expectedCount))
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(1, expectedCount, 1));
    }
  }

  /**
   * The same check as {@link #awaitTopologyPartitionCount}, without waiting for it to become true.
   */
  private void assertTopologyPartitionCount(
      final String physicalTenantId, final int expectedCount) {
    try (final var client = TENANTS.newClientBuilder(broker, physicalTenantId).build()) {
      TopologyAssert.assertThat(client.newTopologyRequest().send().join())
          .isComplete(1, expectedCount, 1);
    }
  }
}
