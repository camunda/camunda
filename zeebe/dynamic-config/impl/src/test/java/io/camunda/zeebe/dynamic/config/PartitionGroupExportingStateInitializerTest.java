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
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PartitionGroupExportingStateInitializerTest {

  private static final MemberId LOCAL_MEMBER_ID = MemberId.from("0");

  @Test
  void shouldSeedLegacyExportingStateInEveryGroup() {
    // given — local member replicates a partition in two different groups, both still UNKNOWN
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a", groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.init()),
                "tenant-b", groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.init())));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(
                    new PartitionId("tenant-a", 1), ExportingState.PAUSED,
                    new PartitionId("tenant-b", 1), ExportingState.SOFT_PAUSED),
                LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(exportingStateOf(result, "tenant-a", LOCAL_MEMBER_ID))
        .isEqualTo(ExportingState.PAUSED);
    assertThat(exportingStateOf(result, "tenant-b", LOCAL_MEMBER_ID))
        .isEqualTo(ExportingState.SOFT_PAUSED);
  }

  @Test
  void shouldNotOverwriteStateAlreadySetInDynamicConfiguration() {
    // given — the dynamic configuration already defines a state, so it is authoritative
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a",
                groupWithMember(
                    LOCAL_MEMBER_ID,
                    DynamicPartitionConfig.init()
                        .updateExporting(
                            exporting -> exporting.withState(ExportingState.SOFT_PAUSED)))));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(new PartitionId("tenant-a", 1), ExportingState.PAUSED), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotUpdateOtherMembersInAGroup() {
    // given
    final var otherMember = MemberId.from("1");
    final var group =
        groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.init())
            .addMember(otherMember, initialPartitionState(DynamicPartitionConfig.init()));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(new PartitionId("tenant-a", 1), ExportingState.PAUSED), LOCAL_MEMBER_ID)
            .modify(configurationOf(Map.of("tenant-a", group)))
            .join();

    // then
    assertThat(exportingStateOf(result, "tenant-a", LOCAL_MEMBER_ID))
        .isEqualTo(ExportingState.PAUSED);
    assertThat(exportingStateOf(result, "tenant-a", otherMember))
        .describedAs("The legacy file is local disk state, so other members are untouched")
        .isEqualTo(ExportingState.UNKNOWN);
  }

  @Test
  void shouldNotChangeGroupWhereLocalMemberIsAbsent() {
    // given — local member does not replicate any partition in this group
    final var configuration =
        configurationOf(
            Map.of("tenant-a", groupWithMember(MemberId.from("1"), DynamicPartitionConfig.init())));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(new PartitionId("tenant-a", 1), ExportingState.PAUSED), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotChangeWhenLegacyStateIsUnknown() {
    // given
    final var configuration =
        configurationOf(
            Map.of("tenant-a", groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.init())));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(new PartitionId("tenant-a", 1), ExportingState.UNKNOWN), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotChangeWhenLegacyStateIsMissing() {
    // given — no legacy state was read for this partition
    final var configuration =
        configurationOf(
            Map.of("tenant-a", groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.init())));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(Map.of(), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotSeedUninitializedPartitionConfig() {
    // given — the partition config is not initialized yet, so there is nothing to update
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a",
                groupWithMember(LOCAL_MEMBER_ID, DynamicPartitionConfig.uninitialized())));

    // when
    final var result =
        new PartitionGroupExportingStateInitializer(
                Map.of(new PartitionId("tenant-a", 1), ExportingState.PAUSED), LOCAL_MEMBER_ID)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  private static ExportingState exportingStateOf(
      final CurrentClusterConfiguration configuration,
      final String groupId,
      final MemberId memberId) {
    return configuration
        .partitionGroup(groupId)
        .getMember(memberId)
        .getPartition(1)
        .config()
        .exporting()
        .state();
  }

  private static CurrentClusterConfiguration configurationOf(
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        partitionGroups,
        PhasedChangeState.empty());
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
