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
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.CurrentClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CurrentClusterConfigurationInitializerTest {

  @Test
  void shouldInitializeMultiGroupConfigurationFromStaticConfig() {
    // given — two members, each replicating a partition in a different group
    final var member0 = MemberId.from("0");
    final var member1 = MemberId.from("1");
    final var members = Set.of(member0, member1);
    final var defaultPartition =
        new PartitionMetadata(
            new PartitionId("default", 1), Set.of(member0), Map.of(member0, 1), 1, member0);
    final var tenantAPartition =
        new PartitionMetadata(
            new PartitionId("tenantA", 1), Set.of(member1), Map.of(member1, 1), 1, member1);
    final var staticConfiguration =
        new StaticConfiguration(
            new ControllablePartitionDistributor()
                .withPartitions(Set.of(defaultPartition, tenantAPartition)),
            members,
            member0,
            List.of(new PartitionId("default", 1), new PartitionId("tenantA", 1)),
            1,
            DynamicPartitionConfig.init(),
            "cluster-x");

    // when
    final var initializer = new StaticInitializer(staticConfiguration);
    final var configuration = initializer.initialize().join();

    // then — global membership includes both members, split into their respective groups
    assertThat(configuration.globalConfiguration().members().keySet())
        .containsExactlyInAnyOrder(member0, member1);
    assertThat(configuration.globalConfiguration().clusterId()).contains("cluster-x");
    assertThat(configuration.partitionGroups()).containsOnlyKeys("default", "tenantA");
    assertThat(configuration.partitionGroup("default").members().keySet()).containsExactly(member0);
    assertThat(configuration.partitionGroup("tenantA").members().keySet()).containsExactly(member1);
  }

  @Test
  void shouldGenerateNoPartitionGroupsWhenNoPartitionsAreDistributed() {
    // given — a distributor that returns no partitions
    final var member0 = MemberId.from("0");
    final var staticConfiguration =
        new StaticConfiguration(
            new ControllablePartitionDistributor().withPartitions(Set.of()),
            Set.of(member0),
            member0,
            List.of(new PartitionId("default", 1)),
            1,
            DynamicPartitionConfig.init(),
            null);

    // when
    final var initializer = new StaticInitializer(staticConfiguration);
    final var configuration = initializer.initialize().join();

    // then — no partitions were distributed, so no partition group is generated, but the member
    // still appears in the global configuration
    assertThat(configuration.partitionGroups()).isEmpty();
    assertThat(configuration.globalConfiguration().members().keySet()).containsExactly(member0);
  }
}
