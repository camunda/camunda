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
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PartitionGroupExporterStateInitializerTest {

  private static final MemberId LOCAL_MEMBER_ID = MemberId.from("0");

  @Test
  void shouldUpdateLocalMemberExporterStateInEveryGroup() {
    // given — local member replicates a partition in two different groups
    final var config = DynamicPartitionConfig.init();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a", groupWithMember(LOCAL_MEMBER_ID, config),
                "tenant-b", groupWithMember(LOCAL_MEMBER_ID, config)),
            PhasedChangeState.empty());

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(Set.of("expA"), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    for (final var groupId : Set.of("tenant-a", "tenant-b")) {
      final var partitionState =
          result.partitionGroup(groupId).getMember(LOCAL_MEMBER_ID).getPartition(1);
      assertThat(partitionState.config().exporting().exporters()).containsKey("expA");
    }
  }

  @Test
  void shouldNotUpdateOtherMembersInAGroup() {
    // given
    final var config = DynamicPartitionConfig.init();
    final var otherMember = MemberId.from("1");
    final var group =
        groupWithMember(LOCAL_MEMBER_ID, config)
            .addMember(otherMember, initialPartitionState(config));
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", group),
            PhasedChangeState.empty());

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(Set.of("expA"), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(
            result
                .partitionGroup("tenant-a")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .containsKey("expA");
    assertThat(
            result
                .partitionGroup("tenant-a")
                .getMember(otherMember)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .doesNotContainKey("expA");
  }

  @Test
  void shouldNotChangeGroupWhereLocalMemberIsAbsent() {
    // given — local member does not replicate any partition in this group
    final var otherMember = MemberId.from("1");
    final var config = DynamicPartitionConfig.init();
    final var group = groupWithMember(otherMember, config);
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", group),
            PhasedChangeState.empty());

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(Set.of("expA"), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotUpdateWhenNoExporterChanges() {
    // given
    final var config =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of("expA", new ExporterState(0, State.ENABLED, Optional.empty()))));
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", groupWithMember(LOCAL_MEMBER_ID, config)),
            PhasedChangeState.empty());

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(Set.of("expA"), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  private static PartitionGroupConfiguration groupWithMember(
      final MemberId memberId, final DynamicPartitionConfig partitionConfig) {
    return PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
        .addMember(memberId, initialPartitionState(partitionConfig));
  }

  private static BrokerPartitionState initialPartitionState(
      final DynamicPartitionConfig partitionConfig) {
    return BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, partitionConfig)));
  }
}
