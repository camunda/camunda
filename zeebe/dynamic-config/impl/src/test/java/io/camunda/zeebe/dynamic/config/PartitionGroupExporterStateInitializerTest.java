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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
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
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldNotUpdateOtherMembersInAGroupOnNormalRestart(final boolean isCoordinator) {
    // given — a normal restart (no post-restore pending plan): isCoordinator alone must not
    // widen the write scope beyond the local member, only isAfterRestore() does that (see
    // PartitionGroupExporterStateInitializer's class javadoc on why writing another member's
    // exporter state outside of that case races the group-version bump and gets the broker
    // shut down as an inconsistent configuration)
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
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, isCoordinator)
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
            postRestorePendingState("tenant-a", "tenant-b"));

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
            postRestorePendingState("tenant-a", "tenant-b"));

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
            postRestorePendingState("tenant-a"));
    final var exporters = Map.of("tenant-a", Set.of("expA"));

    // when
    final var result =
        new PartitionGroupExporterStateInitializer(exporters, LOCAL_MEMBER_ID, false)
            .modify(configuration)
            .join();

    // then — unchanged; the coordinator will initialize on behalf of everyone
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldUpdateOnlyLocalMemberWhenPendingPlanIsNotARestore() {
    // given — a pending PartitionGroupPhase with an UpdateRoutingState operation, the same shape
    // a restore plan has, but under a normal plan id rather than the restore sentinel.
    // isAfterRestore() keys off that sentinel id, not the operation shape, so even with
    // isCoordinator=true this must be treated as an ordinary restart: only the local member is
    // updated, not every member of the group.
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
            normalPendingState("tenant-a"));

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

  /**
   * The post-restore plan as {@code RestoreManager} writes it: one phase naming every restored
   * partition group. The groups must be the configuration's own — a plan naming fewer groups than
   * the configuration has is not a shape a restore can produce, and testing against one would let a
   * single-group-only {@code isAfterRestore()} pass this suite.
   */
  private static PhasedChangeState postRestorePendingState(final String... groupIds) {
    final Map<String, List<PartitionGroupOperation>> operations = new TreeMap<>();
    for (final var groupId : groupIds) {
      operations.put(groupId, List.of(new UpdateRoutingState(LOCAL_MEMBER_ID, Optional.empty())));
    }
    final var plan =
        PhasedChangePlan.initForRestore(
            List.of(PartitionGroupPhase.sequential(operations)), Instant.EPOCH);
    return new PhasedChangeState(1L, Map.of(plan.id(), plan), List.of());
  }

  /**
   * A pending plan carrying the same {@link PartitionGroupPhase}/{@link UpdateRoutingState} shape a
   * restore plan has (see {@link #postRestorePendingState}), but under a normal plan id ({@link
   * PhasedChangePlan#INITIAL_PLAN_ID}) rather than {@link PhasedChangePlan#RESTORED_PLAN_ID} — the
   * distinction {@link CurrentClusterConfiguration#isAfterRestore()} keys off.
   */
  private static PhasedChangeState normalPendingState(final String... groupIds) {
    final Map<String, List<PartitionGroupOperation>> operations = new TreeMap<>();
    for (final var groupId : groupIds) {
      operations.put(groupId, List.of(new UpdateRoutingState(LOCAL_MEMBER_ID, Optional.empty())));
    }
    final var plan =
        new PhasedChangePlan(
            PhasedChangePlan.INITIAL_PLAN_ID,
            0,
            List.of(PartitionGroupPhase.sequential(operations)),
            Instant.EPOCH);
    return new PhasedChangeState(plan.id() + 1, Map.of(plan.id(), plan), List.of());
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

  /**
   * Tests {@link PartitionGroupExporterStateInitializer#updateExporterStateInPartition} directly,
   * as the pure function of a partition's state and the configured exporters that it is. Ported
   * from the exporter-state-reconciliation cases of the (now removed) legacy {@code
   * ExporterStateInitializer}, which shared this same static helper before the per-partition-group
   * variant took it over.
   */
  @Nested
  final class ExporterStateReconciliationTest {

    @ParameterizedTest
    @MethodSource("provideConfigs")
    void shouldUpdateExporterConfig(final ExporterConfigParameter parameter) {
      // when
      final var updated =
          PartitionGroupExporterStateInitializer.updateExporterStateInPartition(
              parameter.initialState(), parameter.configuredExporters());

      // then
      assertThat(updated.config()).isEqualTo(parameter.expectedConfig());
    }

    @Test
    void shouldUpdateExporterConfigOnFirstUpdateToV86() {
      // given — a partition state persisted before exporter-state tracking was introduced
      final var initialState = PartitionState.active(1, DynamicPartitionConfig.uninitialized());

      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.UNKNOWN,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));

      // when
      final var updated =
          PartitionGroupExporterStateInitializer.updateExporterStateInPartition(
              initialState, Set.of("expA", "expB"));

      // then
      assertThat(updated.config()).isEqualTo(expectedConfig);
    }

    static Stream<Arguments> provideConfigs() {
      return Stream.of(
          exporterAdded(),
          enabledExporterRemoved(),
          exporterAddedAndRemoved(),
          disabledExporterConfigRemoved(),
          exporterReadded(),
          configNotFoundExporterNotReadded());
    }

    private static Arguments exporterAddedAndRemoved() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.CONFIG_NOT_FOUND, Optional.empty()),
                      "exporter1",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter2",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));

      return Arguments.of(
          Named.of(
              "Exporters Added and Removed",
              new ExporterConfigParameter(
                  withTwoEnabledExporters(),
                  Set.of("exporter1", "exporter2", "expA"),
                  expectedConfig)));
    }

    private static Arguments enabledExporterRemoved() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.CONFIG_NOT_FOUND, Optional.empty()))));
      return Arguments.of(
          Named.of(
              "Enabled Exporters Removed",
              new ExporterConfigParameter(
                  withTwoEnabledExporters(), Set.of("expA"), expectedConfig)));
    }

    private static Arguments exporterAdded() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter1",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter2",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));
      return Arguments.of(
          Named.of(
              "New Exporters Added",
              new ExporterConfigParameter(
                  withTwoEnabledExporters(),
                  Set.of("expA", "expB", "exporter1", "exporter2"),
                  expectedConfig)));
    }

    private static Arguments exporterReadded() {
      final var initialConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expC",
                      new ExporterState(1, State.CONFIG_NOT_FOUND, Optional.empty()))));
      final var initialState = PartitionState.active(1, initialConfig);

      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expC",
                      new ExporterState(1, State.ENABLED, Optional.empty()))));
      return Arguments.of(
          Named.of(
              "Exporters Readded",
              new ExporterConfigParameter(initialState, Set.of("expA", "expC"), expectedConfig)));
    }

    private static Arguments configNotFoundExporterNotReadded() {
      final var initialConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expC",
                      new ExporterState(1, State.CONFIG_NOT_FOUND, Optional.empty()))));
      final var initialState = PartitionState.active(1, initialConfig);

      // expC is still absent from the application config — it must stay CONFIG_NOT_FOUND
      return Arguments.of(
          Named.of(
              "CONFIG_NOT_FOUND Exporter Not Readded",
              new ExporterConfigParameter(initialState, Set.of("expA"), initialConfig)));
    }

    private static Arguments disabledExporterConfigRemoved() {
      final var initialConfig =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.DISABLED, Optional.empty()))));
      final var initialState = PartitionState.active(1, initialConfig);

      // expectedConfig = initialConfig. Disabled exporters should stay disabled.
      return Arguments.of(
          Named.of(
              "Disabled Exporter's Config is Removed",
              new ExporterConfigParameter(initialState, Set.of("expA"), initialConfig)));
    }

    private static PartitionState withTwoEnabledExporters() {
      final DynamicPartitionConfig config =
          new DynamicPartitionConfig(
              new ExportingConfig(
                  ExportingState.EXPORTING,
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));
      return PartitionState.active(1, config);
    }

    private record ExporterConfigParameter(
        PartitionState initialState,
        Set<String> configuredExporters,
        DynamicPartitionConfig expectedConfig) {}
  }
}
