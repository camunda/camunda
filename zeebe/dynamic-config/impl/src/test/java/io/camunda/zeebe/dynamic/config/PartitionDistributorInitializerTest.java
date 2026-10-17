/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PartitionDistributorInitializerTest {

  private static StaticConfiguration staticConfigWith(final PartitionDistributor distributor) {
    return new StaticConfiguration(
        distributor,
        Set.of(MemberId.from("0")),
        MemberId.from("0"),
        List.of(new PartitionId("test", 1)),
        1,
        Map.of("test", DynamicPartitionConfig.init()),
        null);
  }

  @Test
  void shouldSkipIfAlreadySetOnGlobalConfiguration() {
    // given
    final var configuration =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                global ->
                    global.setPartitionDistributorConfig(
                        new PartitionDistributorConfig.RoundRobinConfig()));
    final var initializer =
        PartitionDistributorInitializer.currentClusterConfigurationPartitionDistributorInitializer(
            staticConfigWith(new ZoneAwarePartitionDistributor(List.of())));

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldDeriveRoundRobinConfigOnGlobalConfigurationWhenAbsent() {
    // given
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        PartitionDistributorInitializer.currentClusterConfigurationPartitionDistributorInitializer(
            staticConfigWith(new RoundRobinPartitionDistributor()));

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.globalConfiguration().partitionDistributorConfig())
        .hasValue(new PartitionDistributorConfig.RoundRobinConfig());
  }

  @Test
  void shouldDeriveZoneAwareConfigOnGlobalConfiguration() {
    // given
    final var zoneSpecs = List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500));
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        PartitionDistributorInitializer.currentClusterConfigurationPartitionDistributorInitializer(
            staticConfigWith(new ZoneAwarePartitionDistributor(zoneSpecs)));

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.globalConfiguration().partitionDistributorConfig())
        .hasValueSatisfying(
            distributorConfig -> {
              assertThat(distributorConfig)
                  .isInstanceOf(PartitionDistributorConfig.ZoneAwareConfig.class);
              final var zoneAware = (PartitionDistributorConfig.ZoneAwareConfig) distributorConfig;
              assertThat(zoneAware.zones())
                  .containsExactlyInAnyOrder(
                      new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000),
                      new PartitionDistributorConfig.ZoneSpec("zone-b", 1, 500));
            });
  }

  @Test
  void shouldUseFixedConfigForUnknownDistributorOnGlobalConfiguration() {
    // given
    final PartitionDistributor unknownDistributor =
        (members, partitionIds, replicationFactor) -> Set.of();
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        PartitionDistributorInitializer.currentClusterConfigurationPartitionDistributorInitializer(
            staticConfigWith(unknownDistributor));

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.globalConfiguration().partitionDistributorConfig())
        .hasValue(new PartitionDistributorConfig.FixedConfig());
  }
}
