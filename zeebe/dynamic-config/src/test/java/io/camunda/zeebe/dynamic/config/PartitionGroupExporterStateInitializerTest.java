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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.time.Instant;
import java.util.List;
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
    final var exporters = Map.of("tenant-a", Set.of("expA"), "tenant-b", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
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
    final var exporters = Map.of("tenant-a", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
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
    final var exporters = Map.of("tenant-a", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
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
    final var exporters = Map.of("tenant-a", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldOnlyUpdateTenantWhoseExportersAdded() {
    // given — local member replicates a partition in two different groups, but only one has
    // exporter changes
    final var configA = DynamicPartitionConfig.init();
    final var configB =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of("expA", new ExporterState(0, State.ENABLED, Optional.empty()))));
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a", groupWithMember(LOCAL_MEMBER_ID, configA),
                "tenant-b", groupWithMember(LOCAL_MEMBER_ID, configB)),
            PhasedChangeState.empty());

    // when
    final var exporters = Map.of("tenant-a", Set.of("expA"), "tenant-b", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — only tenant-a is updated; tenant-b is unchanged
    assertThat(
            result
                .partitionGroup("tenant-a")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .containsKey("expA");
    assertThat(result.partitionGroup("tenant-b"))
        .isEqualTo(configuration.partitionGroup("tenant-b"));
  }

  @Test
  void shouldOnlyUpdateTenantWhoseExporterRemoved() {
    // given — local member replicates a partition in two different groups, but only one has
    // exporter changes
    final var configA =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of("expA", new ExporterState(0, State.ENABLED, Optional.empty()))));
    final var configB =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of("expA", new ExporterState(0, State.ENABLED, Optional.empty()))));
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a", groupWithMember(LOCAL_MEMBER_ID, configA),
                "tenant-b", groupWithMember(LOCAL_MEMBER_ID, configB)),
            PhasedChangeState.empty());

    // when
    final var exporters = Map.of("tenant-a", Set.of("expA"), "tenant-b", Set.<String>of());
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — only tenant-b is updated; tenant-a is unchanged
    assertThat(result.partitionGroup("tenant-a"))
        .isEqualTo(configuration.partitionGroup("tenant-a"));
    assertThat(
            result
                .partitionGroup("tenant-b")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters()
                .get("expA")
                .state())
        .isEqualTo(State.CONFIG_NOT_FOUND);
  }

  @Test
  void shouldSkipAGroupAbsentFromLocalConfiguration() {
    // given — tenant-b was removed from local static configuration (see
    // PhysicalTenantAvailabilityInitializer) after being provisioned, so it has no entry in the
    // locally-configured exporters map, even though its group still exists and — since this is the
    // very first restart after the removal, before the coordinator-only availability initializer
    // has run — is still marked enabled
    final var config = DynamicPartitionConfig.init();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a", groupWithMember(LOCAL_MEMBER_ID, config),
                "tenant-b", groupWithMember(LOCAL_MEMBER_ID, config)),
            PhasedChangeState.empty());

    // when — only tenant-a has a local exporter configuration
    final var exporters = Map.of("tenant-a", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — tenant-a is reconciled normally, tenant-b is left untouched rather than throwing,
    // regardless of its still-stale disabled flag
    assertThat(
            result
                .partitionGroup("tenant-a")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .containsKey("expA");
    assertThat(result.partitionGroup("tenant-b"))
        .isEqualTo(configuration.partitionGroup("tenant-b"));
  }

  @Test
  void shouldSkipAGroupAbsentFromLocalConfigurationWhenCoordinatorAndPostRestore() {
    // given — same as above, but on the coordinator's post-restore, all-members reconciliation path
    final var config = DynamicPartitionConfig.init();
    final var otherMember = MemberId.from("1");
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a",
                groupWithMember(LOCAL_MEMBER_ID, config)
                    .addMember(otherMember, initialPartitionState(config)),
                "tenant-b",
                groupWithMember(LOCAL_MEMBER_ID, config)),
            postRestorePendingState());

    // when
    final var exporters = Map.of("tenant-a", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, true)
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
    assertThat(result.partitionGroup("tenant-b"))
        .isEqualTo(configuration.partitionGroup("tenant-b"));
  }

  @Test
  void shouldReconcileATenantThatReappearedInLocalConfigurationWhileStillMarkedDisabled() {
    // given — tenant-b reappeared in local static configuration, but this is the very first
    // restart since then: the coordinator-only PhysicalTenantAvailabilityInitializer has not yet
    // flipped its persisted flag back, so the group is still marked disabled even though it is now
    // locally configured again
    final var config = DynamicPartitionConfig.init();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-b", groupWithMember(LOCAL_MEMBER_ID, config).disable()),
            PhasedChangeState.empty());

    // when — tenant-b is locally configured again
    final var exporters = Map.of("tenant-b", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — reconciled normally despite the still-stale disabled flag
    assertThat(
            result
                .partitionGroup("tenant-b")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .containsKey("expA");
  }

  @Test
  void shouldSkipALocallyConfiguredTenantNotYetProvisioned() {
    // given — tenant-b is locally configured (has a local exporter configuration), but has not
    // been provisioned into the cluster configuration yet - PhysicalTenantProvisioningInitializer's
    // job, not this one's
    final var config = DynamicPartitionConfig.init();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", groupWithMember(LOCAL_MEMBER_ID, config)),
            PhasedChangeState.empty());

    // when
    final var exporters = Map.of("tenant-a", Set.of("expA"), "tenant-b", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — no error, and no group was created for tenant-b
    assertThat(result.partitionGroups()).containsOnlyKeys("tenant-a");
  }

  @Test
  void shouldUpdateAllMembersInEveryGroupWhenCoordinatorAndPostRestore() {
    // given — two groups, each with the local member and another member; the local member is
    // the coordinator, and a restore was just migrated (pending post-restore plan)
    final var config = DynamicPartitionConfig.init();
    final var otherMember = MemberId.from("1");
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(
                "tenant-a",
                groupWithMember(LOCAL_MEMBER_ID, config)
                    .addMember(otherMember, initialPartitionState(config)),
                "tenant-b",
                groupWithMember(LOCAL_MEMBER_ID, config)),
            postRestorePendingState());

    // when
    final var exporters = Map.of("tenant-a", Set.of("expA"), "tenant-b", Set.of("expA"));
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, true)
            .modify(configuration)
            .join();

    // then — every member of every group is updated, not just the local member
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
        .containsKey("expA");
    assertThat(
            result
                .partitionGroup("tenant-b")
                .getMember(LOCAL_MEMBER_ID)
                .getPartition(1)
                .config()
                .exporting()
                .exporters())
        .containsKey("expA");
  }

  @Test
  void shouldSkipWhenNonCoordinatorAndPostRestore() {
    // given
    final var config = DynamicPartitionConfig.init();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", groupWithMember(LOCAL_MEMBER_ID, config)),
            postRestorePendingState());
    final var exporters = Map.of("tenant-a", Set.of("expA"));

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — unchanged; the coordinator will initialize on behalf of everyone
    assertThat(result).isEqualTo(configuration);
  }

  private static PhasedChangeState postRestorePendingState() {
    final var plan =
        PhasedChangePlan.initForRestore(
            List.of(
                PartitionGroupPhase.sequential(
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        List.of(new UpdateRoutingState(LOCAL_MEMBER_ID, Optional.empty()))))),
            Instant.EPOCH);
    return new PhasedChangeState(1L, Map.of(plan.id(), plan), List.of());
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
