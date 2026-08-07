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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
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

    // then — a bad request, so the caller gets 400 rather than 500
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(InvalidRequest.class).hasMessageContaining("unknown");
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

  /**
   * A cluster with one partition group per physical tenant: the default group hosted by member 0
   * and {@link #TENANT_B}'s group hosted by member 1, each in the given mode.
   */
  private CurrentClusterConfiguration twoTenantCluster(
      final Mode defaultGroupMode, final Mode tenantBMode) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                id0, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE),
                id1, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(
            DEFAULT_GROUP, singleMemberGroup(id0, defaultGroupMode),
            TENANT_B, singleMemberGroup(id1, tenantBMode)),
        PhasedChangeState.empty());
  }

  private PartitionGroupConfiguration singleMemberGroup(final MemberId memberId, final Mode mode) {
    return new PartitionGroupConfiguration(
        1,
        0,
        Map.of(
            memberId,
            BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, partitionConfig)))
                .setMode(mode)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
