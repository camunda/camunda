/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ModeChangeRequestTransformerTest {

  private static final String TENANT_B = "tenant-b";

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldTargetOnlyMembersNotAlreadyInTheTargetModeOnTheLegacySingleGroupModel() {
    // given — a legacy single-group configuration (the mode lives on the member and is shared by
    // all of its partitions) where one member is active and one is already recovering
    final var transformer = recoveringTransformer(Optional.empty());
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()).toRecovering());

    // when
    final var result = transformer.operations(clusterConfiguration);

    // then — only the active member is transitioned, so re-sending the request is idempotent
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new ModeChangeOperation(id0, Mode.RECOVERING),
            new AwaitModeChangeOperation(id0, Mode.RECOVERING));
  }

  @Test
  void shouldIgnoreTheRequestedPhysicalTenantOnTheLegacySingleGroupModel() {
    // given — the same cluster planned twice: once scoped to a physical tenant, once unscoped
    final var clusterConfiguration =
        ClusterConfiguration.init().addMember(id0, MemberState.initializeAsActive(Map.of()));

    // when
    final var scoped =
        recoveringTransformer(Optional.of(TENANT_B)).operations(clusterConfiguration);
    final var unscoped = recoveringTransformer(Optional.empty()).operations(clusterConfiguration);

    // then — the scope makes no difference: the legacy model has no per-tenant mode, so the whole
    // cluster transitions either way. Scoping to one tenant is only expressible on the multi-group
    // model, via phases(), which is the only method the coordinator calls.
    EitherAssert.assertThat(scoped).isRight();
    EitherAssert.assertThat(unscoped).isRight();
    assertThat(scoped.get()).isNotEmpty().isEqualTo(unscoped.get());
  }

  @Test
  void shouldNotTransitionABrokerThatHostsNoPartitionOfTheGroup() {
    // given — both brokers are listed in the default group and both are still PROCESSING, but only
    // id0 hosts a partition there; id1 holds nothing and merely lingers in the member map. Both are
    // ACTIVE cluster-wide, so only the group can tell them apart.
    final var transformer = recoveringTransformer(Optional.empty());
    final var clusterConfiguration =
        cluster(
            Map.of(
                DEFAULT_GROUP,
                group(
                    Map.of(
                        id0, hostingAPartition(Mode.PROCESSING),
                        id1, hostingNothing(Mode.PROCESSING)))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — id1 is left out: it has no partition to transition, and awaiting a mode change from it
    // would never complete and would stall the whole plan
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
                Map.of(
                    DEFAULT_GROUP,
                    List.of(
                        new ModeChangeOperation(id0, Mode.RECOVERING),
                        new AwaitModeChangeOperation(id0, Mode.RECOVERING)))));
  }

  @Test
  void shouldSucceedWithNoPhasesWhenNoBrokerToTransitionHostsAPartitionOfTheGroup() {
    // given — the only broker still PROCESSING in the group hosts nothing there
    final var transformer = recoveringTransformer(Optional.empty());
    final var clusterConfiguration =
        cluster(Map.of(DEFAULT_GROUP, group(Map.of(id0, hostingNothing(Mode.PROCESSING)))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — nothing to plan, rather than a plan that can never complete
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldTargetOnlyTheRequestedPhysicalTenantsPartitionGroup() {
    // given
    final var transformer = recoveringTransformer(Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster(Mode.PROCESSING, Mode.PROCESSING));

    // then — the default group keeps processing
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
                Map.of(
                    TENANT_B,
                    List.of(
                        new ModeChangeOperation(id1, Mode.RECOVERING),
                        new AwaitModeChangeOperation(id1, Mode.RECOVERING)))));
  }

  @Test
  void shouldTransitionEveryPhysicalTenantInParallelWhenNoneIsRequested() {
    // given
    final var transformer = recoveringTransformer(Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster(Mode.PROCESSING, Mode.PROCESSING));

    // then — one phase carrying both groups, so their partitions transition concurrently
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
                Map.of(
                    DEFAULT_GROUP,
                    List.of(
                        new ModeChangeOperation(id0, Mode.RECOVERING),
                        new AwaitModeChangeOperation(id0, Mode.RECOVERING)),
                    TENANT_B,
                    List.of(
                        new ModeChangeOperation(id1, Mode.RECOVERING),
                        new AwaitModeChangeOperation(id1, Mode.RECOVERING)))));
  }

  @Test
  void shouldSkipPhysicalTenantsAlreadyInTheTargetMode() {
    // given — tenant-b already transitioned, the default group did not
    final var transformer = recoveringTransformer(Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster(Mode.PROCESSING, Mode.RECOVERING));

    // then — the group that has nothing to do is left out of the phase entirely
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
                Map.of(
                    DEFAULT_GROUP,
                    List.of(
                        new ModeChangeOperation(id0, Mode.RECOVERING),
                        new AwaitModeChangeOperation(id0, Mode.RECOVERING)))));
  }

  @Test
  void shouldRejectUnknownPhysicalTenant() {
    // given
    final var transformer = recoveringTransformer(Optional.of("unknown"));

    // when
    final var result = transformer.phases(twoTenantCluster(Mode.PROCESSING, Mode.PROCESSING));

    // then — the tenant does not exist, so the caller gets 404 rather than 500
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(NotFound.class).hasMessageContaining("unknown");
  }

  @Test
  void shouldSucceedWithNoPhasesWhenThePartitionGroupIsAlreadyInTheTargetMode() {
    // given — tenant-b already transitioned, the default group did not
    final var transformer = recoveringTransformer(Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster(Mode.PROCESSING, Mode.RECOVERING));

    // then — idempotent, and the group that did not transition is left alone
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  private static ModeChangeRequestTransformer recoveringTransformer(
      final Optional<String> physicalTenantId) {
    return new ModeChangeRequestTransformer(
        new ModeChangeRequest(physicalTenantId, Mode.RECOVERING, false));
  }

  private CurrentClusterConfiguration twoTenantCluster(
      final Mode defaultGroupMode, final Mode tenantBMode) {
    return cluster(
        Map.of(
            DEFAULT_GROUP, group(Map.of(id0, hostingAPartition(defaultGroupMode))),
            TENANT_B, group(Map.of(id1, hostingAPartition(tenantBMode)))));
  }

  private CurrentClusterConfiguration cluster(
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    final var brokers =
        partitionGroups.values().stream()
            .flatMap(group -> group.members().keySet().stream())
            .distinct()
            .collect(
                Collectors.toMap(
                    memberId -> memberId,
                    memberId -> new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)));
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1, Optional.empty(), brokers, Optional.empty(), Optional.empty(), Optional.empty()),
        partitionGroups,
        PhasedChangeState.empty());
  }

  private PartitionGroupConfiguration group(final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private BrokerPartitionState hostingAPartition(final Mode mode) {
    return BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, partitionConfig)))
        .setMode(mode);
  }

  private BrokerPartitionState hostingNothing(final Mode mode) {
    return BrokerPartitionState.initialize(Map.of()).setMode(mode);
  }
}
