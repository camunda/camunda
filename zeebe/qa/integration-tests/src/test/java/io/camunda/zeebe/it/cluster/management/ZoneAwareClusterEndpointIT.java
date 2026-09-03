/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.it.cluster.clustering.zoneaware.ZoneHelpers;
import io.camunda.zeebe.management.cluster.AddZoneRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

final class ZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  private static final String[] ZONES = {ZONE_A, ZONE_B};

  @Override
  protected int brokerCount() {
    return 3;
  }

  @Override
  protected int partitionCount() {
    return 3;
  }

  @Override
  @SuppressWarnings("resource")
  protected TestCluster createCluster(
      final int brokerCount, final int partitionCount, final int replicationFactor) {
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(brokerCount)
        .withPartitionsCount(partitionCount)
        .withReplicationFactor(replicationFactor)
        .multiZone(zoneConfigs(brokerCount, replicationFactor))
        .build()
        .start();
  }

  @Override
  protected int minReplicationFactor() {
    return 2;
  }

  @Override
  protected String zone() {
    return ZONE_A;
  }

  @Override
  protected BrokerId brokerId(final int nodeIdx) {
    return new BrokerId.String(memberIdForBroker(nodeIdx).toString());
  }

  /** 0 -> zoneA_0 1 -> zoneB_0 2 -> zoneA_1 */
  @Override
  protected MemberId memberIdForBroker(final int nodeIdx) {
    return MemberId.from(zoneFor(nodeIdx), nodeIdx / ZONES.length);
  }

  @Override
  protected void assertClusterScaleResponse(
      final ClusterActuator actuator, final ClusterConfigPatchRequest request) {
    assertThatCode(() -> actuator.patchCluster(request, true, false))
        .isInstanceOf(FeignException.BadRequest.class)
        .hasMessageContaining("zone-aware");
  }

  @Override
  protected void assertClusterPatchResponse(
      final ClusterActuator actuator, final ClusterConfigPatchRequest request) {
    assertThatCode(() -> actuator.patchCluster(request, true, false))
        .isInstanceOf(FeignException.BadRequest.class)
        .hasMessageContaining("zone-aware");
  }

  private String zoneFor(final int nodeIdx) {
    return ZONES[nodeIdx % ZONES.length];
  }

  private static List<Zone> zoneConfigs(final int brokerCount, final int replicationFactor) {
    final var replicasZoneB = replicationFactor / 2;
    final var replicasZoneA = replicationFactor - replicasZoneB;
    final var brokersZoneB = brokerCount / ZONES.length;
    final var brokersZoneA = brokerCount - brokersZoneB;
    return List.of(
        new Zone(ZONE_A, brokersZoneA, replicasZoneA, 100),
        new Zone(ZONE_B, brokersZoneB, replicasZoneB, 10));
  }

  @Test
  void shouldRejectBareIntegersWhenScaling() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      assertThatCode(() -> actuator.scaleBrokers(List.of(0, 1, 2)))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("Members without a zone cannot be added to a zone-aware cluster");
    }
  }

  @Test
  void shouldRejectAddBrokerWithBareInteger() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- bare integer broker ID is rejected on zone-aware clusters
      assertThatCode(() -> actuator.addBroker(2))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("Members without a zone cannot be added to a zone-aware cluster");
    }
  }

  @Test
  void shouldRejectPartitionJoinOnZoneAwareCluster() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- partition join is rejected on zone-aware clusters
      assertThatCode(() -> actuator.joinPartition(0, 1, 1))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("is not an active member");
    }
  }

  @Test
  void shouldRejectPartitionLeaveOnZoneAwareCluster() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- partition leave is rejected on zone-aware clusters
      assertThatCode(() -> actuator.leavePartition(0, 1))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("local member does not exist");
    }
  }

  @Test
  void shouldUpdatePartitionDistribution() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - increase zoneA replicas from 1→2 (RF 2→3)
      final var config =
          new PartitionDistributionConfig()
              .type(PartitionDistributionConfig.TypeEnum.ZONE_AWARE)
              .zones(
                  List.of(
                      new ZoneSpec().name(ZONE_A).numberOfReplicas(2).priority(100),
                      new ZoneSpec().name(ZONE_B).numberOfReplicas(1).priority(10)));
      final var response = actuator.patchPartitionDistribution(config, false);

      // then - exact planned operations.
      // Before (RF=2): zoneA_0 holds P1,P3 and zoneA_1 holds P2 (1 replica/zone, priority 2+1).
      // After  (RF=3): each partition needs both zoneA brokers; existing zoneA replica gets
      // promoted to priority 3, new zoneA replica joins at priority 2. zoneB unchanged.
      // nodeIdx mapping: 0=zoneA_0, 1=zoneB_0, 2=zoneA_1
      assertThat(response.getPlannedChanges())
          .isEqualTo(
              List.of(
                  new Operation()
                      .operation(OperationEnum.UPDATE_PARTITION_DISTRIBUTOR_CONFIG)
                      // Coordinator
                      .brokerId(brokerId(0))
                      .partitionDistributionConfig(config),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(2))
                      .partitionId(1)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_PROMOTE)
                      .brokerId(brokerId(2))
                      .partitionId(1)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(1)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(0))
                      .partitionId(2)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_PROMOTE)
                      .brokerId(brokerId(0))
                      .partitionId(2)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(2))
                      .partitionId(2)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(2))
                      .partitionId(3)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_PROMOTE)
                      .brokerId(brokerId(2))
                      .partitionId(3)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(3)
                      .physicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                      .priority(3)));

      Awaitility.await()
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));

      final var topology = actuator.getTopology();
      assertThat(topology.getPartitionDistribution()).isEqualTo(config);
    }
  }

  @Test
  void shouldRejectPartitionDistributionWithoutZones() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      final var config =
          new PartitionDistributionConfig().type(TypeEnum.ZONE_AWARE).zones(List.of());
      assertThatCode(() -> actuator.patchPartitionDistribution(config, false))
          .isInstanceOf(FeignException.BadRequest.class);
    }
  }

  @Test
  void shouldRejectRoundRobinConfigOnZoneAwareCluster() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      final var config =
          new PartitionDistributionConfig().type(PartitionDistributionConfig.TypeEnum.ROUND_ROBIN);
      assertThatCode(() -> actuator.patchPartitionDistribution(config, false))
          .isInstanceOf(FeignException.BadRequest.class);
    }
  }

  @Test
  void shouldRejectClusterPatchWithBareIntegers() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - attempt patch with bare integer IDs on zone-aware cluster
      final var request =
          new ClusterConfigPatchRequest()
              .brokers(
                  new ClusterConfigPatchRequestBrokers()
                      .add(List.of(BrokerId.of(0), BrokerId.of(1))));
      assertThatCode(() -> actuator.patchCluster(request, false, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("Members without a zone cannot be added to a zone-aware cluster");
    }
  }

  @Test
  void shouldRecoverZoneAwareClusterAfterForceRemoveZone() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given - zoneA (brokers 0 and 2) is down; use the zoneB broker as the actuator, since it is
      // the only one that stays alive throughout the test
      cluster.awaitCompleteTopology();
      // odd id means zoneB
      final var actuator = ClusterActuator.of(cluster.brokers().get(memberIdForBroker(1)));
      cluster.brokers().get(memberIdForBroker(0)).close();
      cluster.brokers().get(memberIdForBroker(2)).close();

      // when - force-remove zoneA: force-evict its brokers and drop it from the distribution config
      final var forceRemoveResponse = actuator.forceRemoveZone(ZONE_A, false);
      Awaitility.await()
          .ignoreException(FeignException.class)
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(actuator)
                      .hasAppliedChanges(forceRemoveResponse));
      ClusterActuatorAssert.assertThat(actuator).doesNotHaveBroker(brokerId(0));
      ClusterActuatorAssert.assertThat(actuator).doesNotHaveBroker(brokerId(2));

      // when - start a fresh broker in zoneA and add the zone back with it
      final var newZoneABroker =
          ZoneHelpers.startBrokerInZone(cluster, ZONE_A, 0, 3, cluster.getMultiZoneConfig());
      try {
        final var addZoneRequest =
            new AddZoneRequest().numberOfReplicas(1).priority(100).brokers(List.of(brokerId(0)));
        final var addZoneResponse = actuator.addZone(ZONE_A, addZoneRequest, false);

        // then - the cluster recovers: zoneA is back in the distribution and hosts partitions.
        // wait more than 10s to allow for partition join to be retried
        Awaitility.await()
            .atMost(Duration.ofMinutes(1))
            .untilAsserted(
                () ->
                    ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(addZoneResponse));
        ClusterActuatorAssert.assertThat(actuator).hasActiveBroker(brokerId(0).toString());
        final var expectedDistribution =
            new PartitionDistributionConfig()
                .type(TypeEnum.ZONE_AWARE)
                .zones(
                    List.of(
                        new ZoneSpec().name(ZONE_B).numberOfReplicas(1).priority(10),
                        new ZoneSpec().name(ZONE_A).numberOfReplicas(1).priority(100)));
        assertThat(actuator.getTopology().getPartitionDistribution())
            .isEqualTo(expectedDistribution);
      } finally {
        newZoneABroker.close();
      }
    }
  }

  @Test
  void shouldSwapZonePrioritiesAndMoveLeaders() {
    try (final var cluster = createCluster(brokerCount())) {
      // given - a fully zone-aware cluster where zoneA (priority 100) is the preferred leader zone
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      Awaitility.await()
          .untilAsserted(
              () -> {
                for (int partitionId = 1; partitionId <= partitionCount(); partitionId++) {
                  assertThat(leaderIsInZone(cluster, ZONES[0], partitionId)).isTrue();
                }
              });

      // when - swap the order so zoneB becomes preferred
      final var response = actuator.updateZonePriorities(List.of(ZONES[1], ZONES[0]), false);

      // then - the change is accepted and plans operations
      Awaitility.await()
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));

      // and - the distribution config now lists zoneB first with the higher priority
      final var expectedDistribution =
          new PartitionDistributionConfig()
              .type(TypeEnum.ZONE_AWARE)
              .zones(
                  List.of(
                      new ZoneSpec().name(ZONE_B).numberOfReplicas(1).priority(100),
                      new ZoneSpec().name(ZONE_A).numberOfReplicas(2).priority(10)));
      assertThat(actuator.getTopology().getPartitionDistribution()).isEqualTo(expectedDistribution);

      // and - after a rebalance forces the now-lower-priority zoneA leaders to step down,
      // partition leaders move to zoneB. A priority change alone does not displace a healthy
      // sitting leader; the rebalance issues stepDownIfNotPrimary so the highest-priority
      // (zoneB) node wins re-election. Rebalancing is best-effort, so re-trigger it each poll
      // until every partition leader has moved.
      final var rebalanceActuator = RebalanceActuator.of(cluster.availableGateway());
      Awaitility.await()
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () -> {
                rebalanceActuator.rebalance();
                for (int partitionId = 1; partitionId <= partitionCount(); partitionId++) {
                  assertThat(leaderIsInZone(cluster, ZONE_B, partitionId)).isTrue();
                }
              });
    }
  }

  /**
   * Returns whether any broker in the given zone is the leader for the given partition, by querying
   * each zone broker's partitions actuator directly.
   */
  private boolean leaderIsInZone(
      final TestCluster cluster, final String zone, final int partitionId) {
    for (int nodeIdx = 0; nodeIdx < brokerCount(); nodeIdx++) {
      if (!zoneFor(nodeIdx).equals(zone)) {
        continue;
      }
      final var broker = cluster.brokers().get(memberIdForBroker(nodeIdx));
      final var status = PartitionsActuator.of(broker).query().get(partitionId);
      if (status != null && "Leader".equalsIgnoreCase(status.role())) {
        return true;
      }
    }
    return false;
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class ZoneManagementRejections {
    @AutoClose private final TestCluster cluster = createCluster(minReplicationFactor());

    @AutoClose
    private final TestCluster clusterAfterZoneRemoval = createCluster(minReplicationFactor());

    private ClusterActuator actuator;
    private ClusterActuator actuatorAfterZoneRemoval;

    @BeforeAll
    void awaitCompleteTopology() {
      cluster.awaitCompleteTopology();
      actuator = ClusterActuator.of(cluster.availableGateway());

      clusterAfterZoneRemoval.awaitCompleteTopology();
      actuatorAfterZoneRemoval =
          ClusterActuator.of(clusterAfterZoneRemoval.brokers().get(memberIdForBroker(1)));
      clusterAfterZoneRemoval.brokers().get(memberIdForBroker(0)).close();
      clusterAfterZoneRemoval.brokers().get(memberIdForBroker(2)).close();
      final var forceRemoveResponse = actuatorAfterZoneRemoval.forceRemoveZone(ZONE_A, false);
      Awaitility.await()
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(actuatorAfterZoneRemoval)
                      .hasAppliedChanges(forceRemoveResponse));
    }

    @Test
    void shouldRejectForceRemoveOfUnknownZone() {
      // given - the shared cluster is running with both zones present

      // when - then
      assertThatCode(() -> actuator.forceRemoveZone("zoneUnknown", false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("unknown zone");
    }

    @Test
    void shouldRejectForceRemoveOfLastRemainingZone() {
      // given - zoneA has already been force-removed from the shared cluster

      // when - then - force-removing the last remaining zone is rejected
      assertThatCode(() -> actuatorAfterZoneRemoval.forceRemoveZone(ZONE_B, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("last remaining zone");
    }

    @Test
    void shouldRejectAddZoneOfExistingZone() {
      // given - zoneA is still present in the shared cluster's distribution config

      // when - then
      final var addZoneRequest =
          new AddZoneRequest().numberOfReplicas(1).priority(100).brokers(List.of(brokerId(0)));
      assertThatCode(() -> actuator.addZone(ZONE_A, addZoneRequest, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("already present");
    }

    @Test
    void shouldRejectAddZoneWithMismatchedBrokers() {
      // given - zoneA has already been force-removed from the shared cluster

      // when - then - a request naming no brokers at all is rejected
      final var emptyBrokersRequest =
          new AddZoneRequest().numberOfReplicas(1).priority(100).brokers(List.of());
      assertThatCode(() -> actuatorAfterZoneRemoval.addZone(ZONE_A, emptyBrokersRequest, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("Exactly one of brokers and numberOfBrokers must be set.");

      // when - then - fewer brokers than numberOfReplicas is rejected
      final var tooFewBrokersRequest =
          new AddZoneRequest().numberOfReplicas(2).priority(100).brokers(List.of(brokerId(0)));
      assertThatCode(() -> actuatorAfterZoneRemoval.addZone(ZONE_A, tooFewBrokersRequest, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("less than the requested number of replicas");
    }
  }
}
