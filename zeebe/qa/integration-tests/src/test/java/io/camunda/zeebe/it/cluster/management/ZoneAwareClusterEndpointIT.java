/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static io.camunda.zeebe.it.cluster.clustering.zoneaware.ZoneHelpers.addBrokerInZone;
import static io.camunda.zeebe.qa.util.cluster.TestClusterBuilder.DEFAULT_CLUSTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class ZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  private static final String[] ZONES = {"zoneA", "zoneB"};

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
    return ZONES[0];
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
        new Zone(ZONES[0], brokersZoneA, replicasZoneA, 100),
        new Zone(ZONES[1], brokersZoneB, replicasZoneB, 10));
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
          .hasMessageContaining("is not active");
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
                      new ZoneSpec().name(ZONES[0]).numberOfReplicas(2).priority(100),
                      new ZoneSpec().name(ZONES[1]).numberOfReplicas(1).priority(10)));
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
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(1)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(0))
                      .partitionId(2)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(2))
                      .partitionId(2)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(2))
                      .partitionId(3)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(3)
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
  @Timeout(2 * 60)
  @SuppressWarnings("resource")
  void shouldRecoverZoneAwareClusterAfterZoneFailover() {
    // given -- zoneA x2, zoneB x2, RF=4 (2 replicas/zone), 2 partitions
    try (final var cluster = createCluster(4, 2, 4)) {
      cluster.awaitCompleteTopology();
      // Pin the actuator to a surviving zoneB broker -- availableGateway() may resolve to a
      // zoneA broker, which is closed below and would take the actuator connection down with it.
      final var actuator = ClusterActuator.of(cluster.brokers().get(MemberId.from(ZONES[1], 0)));

      // when
      // 1. stop both zoneA brokers (incl. coordinator zoneA_0)
      final var brokersToRemove = List.of(MemberId.from("zoneA", 0), MemberId.from("zoneA", 1));
      brokersToRemove.forEach(b -> cluster.brokers().get(b).close());

      // 2. force-remove both zoneA brokers; remaining member set = zoneB only
      final var removed =
          actuator.patchCluster(
              new ClusterConfigPatchRequest()
                  .brokers(
                      new ClusterConfigPatchRequestBrokers()
                          .remove(brokersToRemove.stream().map(BrokerId::of).toList())),
              false,
              true);

      assertChangeDone(actuator, removed);

      final var zoneBOnlyResponse =
          actuator.patchPartitionDistribution(
              new PartitionDistributionConfig()
                  .type(PartitionDistributionConfig.TypeEnum.ZONE_AWARE)
                  .zones(List.of(new ZoneSpec().name(ZONES[1]).numberOfReplicas(2).priority(10))),
              false);
      assertChangeDone(actuator, zoneBOnlyResponse);

      // 3. add 2 new brokers to zoneB (zoneB_2, zoneB_3)
      final var targetZones = List.of(new Zone(ZONES[1], 4, 4, 10));
      final var brokersToAdd = brokersInZone(ZONES[1], 2, 3);
      final var zoneBBrokers = new HashMap<MemberId, TestStandaloneBroker>();
      cluster.brokers().entrySet().stream()
          .filter(e -> e.getKey().isInZone(ZONES[1]))
          .forEach(e -> zoneBBrokers.put(e.getKey(), e.getValue()));
      brokersToAdd.forEach(
          id -> {
            final var broker =
                closeables.manage(
                    addBrokerInZone(
                        cluster,
                        actuator,
                        DEFAULT_CLUSTER_NAME,
                        id.zone(),
                        id.nodeIdx(),
                        4,
                        targetZones));
            zoneBBrokers.put(id, broker);
          });

      // 4. reconfigure partition distribution: RF=4 all in zoneB, no zoneA
      final var config =
          new PartitionDistributionConfig()
              .type(PartitionDistributionConfig.TypeEnum.ZONE_AWARE)
              .zones(List.of(new ZoneSpec().name(ZONES[1]).numberOfReplicas(4).priority(10)));
      final var response = actuator.patchPartitionDistribution(config, false);

      // then -- recovery applied; RF=4 all in zoneB
      assertChangeDone(actuator, response);

      final var finalTopology = actuator.getTopology();
      assertThat(finalTopology.getPartitionDistribution()).isEqualTo(config);

      final var expectedMemberIds = brokersInZone(ZONES[1], 0, 1, 2, 3);

      // all zoneB brokers present in the final topology, each hosting both partitions (RF=4)
      assertThat(finalTopology.getBrokers())
          .describedAs("all zoneB brokers present in final topology")
          .extracting(BrokerState::getId)
          .containsExactlyInAnyOrder(
              expectedMemberIds.stream().map(BrokerId::of).toList().toArray(new BrokerId[0]));
      assertThat(finalTopology.getBrokers())
          .allSatisfy(
              broker ->
                  assertThat(broker.getPartitions())
                      .describedAs("broker %s has all partitions", broker.getId())
                      .hasSize(2));
      for (final var id : expectedMemberIds) {
        Awaitility.await()
            .untilAsserted(() -> TestCluster.assertHealthyTopology(zoneBBrokers.get(id)));
      }
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

  private static List<MemberId> brokersInZone(final String zone, final int... ids) {
    return Arrays.stream(ids).mapToObj(id -> MemberId.from(zone, id)).toList();
  }
}
