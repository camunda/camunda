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

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that a new physical tenant added to the static configuration after a zone-aware cluster
 * has already bootstrapped is provisioned additively: its partitions are placed per the cluster's
 * zone layout ({@link io.camunda.zeebe.dynamic.config.util.ZoneAwareAdditivePartitionReassigner}),
 * without moving any replica of a physical tenant that already existed.
 *
 * <p>See {@code PhysicalTenantProvisioningIT} for the equivalent single-zone scenario, including
 * coverage of the restart strategy matrix (full/rolling) - this test focuses on the zone-layout
 * dimension alone.
 */
@ZeebeIntegration
final class PhysicalTenantZoneAwareProvisioningIT {

  private static final String ZONE_A = "zone-a";
  private static final String ZONE_B = "zone-b";
  // zone-a: higher priority, 2 replicas per partition. zone-b: lower priority, 1 replica.
  private static final List<Zone> ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 2, 2, 100), new Zone(ZONE_B, 2, 1, 50));
  private static final int BROKERS_COUNT = 4;
  private static final int REPLICATION_FACTOR = 3;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final int TENANT_A_PARTITIONS_COUNT = 2;
  private static final int TENANT_B_PARTITIONS_COUNT = 2;

  // declared at cluster bootstrap time: only the default tenant and tenantA
  private static final PhysicalTenantsITHelper TENANTS_BEFORE =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  // the same tenants, plus tenantB - added to the static configuration only after the zone-aware
  // cluster has already bootstrapped once, then applied on restart
  private static final PhysicalTenantsITHelper TENANTS_AFTER =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .withTenant(TENANT_B, Storage.none(), TENANT_B_PARTITIONS_COUNT)
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(2)
          .multiZone(ZONE_CONFIGS)
          .withBrokerConfig(broker -> TENANTS_BEFORE.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  @Timeout(2 * 60)
  void shouldProvisionNewPhysicalTenantOnZoneAwareClusterAdditively() {
    // given - the zone-aware cluster is up and running with only the default tenant and tenantA,
    // whose partitions already respect the configured zone layout
    cluster.awaitCompleteTopology();
    final var actuator = ClusterActuator.of(cluster.availableGateway());

    try (final var tenantAClient =
        TENANTS_BEFORE.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      await("tenantA's topology is complete before the restart")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, TENANT_A_PARTITIONS_COUNT, REPLICATION_FACTOR));
    }
    assertPartitionsRespectZoneLayout(actuator.getTopology(TENANT_A));
    final Map<Integer, Set<String>> tenantAReplicasBefore =
        replicasByPartition(actuator.getTopology(TENANT_A));

    // when - tenantB is added to every broker's static configuration and the cluster is restarted
    cluster.shutdown();
    cluster.brokers().values().forEach(TENANTS_AFTER::configure);
    cluster.start().awaitCompleteTopology();

    // then - tenantB is provisioned with its partitions placed per the cluster's zone layout, and
    // becomes usable end-to-end
    try (final var tenantBClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_B).build()) {
      await(
              "tenantB's newly-provisioned partitions are visible, have a leader, and follow the "
                  + "cluster's zone layout")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                TopologyAssert.assertThat(tenantBClient.newTopologyRequest().send().join())
                    .isComplete(BROKERS_COUNT, TENANT_B_PARTITIONS_COUNT, REPLICATION_FACTOR);
                assertPartitionsRespectZoneLayout(actuator.getTopology(TENANT_B));
              });

      final String processId = "zone-aware-provisioning-process";
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

      await("deployment to the newly-provisioned tenantB succeeds")
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          tenantBClient
                              .newDeployResourceCommand()
                              .addProcessModel(process, processId + ".bpmn")
                              .send()
                              .join()
                              .getProcesses())
                      .isNotEmpty());

      final long processInstanceKey =
          tenantBClient
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
      assertThat(processInstanceKey).isPositive();
    }

    // and - tenantA's replicas were never touched by tenantB's provisioning: same partitions,
    // same brokers, per partition
    assertThat(replicasByPartition(actuator.getTopology(TENANT_A)))
        .as("tenantA's replicas are unaffected by tenantB's provisioning")
        .isEqualTo(tenantAReplicasBefore);
  }

  /**
   * For every partition of the scoped physical tenant, asserts that the number of brokers hosting a
   * replica in each zone matches that zone's configured {@link Zone#numberOfReplicas()} exactly -
   * the guarantee {@code ZoneAwareAdditivePartitionReassigner} makes for a newly-provisioned
   * tenant, and that an existing tenant already satisfies from its initial bootstrap placement.
   */
  private static void assertPartitionsRespectZoneLayout(final GetTopologyResponse topology) {
    final Map<Integer, Set<String>> replicasByPartition = replicasByPartition(topology);
    replicasByPartition.forEach(
        (partitionId, brokerIds) -> {
          for (final Zone zone : ZONE_CONFIGS) {
            final long replicasInZone =
                brokerIds.stream().filter(brokerId -> isInZone(brokerId, zone.name())).count();
            assertThat(replicasInZone)
                .as(
                    "partition %d should have %d replicas in zone %s",
                    partitionId, zone.numberOfReplicas(), zone.name())
                .isEqualTo(zone.numberOfReplicas());
          }
        });
  }

  private static Map<Integer, Set<String>> replicasByPartition(final GetTopologyResponse topology) {
    return topology.getBrokers().stream()
        .flatMap(
            broker ->
                broker.getPartitions().stream()
                    .map(partition -> Map.entry(partition.getId(), broker.getId())))
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(entry -> entry.getValue().toString(), Collectors.toSet())));
  }

  private static boolean isInZone(final String brokerId, final String zone) {
    return MemberId.from(brokerId).isInZone(zone);
  }
}
