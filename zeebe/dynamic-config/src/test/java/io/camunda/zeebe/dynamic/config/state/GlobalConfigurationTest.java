/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.FixedConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GlobalConfigurationTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  private static BrokerState broker(final long version, final State state) {
    return new BrokerState(version, Instant.EPOCH, state);
  }

  private static GlobalConfiguration config(
      final long version, final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        version, Optional.empty(), members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  @Nested
  class Merge {

    @Test
    void shouldTakeHigherConfigVersionWholesale() {
      // given — same member id, but the higher-version config has a different broker state
      final var lower = config(1, Map.of(MEMBER_0, broker(5, State.ACTIVE)));
      final var higher = config(2, Map.of(MEMBER_0, broker(1, State.LEAVING)));

      // when / then — the whole higher-version config wins regardless of member versions
      assertThat(lower.merge(higher)).isEqualTo(higher);
      assertThat(higher.merge(lower)).isEqualTo(higher);
    }

    @Test
    void shouldMergeMembersByMemberVersionWhenConfigVersionsEqual() {
      // given — same config version; MEMBER_0 newer on the left, MEMBER_1 only on the right
      final var left = config(3, Map.of(MEMBER_0, broker(9, State.ACTIVE)));
      final var right =
          config(3, Map.of(MEMBER_0, broker(2, State.JOINING), MEMBER_1, broker(1, State.ACTIVE)));

      // when
      final var merged = left.merge(right);

      // then — higher per-member version wins, union of members is kept
      assertThat(merged.members().get(MEMBER_0)).isEqualTo(broker(9, State.ACTIVE));
      assertThat(merged.members().get(MEMBER_1)).isEqualTo(broker(1, State.ACTIVE));
    }

    @Test
    void shouldMergePendingChangesByPlanVersion() {
      // given — same config version, two versions of the same plan
      final var plan = ClusterChangePlan.init(1, List.of(new DeleteHistoryOperation(MEMBER_0)));
      final var advancedPlan = plan.advance();
      final var left =
          new GlobalConfiguration(
              1, Optional.empty(), Map.of(), Optional.empty(), Optional.of(plan), Optional.empty());
      final var right =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.empty(),
              Optional.of(advancedPlan),
              Optional.empty());

      // when
      final var merged = left.merge(right);

      // then — the higher plan version wins
      assertThat(merged.pendingChanges()).contains(advancedPlan);
    }

    @Test
    void shouldKeepFirstNonEmptyClusterId() {
      // given — this has a cluster id, other has a different one
      final var withId =
          new GlobalConfiguration(
              1,
              Optional.of("cluster-a"),
              Map.of(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      final var withOtherId =
          new GlobalConfiguration(
              1,
              Optional.of("cluster-b"),
              Map.of(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());

      // when / then — this wins when present; other's id is adopted only when this is empty
      assertThat(withId.merge(withOtherId).clusterId()).contains("cluster-a");
      assertThat(config(1, Map.of()).merge(withId).clusterId()).contains("cluster-a");
    }

    @Test
    void shouldTakePresentPartitionDistributorConfigOverAbsent() {
      // given
      final var withConfig =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.of(new RoundRobinConfig()),
              Optional.empty(),
              Optional.empty());
      final var withoutConfig = config(1, Map.of());

      // when / then — present wins over absent, both directions
      assertThat(withConfig.merge(withoutConfig).partitionDistributorConfig())
          .contains(new RoundRobinConfig());
      assertThat(withoutConfig.merge(withConfig).partitionDistributorConfig())
          .contains(new RoundRobinConfig());
    }

    @Test
    void shouldThrowWhenMergingConflictingDistributorConfigsAtSameVersion() {
      // given — both present but different, at the same config version
      final var withRoundRobin =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.of(new RoundRobinConfig()),
              Optional.empty(),
              Optional.empty());
      final var withFixed =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.of(new FixedConfig()),
              Optional.empty(),
              Optional.empty());

      // when / then
      assertThatThrownBy(() -> withRoundRobin.merge(withFixed))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class StartConfigurationChange {

    @Test
    void shouldSetPendingChangesAndIncrementVersion() {
      // given
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when
      final var updated =
          config.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0)));

      // then
      assertThat(updated.version()).isEqualTo(5);
      assertThat(updated.hasPendingChanges()).isTrue();
      assertThat(updated.pendingChanges().get().pendingOperations())
          .containsExactly(new DeleteHistoryOperation(MEMBER_0));
    }

    @Test
    void shouldUseNewVersionAsPlanId() {
      // given
      final var config = config(4, Map.of());

      // when
      final var updated =
          config.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0)));

      // then
      assertThat(updated.pendingChanges().get().id()).isEqualTo(5);
    }

    @Test
    void shouldThrowWhenChangeAlreadyInProgress() {
      // given
      final var config =
          config(4, Map.of())
              .startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0)));

      // when / then
      assertThatThrownBy(
              () -> config.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0))))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenNoOperations() {
      // given
      final var config = config(4, Map.of());

      // when / then
      assertThatThrownBy(() -> config.startConfigurationChange(List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class MemberUpdates {

    @Test
    void shouldAddMemberWithoutChangingConfigVersion() {
      // given
      final var config = config(4, Map.of());

      // when
      final var updated = config.addMember(MEMBER_0, broker(0, State.JOINING));

      // then
      assertThat(updated.hasMember(MEMBER_0)).isTrue();
      assertThat(updated.version()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenAddingExistingMember() {
      // given
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when / then
      assertThatThrownBy(() -> config.addMember(MEMBER_0, broker(0, State.JOINING)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUpdateMemberBumpingOnlyTheMemberVersion() {
      // given
      final var config = config(4, Map.of(MEMBER_0, BrokerState.uninitialized()));

      // when
      final var updated = config.updateMember(MEMBER_0, b -> b.setState(State.ACTIVE));

      // then — the member's own version is bumped, the config version is not
      assertThat(updated.getMember(MEMBER_0).state()).isEqualTo(State.ACTIVE);
      assertThat(updated.getMember(MEMBER_0).version()).isEqualTo(1);
      assertThat(updated.version()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenUpdatingUnknownMember() {
      // given
      final var config = config(4, Map.of());

      // when / then
      assertThatThrownBy(() -> config.updateMember(MEMBER_0, b -> b.setState(State.ACTIVE)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldReturnSameConfigWhenMemberUpdateIsNoOp() {
      // given
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when / then — setState to the current state is a no-op, so the config is unchanged
      assertThat(config.updateMember(MEMBER_0, b -> b.setState(State.ACTIVE))).isSameAs(config);
    }

    @Test
    void shouldReturnNullForUnknownMember() {
      // given
      final var config = config(4, Map.of());

      // when / then
      assertThat(config.getMember(MEMBER_0)).isNull();
    }
  }

  @Nested
  class ClusterLevelUpdates {

    @Test
    void shouldSetClusterIdAndBumpVersion() {
      // given
      final var config = config(4, Map.of());

      // when
      final var updated = config.setClusterId("cluster-a");

      // then
      assertThat(updated.clusterId()).contains("cluster-a");
      assertThat(updated.version()).isEqualTo(5);
    }

    @Test
    void shouldReturnSameWhenClusterIdUnchanged() {
      // given
      final var config = config(4, Map.of()).setClusterId("cluster-a");

      // when / then
      assertThat(config.setClusterId("cluster-a")).isSameAs(config);
    }

    @Test
    void shouldSetPartitionDistributorConfigAndBumpVersion() {
      // given
      final var config = config(4, Map.of());

      // when
      final var updated = config.setPartitionDistributorConfig(new RoundRobinConfig());

      // then
      assertThat(updated.partitionDistributorConfig()).contains(new RoundRobinConfig());
      assertThat(updated.version()).isEqualTo(5);
    }

    @Test
    void shouldReturnSameWhenPartitionDistributorConfigUnchanged() {
      // given
      final var config = config(4, Map.of()).setPartitionDistributorConfig(new RoundRobinConfig());

      // when / then
      assertThat(config.setPartitionDistributorConfig(new RoundRobinConfig())).isSameAs(config);
    }
  }

  @Nested
  class Factory {

    @Test
    void shouldCreateInitConfig() {
      // when
      final var config = GlobalConfiguration.init();

      // then
      assertThat(config.version()).isEqualTo(GlobalConfiguration.INITIAL_VERSION);
      assertThat(config.members()).isEmpty();
      assertThat(config.clusterId()).isEmpty();
      assertThat(config.partitionDistributorConfig()).isEmpty();
      assertThat(config.pendingChanges()).isEmpty();
      assertThat(config.lastChange()).isEmpty();
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenClusterIdIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new GlobalConfiguration(
                      1, null, Map.of(), Optional.empty(), Optional.empty(), Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenMembersIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new GlobalConfiguration(
                      1,
                      Optional.empty(),
                      null,
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenPartitionDistributorConfigIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new GlobalConfiguration(
                      1, Optional.empty(), Map.of(), null, Optional.empty(), Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenPendingChangesIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new GlobalConfiguration(
                      1, Optional.empty(), Map.of(), Optional.empty(), null, Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenLastChangeIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new GlobalConfiguration(
                      1, Optional.empty(), Map.of(), Optional.empty(), Optional.empty(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
