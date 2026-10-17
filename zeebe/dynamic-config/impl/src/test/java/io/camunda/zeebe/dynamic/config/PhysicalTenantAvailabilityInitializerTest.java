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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PhysicalTenantAvailabilityInitializerTest {

  private static final MemberId LOCAL_MEMBER_ID = MemberId.from("0");

  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  @Test
  void shouldDisableATenantRemovedFromLocalStaticConfiguration() {
    // given — tenantA is still in the topology, but the local static configuration only lists
    // tenantB
    final var existing =
        Set.of(
            partition("tenantA", 1, Set.of(member(0))), partition("tenantB", 1, Set.of(member(0))));
    final var configuration = configurationWith(existing);
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantB", 1)));
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.partitionGroup("tenantA").isDisabled()).isTrue();
    assertThat(result.partitionGroup("tenantB").isDisabled()).isFalse();
  }

  @Test
  void shouldRetainPartitionAssignmentWhileDisabling() {
    // given
    final var existing = Set.of(partition("tenantA", 1, Set.of(member(0), member(1))));
    final var configuration = configurationWith(existing);
    final var staticConfiguration = staticConfigWith(List.of());
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — members/partitions are untouched, only availability changed
    assertThat(result.partitionGroup("tenantA").isDisabled()).isTrue();
    assertThat(result.partitionGroup("tenantA").members())
        .isEqualTo(configuration.partitionGroup("tenantA").members());
    assertThat(result.partitionGroup("tenantA").version())
        .isEqualTo(configuration.partitionGroup("tenantA").version());
  }

  @Test
  void shouldReEnableATenantThatReappearsInLocalStaticConfiguration() {
    // given — tenantA was previously disabled, but is now back in the local static configuration
    final var existing = Set.of(partition("tenantA", 1, Set.of(member(0))));
    final var disabledConfiguration =
        configurationWith(existing)
            .updatePartitionGroupConfig("tenantA", PartitionGroupConfiguration::disable);
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantA", 1)));
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(disabledConfiguration).join();

    // then
    assertThat(result.partitionGroup("tenantA").isDisabled()).isFalse();
  }

  @Test
  void shouldNotChangeAnythingWhenEveryGroupMatchesLocalStaticConfiguration() {
    // given
    final var existing = Set.of(partition("tenantA", 1, Set.of(member(0))));
    final var configuration = configurationWith(existing);
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantA", 1)));
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldLeaveARemovedTenantRemovedWhenStaticConfigurationListsItAgain() {
    // given — tenantA was explicitly removed, but its id is (still or again) listed in the local
    // static configuration
    final var existing = Set.of(partition("tenantA", 1, Set.of(member(0))));
    final var removedConfiguration =
        configurationWith(existing)
            .updatePartitionGroupConfig("tenantA", PartitionGroupConfiguration::remove);
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantA", 1)));
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(removedConfiguration).join();

    // then — removal is terminal: the tenant stays removed, it is not re-enabled
    assertThat(result.partitionGroup("tenantA").isRemoved()).isTrue();
    assertThat(result.partitionGroup("tenantA").isDisabled()).isTrue();
  }

  @Test
  void shouldLeaveANotYetProvisionedTenantAlone() {
    // given — the local static configuration lists tenantB, but it has no group yet (provisioning's
    // job, not this initializer's)
    final var existing = Set.of(partition("tenantA", 1, Set.of(member(0))));
    final var configuration = configurationWith(existing);
    final var staticConfiguration =
        staticConfigWith(
            List.of(tenantPartitionIds("tenantA", 1), tenantPartitionIds("tenantB", 1)));
    final var initializer = new PhysicalTenantAvailabilityInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — no group was created for tenantB, and tenantA is untouched (still enabled)
    assertThat(result.partitionGroups()).containsOnlyKeys("tenantA");
    assertThat(result.partitionGroup("tenantA").isDisabled()).isFalse();
  }

  private StaticConfiguration staticConfigWith(final List<List<PartitionId>> tenantPartitionIds) {
    final List<PartitionId> allPartitionIds =
        tenantPartitionIds.stream().flatMap(List::stream).toList();
    final var tenantConfigs =
        tenantPartitionIds.stream()
            .map(list -> list.get(0).group())
            .collect(Collectors.toMap(Function.identity(), group -> partitionConfig));
    return new StaticConfiguration(
        new RoundRobinPartitionDistributor(),
        Set.of(member(0), member(1), member(2)),
        LOCAL_MEMBER_ID,
        allPartitionIds,
        1,
        tenantConfigs,
        "clusterId");
  }

  private List<PartitionId> tenantPartitionIds(final String tenantId, final int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> new PartitionId(tenantId, number))
        .toList();
  }

  private CurrentClusterConfiguration configurationWith(final Set<PartitionMetadata> existing) {
    final Set<MemberId> members = new HashSet<>();
    existing.forEach(metadata -> members.addAll(metadata.members()));
    for (int i = 0; i < 3; i++) {
      members.add(member(i));
    }
    final var tenantConfigs =
        existing.stream()
            .map(p -> p.id().group())
            .distinct()
            .collect(Collectors.toMap(Function.identity(), group -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members, existing, tenantConfigs, "clusterId");
  }

  private PartitionMetadata partition(
      final String group, final int number, final Set<MemberId> members) {
    final Map<MemberId, Integer> priorities = new HashMap<>();
    final var sortedMembers = members.stream().sorted().toList();
    for (int i = 0; i < sortedMembers.size(); i++) {
      priorities.put(sortedMembers.get(i), sortedMembers.size() - i);
    }
    final var primary = sortedMembers.get(0);
    return new PartitionMetadata(
        new PartitionId(group, number), members, priorities, priorities.get(primary), primary);
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
