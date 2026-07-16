/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.zoneaware;

import static io.camunda.zeebe.it.cluster.clustering.zoneaware.ZoneHelpers.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Zone;
import io.camunda.zeebe.it.cluster.clustering.zoneaware.ZoneHelpers.AddZoneScenario;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.DynamicAutoCloseable;
import java.util.List;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that a new zone can be added to a running zone-aware cluster via {@code PUT
 * /actuator/cluster/partition-distribution}.
 *
 * <p>Adding a zone requires the new zone's broker(s) to already be members of the persisted cluster
 * topology before the distribution is updated, otherwise {@code ZoneAwarePartitionDistributor}
 * rejects the request because the zone has no brokers. Each test therefore:
 *
 * <ul>
 *   <li>starts a zone-aware
 *   <li>cluster over the initial zone(s)
 *   <li>starts a standalone broker in the new zone that joins the cluster
 *   <li>adds it to the topology via the cluster actuator
 *   <li>updates the partition distribution to include the new zone
 * </ul>
 */
@ZeebeIntegration
@Timeout(2 * 60)
final class AddZoneToClusterIT {

  private static final int PARTITIONS_COUNT = 2;
  @AutoClose
  final DynamicAutoCloseable closeables = new DynamicAutoCloseable();

  @ParameterizedTest(name = "{0}")
  @MethodSource("addZoneScenarios")
  void shouldAddZoneToCluster(final AddZoneScenario scenario) {
    // given - a zone-aware cluster over the initial zones
    try (final var cluster =
        createCluster(scenario.clusterName(), scenario.initialZones(), PARTITIONS_COUNT, 2)) {
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - start a broker in the new zone, add it to the topology, then add the zone to the
      // partition distribution
      closeables.manage(addBrokerInZone(
          cluster,
          actuator,
          scenario.clusterName(),
          scenario.newZone(),
          0,
          3,
          scenario.targetZones()));

      final var config =
          new PartitionDistributionConfig()
              .type(TypeEnum.ZONE_AWARE)
              .zones(
                  scenario.targetZones().stream()
                      .map(
                          z ->
                              new ZoneSpec()
                                  .name(z.name())
                                  .numberOfReplicas(z.numberOfReplicas())
                                  .priority(z.priority()))
                      .toList());
      final var response = actuator.patchPartitionDistribution(config, false);

      // then - the new distribution is applied and the new zone hosts partitions
      Awaitility.await()
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));
      assertThat(actuator.getTopology().getPartitionDistribution()).isEqualTo(config);
      assertZoneHostsPartitions(actuator, scenario.newZone(), 0);
    }
  }

  private static Stream<Arguments> addZoneScenarios() {
    return Stream.of(
        Arguments.of(
            new AddZoneScenario(
                "add-zone-single",
                List.of(new Zone("zoneA", 2, 2, 100)),
                List.of(new Zone("zoneA", 2, 2, 100), new Zone("zoneB", 1, 1, 10)),
                "zoneB")),
        Arguments.of(
            new AddZoneScenario(
                "add-zone-two",
                List.of(new Zone("zoneA", 1, 1, 100), new Zone("zoneB", 1, 1, 10)),
                List.of(
                    new Zone("zoneA", 1, 1, 100),
                    new Zone("zoneB", 1, 1, 10),
                    new Zone("zoneC", 1, 1, 5)),
                "zoneC")));
  }
}
