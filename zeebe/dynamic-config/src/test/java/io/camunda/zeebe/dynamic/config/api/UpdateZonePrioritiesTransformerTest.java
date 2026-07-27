/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class UpdateZonePrioritiesTransformerTest {

  private static final DynamicPartitionConfig PARTITION_CONFIG = DynamicPartitionConfig.init();

  private static final List<PartitionId> PARTITION_IDS =
      IntStream.rangeClosed(1, 2).mapToObj(i -> new PartitionId("temp", i)).toList();

  // Builds a fully zone-aware ClusterConfiguration with one member per zone. Modeled on
  // ForceRemoveZoneTransformerTest#buildTopology /
  // UpdatePartitionDistributionTransformerTest#buildTopology.
  private ClusterConfiguration zoneAwareCluster(final ZoneSpec... zones) {
    final var config = new ZoneAwareConfig(List.of(zones));
    final var members =
        config.zones().stream()
            .map(zone -> MemberId.from(zone.name(), 0))
            .collect(java.util.stream.Collectors.toSet());
    final var distribution =
        new ZoneAwarePartitionDistributor(config.zones())
            .distributePartitions(Set.copyOf(members), PARTITION_IDS, config.replicationFactor());
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c");
    return topology.setPartitionDistributorConfig(config);
  }

  @Test
  void shouldSwapPrioritiesByRequestOrderKeepingExistingValues() {
    // given zone-a=3 (leader), zone-b=1
    final var config = zoneAwareCluster(new ZoneSpec(ZONE_A, 1, 3), new ZoneSpec(ZONE_B, 1, 1));

    // when requesting order [zone-b, zone-a]  (zone-b should now be leader)
    final var result =
        new UpdateZonePrioritiesTransformer(List.of(ZONE_B, ZONE_A)).operations(config);

    // then the persisted config keeps the SAME priority values {3,1} but re-assigned
    EitherAssert.assertThat(result).isRight();
    final var configOp =
        result.get().stream()
            .filter(UpdatePartitionDistributorConfigOperation.class::isInstance)
            .map(UpdatePartitionDistributorConfigOperation.class::cast)
            .findFirst()
            .orElseThrow();
    final var newZones = ((ZoneAwareConfig) configOp.config()).zones();
    assertThat(newZones)
        .extracting(ZoneSpec::name, ZoneSpec::priority)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(ZONE_B, 3),
            org.assertj.core.groups.Tuple.tuple(ZONE_A, 1));

    // and a partition priority-reconfigure operation is emitted so leaders actually move
    assertThat(result.get()).anyMatch(PartitionReconfigurePriorityOperation.class::isInstance);
  }

  @Test
  void shouldRejectWhenRequestZonesDoNotMatchConfiguredZones() {
    // given
    final var config = zoneAwareCluster(new ZoneSpec(ZONE_A, 1, 3), new ZoneSpec(ZONE_B, 1, 1));

    // when a zone is unknown / set mismatch
    final var result =
        new UpdateZonePrioritiesTransformer(List.of(ZONE_A, ZONE_C)).operations(config);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining(
            "Zone priority request must list exactly the configured zones [zone-a, zone-b], but got [zone-a, zone-c]");
  }

  @Test
  void shouldRejectDuplicateZoneInRequest() {
    final var config = zoneAwareCluster(new ZoneSpec(ZONE_A, 1, 3), new ZoneSpec(ZONE_B, 1, 1));
    final var result =
        new UpdateZonePrioritiesTransformer(List.of(ZONE_A, ZONE_A)).operations(config);
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("Zone priority request contains duplicate zones: [zone-a, zone-a]");
  }

  @Test
  void shouldRejectIncompleteZoneList() {
    final var config = zoneAwareCluster(new ZoneSpec(ZONE_A, 1, 3), new ZoneSpec(ZONE_B, 1, 1));
    final var result = new UpdateZonePrioritiesTransformer(List.of(ZONE_A)).operations(config);
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining(
            "Zone priority request must list exactly the configured zones [zone-a, zone-b], but got [zone-a]");
  }

  @Test
  void shouldRejectWhenNotZoneAware() {
    // given a non-zone-aware cluster (no PartitionDistributorConfig persisted)
    final var config = ClusterConfiguration.init();
    final var result = new UpdateZonePrioritiesTransformer(List.of(ZONE_A)).operations(config);
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining(
            "Updating zone priorities requires a persisted zone-aware partition distribution config, but was not set");
  }
}
