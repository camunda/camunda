/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Partitioning.Scheme;
import io.camunda.configuration.Zone;
import io.camunda.configuration.ZoneAware;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.ClusterZoneMigrationRequest;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.DynamicAutoCloseable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class NonZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  private static final String CLUSTER_NAME = "non-zone-aware-cluster-endpoint-it";

  @AutoClose private final DynamicAutoCloseable closeables = new DynamicAutoCloseable();

  @Override
  protected int brokerCount() {
    return 2;
  }

  @Override
  protected int partitionCount() {
    return 2;
  }

  @Override
  @SuppressWarnings("resource")
  protected TestCluster createCluster(
      final int brokerCount, final int partitionCount, final int replicationFactor) {
    return TestCluster.builder()
        .withName(CLUSTER_NAME)
        .withEmbeddedGateway(true)
        .withBrokersCount(brokerCount)
        .withPartitionsCount(partitionCount)
        .withReplicationFactor(replicationFactor)
        .build()
        .start();
  }

  @Override
  protected String zone() {
    return null;
  }

  @Override
  protected BrokerId brokerId(final int nodeIdx) {
    return new BrokerId.Integer(nodeIdx);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("zoneMigrationScenarios")
  void shouldMigrateUnzonedClusterToZoneAwareConfiguration(
      final String description, final ZoneMigrationScenario scenario) {
    try (final var cluster =
        createCluster(
            scenario.brokerCount(), scenario.partitionCount(), scenario.replicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      final var distributionUpdate =
          actuator.patchPartitionDistribution(scenario.expectedDistribution(), false);
      assertThat(distributionUpdate.getPlannedChanges()).isNotEmpty();
      Awaitility.await()
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () -> {
                final var topology = actuator.getTopology();
                assertThat(topology.getPendingChange()).isNull();
                assertThat(topology.getPartitionDistribution())
                    .isEqualTo(scenario.expectedDistribution());
              });

      // start brokers in the first migration stage
      startReplacementBrokers(cluster, scenario.initialReplacementBrokerIds(), scenario);

      // when
      for (int i = 0; i < scenario.migrationZones().size(); i++) {
        final var response =
            actuator.migrateZone(zoneMigrationRequest(scenario.migrationZones().get(i)), false);

        // then
        assertThat(response.getPlannedChanges()).isNotEmpty();
        Awaitility.await()
            .atMost(Duration.ofMinutes(1))
            .untilAsserted(() -> assertThat(actuator.getTopology().getPendingChange()).isNull());
        if (i == 0 && !scenario.delayedReplacementBrokerIds().isEmpty()) {
          startReplacementBrokers(cluster, scenario.delayedReplacementBrokerIds(), scenario);
        }
      }

      // then
      Awaitility.await()
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () -> {
                final var topology = actuator.getTopology();
                assertThat(topology.getPendingChange()).isNull();
                assertThat(topology.getPartitionDistribution())
                    .isEqualTo(scenario.expectedDistribution());
                assertThat(topology.getBrokers())
                    .extracting(BrokerState::getId)
                    .allMatch(BrokerId.String.class::isInstance)
                    .map(id -> id.brokerId().memberId())
                    .containsExactlyInAnyOrderElementsOf(scenario.expectedBrokerIds());
                assertThat(partitionsByZone(topology.getBrokers()))
                    .allSatisfy(
                        (partitionId, zones) ->
                            assertThat(zones)
                                .describedAs(
                                    "partition %s should span the expected number of zones",
                                    partitionId)
                                .hasSize(scenario.expectedZonesPerPartition()));
              });
    }
  }

  private static Stream<Arguments> zoneMigrationScenarios() {
    final var singleRegionBrokerIds = zoneANodes(3);

    return Stream.of(
        Arguments.of(
            "single region",
            new ZoneMigrationScenario(
                3,
                3,
                3,
                List.of(new Zone(ZONE_A, 3, 3, 100)),
                List.of(ZONE_A),
                new PartitionDistributionConfig()
                    .type(TypeEnum.ZONE_AWARE)
                    .zones(List.of(new ZoneSpec().name(ZONE_A).numberOfReplicas(3).priority(100))),
                singleRegionBrokerIds,
                singleRegionBrokerIds,
                List.of(),
                1)),
        Arguments.of(
            "dual region",
            new ZoneMigrationScenario(
                4,
                2,
                4,
                List.of(new Zone(ZONE_A, 2, 2, 100), new Zone(ZONE_B, 2, 2, 100)),
                List.of(ZONE_B, ZONE_A),
                new PartitionDistributionConfig()
                    .type(TypeEnum.ZONE_AWARE)
                    .zones(
                        List.of(
                            new ZoneSpec().name(ZONE_A).numberOfReplicas(2).priority(100),
                            new ZoneSpec().name(ZONE_B).numberOfReplicas(2).priority(100))),
                List.of(ZONE_A_0, ZONE_A_1, ZONE_B_0, ZONE_B_1),
                List.of(ZONE_B_0, ZONE_B_1),
                List.of(ZONE_A_0, ZONE_A_1),
                2)));
  }

  private void startReplacementBrokers(
      final TestCluster cluster,
      final List<MemberId> brokerIds,
      final ZoneMigrationScenario scenario) {
    final var brokers =
        brokerIds.stream()
            .map(
                memberId ->
                    startReplacementBroker(
                        cluster,
                        scenario.targetZones(),
                        scenario.partitionCount(),
                        scenario.replicationFactor(),
                        memberId))
            .toList();
    brokers.forEach(broker -> broker.await(TestHealthProbe.READY, Duration.ofMinutes(1)));
  }

  @SuppressWarnings("resource")
  private TestStandaloneBroker startReplacementBroker(
      final TestCluster cluster,
      final List<Zone> targetZones,
      final int partitionCount,
      final int replicationFactor,
      final MemberId memberId) {
    final var contactPoint =
        cluster.brokers().values().iterator().next().address(TestZeebePort.CLUSTER);
    final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withUnifiedConfig(
                cfg -> {
                  final var clusterCfg = cfg.getCluster();
                  clusterCfg.setName(CLUSTER_NAME);
                  clusterCfg.setInitialContactPoints(List.of(contactPoint));
                  clusterCfg.setZone(memberId.zone());
                  clusterCfg.setNodeId(memberId.nodeIdx());
                  clusterCfg.setSize(cluster.brokers().size());
                  clusterCfg.setPartitionCount(partitionCount);
                  clusterCfg.setReplicationFactor(replicationFactor);
                  clusterCfg.getPartitioning().setScheme(Scheme.ZONE_AWARE);
                  clusterCfg.getPartitioning().setZoneAware(new ZoneAware(targetZones));
                  cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
                });

    final var startThread = Thread.ofVirtual().name("start-" + memberId).start(broker::start);
    closeables.manage(
        () -> {
          broker.close();
          startThread.interrupt();
          startThread.join(Duration.ofSeconds(30));
        });
    return broker;
  }

  private static ClusterZoneMigrationRequest zoneMigrationRequest(final String zone) {
    return new ClusterZoneMigrationRequest().zone(zone);
  }

  private static Map<Integer, Set<String>> partitionsByZone(final List<BrokerState> brokers) {
    return brokers.stream()
        .filter(broker -> broker.getId() instanceof BrokerId.String)
        .collect(
            Collectors.groupingBy(
                broker -> MemberId.from(((BrokerId.String) broker.getId()).value()).zone(),
                Collectors.flatMapping(
                    broker -> broker.getPartitions().stream().map(PartitionState::getId),
                    Collectors.toSet())))
        .entrySet()
        .stream()
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .map(partitionId -> Map.entry(partitionId, entry.getKey())))
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
  }

  private record ZoneMigrationScenario(
      int brokerCount,
      int partitionCount,
      int replicationFactor,
      List<Zone> targetZones,
      List<String> migrationZones,
      PartitionDistributionConfig expectedDistribution,
      List<MemberId> expectedBrokerIds,
      List<MemberId> initialReplacementBrokerIds,
      List<MemberId> delayedReplacementBrokerIds,
      int expectedZonesPerPartition) {}
}
