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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PartitionGroupConfigurationTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  private static PartitionState partition(final int priority) {
    return PartitionState.active(priority, DynamicPartitionConfig.init());
  }

  private static BrokerPartitionState broker(final long version, final Integer... partitionIds) {
    final Map<Integer, PartitionState> partitions = new HashMap<>();
    for (final Integer id : partitionIds) {
      partitions.put(id, partition(1));
    }
    return new BrokerPartitionState(version, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  private static PartitionGroupConfiguration group(
      final long version, final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        version,
        PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  @Nested
  class Merge {

    @Test
    void shouldTakeHigherConfigVersionWholesale() {
      // given — same member id, but the higher-version group has a different broker state
      final var lower = group(1, Map.of(MEMBER_0, broker(5, 1)));
      final var higher = group(2, Map.of(MEMBER_0, broker(1, 2)));

      // when / then — the whole higher-version config wins, regardless of member versions
      assertThat(lower.merge(higher)).isEqualTo(higher);
      assertThat(higher.merge(lower)).isEqualTo(higher);
    }

    @Test
    void shouldMergeMembersByMemberVersionWhenConfigVersionsEqual() {
      // given — same config version; MEMBER_0 is newer on the left, MEMBER_1 only on the right
      final var left = group(3, Map.of(MEMBER_0, broker(9, 1)));
      final var right = group(3, Map.of(MEMBER_0, broker(2, 1), MEMBER_1, broker(1, 2)));

      // when
      final var merged = left.merge(right);

      // then — higher per-member version wins, union of members is kept
      assertThat(merged.members().get(MEMBER_0)).isEqualTo(broker(9, 1));
      assertThat(merged.members().get(MEMBER_1)).isEqualTo(broker(1, 2));
    }

    @Test
    void shouldMergeRoutingStateByVersion() {
      // given
      final var lowerRouting = RoutingState.initializeWithPartitionCount(1).withVersion(1);
      final var higherRouting = RoutingState.initializeWithPartitionCount(1).withVersion(2);
      final var left =
          new PartitionGroupConfiguration(
              1, 0, Map.of(), Optional.of(lowerRouting), Optional.empty(), Optional.empty());
      final var right =
          new PartitionGroupConfiguration(
              1, 0, Map.of(), Optional.of(higherRouting), Optional.empty(), Optional.empty());

      // when
      final var merged = left.merge(right);

      // then
      assertThat(merged.routingState()).contains(higherRouting);
    }

    @Test
    void shouldMergePendingChangesByPlanVersion() {
      // given — same config version, two versions of the same plan
      final var plan = ClusterChangePlan.init(1, List.of(new DeleteHistoryOperation(MEMBER_0)));
      final var advancedPlan = plan.advance();
      final var left =
          new PartitionGroupConfiguration(
              1, 0, Map.of(), Optional.empty(), Optional.of(plan), Optional.empty());
      final var right =
          new PartitionGroupConfiguration(
              1, 0, Map.of(), Optional.empty(), Optional.of(advancedPlan), Optional.empty());

      // when
      final var merged = left.merge(right);

      // then — the higher plan version wins
      assertThat(merged.pendingChanges()).contains(advancedPlan);
    }

    @Test
    void shouldTakeMaxIncarnationNumber() {
      // given
      final var left =
          new PartitionGroupConfiguration(
              1, 7, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
      final var right =
          new PartitionGroupConfiguration(
              1, 3, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());

      // when / then
      assertThat(left.merge(right).incarnationNumber()).isEqualTo(7);
      assertThat(right.merge(left).incarnationNumber()).isEqualTo(7);
    }

    @Test
    void shouldCarryPerBrokerModeThroughMemberMerge() {
      // given — same config version; MEMBER_0 entered recovery in its newer state
      final var left = group(3, Map.of(MEMBER_0, broker(1, 1)));
      final var recoveringBroker =
          new BrokerPartitionState(2, Instant.EPOCH, Map.of(1, partition(1)), Mode.RECOVERING);
      final var right = group(3, Map.of(MEMBER_0, recoveringBroker));

      // when
      final var merged = left.merge(right);

      // then — the mode rides the winning (higher-version) broker state
      assertThat(merged.members().get(MEMBER_0).mode()).isEqualTo(Mode.RECOVERING);
    }
  }

  @Nested
  class StartConfigurationChange {

    @Test
    void shouldSetPendingChangesAndIncrementVersion() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when
      final var updated =
          config.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0)));

      // then
      assertThat(updated.version()).isEqualTo(5);
      assertThat(updated.hasPendingChanges()).isTrue();
      assertThat(updated.pendingChanges()).isPresent();
      assertThat(updated.pendingChanges().get().pendingOperations())
          .containsExactly(new DeleteHistoryOperation(MEMBER_0));
    }

    @Test
    void shouldUseNewVersionAsPlanId() {
      // given
      final var config = group(4, Map.of());

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
          group(4, Map.of())
              .startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0)));

      // when / then
      assertThatThrownBy(
              () -> config.startConfigurationChange(List.of(new DeleteHistoryOperation(MEMBER_0))))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenNoOperations() {
      // given
      final var config = group(4, Map.of());

      // when / then
      assertThatThrownBy(() -> config.startConfigurationChange(List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Advance {

    private static final DeleteHistoryOperation OP_1 = new DeleteHistoryOperation(MEMBER_0);
    private static final DeleteHistoryOperation OP_2 = new DeleteHistoryOperation(MEMBER_1);

    @Test
    void shouldThrowWhenNoPendingChange() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when / then
      assertThatThrownBy(config::advance).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRemoveFirstPendingOperationWhenMoreRemain() {
      // given — a plan with two operations
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1))).startConfigurationChange(List.of(OP_1, OP_2));
      final var versionAfterStart = config.version();

      // when
      final var advanced = config.advance();

      // then — the first operation is removed, the change is still pending, version is unchanged
      assertThat(advanced.hasPendingChanges()).isTrue();
      assertThat(advanced.pendingChanges().get().pendingOperations()).containsExactly(OP_2);
      assertThat(advanced.version()).isEqualTo(versionAfterStart);
    }

    @Test
    void shouldCompleteChangeWhenLastOperationIsRemoved() {
      // given — a plan with a single operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1))).startConfigurationChange(List.of(OP_1));
      final var planId = config.pendingChanges().get().id();
      final var versionAfterStart = config.version();

      // when
      final var advanced = config.advance();

      // then — pending changes are cleared, the completed change is recorded, version is bumped
      assertThat(advanced.hasPendingChanges()).isFalse();
      assertThat(advanced.pendingChanges()).isEmpty();
      assertThat(advanced.lastChange()).isPresent();
      assertThat(advanced.lastChange().get().id()).isEqualTo(planId);
      assertThat(advanced.version()).isEqualTo(versionAfterStart + 1);
    }

    @Test
    void shouldRemoveMembersWithEmptyPartitionsOnCompletion() {
      // given — MEMBER_0 still hosts partition 1, MEMBER_1 hosts none; a single-op plan
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1)))
              .startConfigurationChange(List.of(OP_1));

      // when
      final var advanced = config.advance();

      // then — on completion the member with no partitions is removed, the other is kept
      assertThat(advanced.members()).containsOnlyKeys(MEMBER_0);
      assertThat(advanced.hasMember(MEMBER_1)).isFalse();
    }

    @Test
    void shouldNotRemoveEmptyMembersWhileOperationsRemain() {
      // given — MEMBER_1 hosts no partitions, but the plan still has a pending operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1)))
              .startConfigurationChange(List.of(OP_1, OP_2));

      // when
      final var advanced = config.advance();

      // then — members are untouched during an intermediate advance
      assertThat(advanced.members()).containsOnlyKeys(MEMBER_0, MEMBER_1);
    }
  }

  @Nested
  class MemberUpdates {

    @Test
    void shouldAddMemberWithoutChangingGroupVersion() {
      // given
      final var config = group(4, Map.of());

      // when
      final var updated = config.addMember(MEMBER_0, broker(0, 1));

      // then — member is present, group version is unchanged
      assertThat(updated.hasMember(MEMBER_0)).isTrue();
      assertThat(updated.version()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenAddingExistingMember() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(0, 1)));

      // when / then
      assertThatThrownBy(() -> config.addMember(MEMBER_0, broker(0, 2)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUpdateMemberBumpingOnlyTheMemberVersion() {
      // given
      final var config =
          group(4, Map.of(MEMBER_0, BrokerPartitionState.initialize(Map.of(1, partition(1)))));

      // when
      final var updated = config.updateMember(MEMBER_0, b -> b.setMode(Mode.RECOVERING));

      // then — the member's own version is bumped, the group version is not
      assertThat(updated.getMember(MEMBER_0).mode()).isEqualTo(Mode.RECOVERING);
      assertThat(updated.getMember(MEMBER_0).version()).isEqualTo(1);
      assertThat(updated.version()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenUpdatingUnknownMember() {
      // given
      final var config = group(4, Map.of());

      // when / then
      assertThatThrownBy(() -> config.updateMember(MEMBER_0, b -> b.setMode(Mode.RECOVERING)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldReturnSameConfigWhenMemberUpdateIsNoOp() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(0, 1)));

      // when / then — setMode to the current mode is a no-op, so the config is returned unchanged
      assertThat(config.updateMember(MEMBER_0, b -> b.setMode(Mode.PROCESSING))).isSameAs(config);
    }

    @Test
    void shouldSetRoutingStateWithoutChangingGroupVersion() {
      // given
      final var config = group(4, Map.of());
      final var routing = RoutingState.initializeWithPartitionCount(1);

      // when
      final var updated = config.setRoutingState(routing);

      // then
      assertThat(updated.routingState()).contains(routing);
      assertThat(updated.version()).isEqualTo(4);
    }

    @Test
    void shouldReturnNullForUnknownMember() {
      // given
      final var config = group(4, Map.of());

      // when / then
      assertThat(config.getMember(MEMBER_0)).isNull();
    }
  }

  @Nested
  class Factory {

    @Test
    void shouldCreateEmptyConfigAtGivenVersion() {
      // when
      final var config = PartitionGroupConfiguration.empty(7);

      // then
      assertThat(config.version()).isEqualTo(7);
      assertThat(config.incarnationNumber())
          .isEqualTo(PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER);
      assertThat(config.members()).isEmpty();
      assertThat(config.routingState()).isEmpty();
      assertThat(config.pendingChanges()).isEmpty();
      assertThat(config.lastChange()).isEmpty();
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenIncarnationNumberIsNegative() {
      // when / then
      assertThatThrownBy(
              () ->
                  new PartitionGroupConfiguration(
                      1, -1, Map.of(), Optional.empty(), Optional.empty(), Optional.empty()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenMembersIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new PartitionGroupConfiguration(
                      1, 0, null, Optional.empty(), Optional.empty(), Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenRoutingStateIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new PartitionGroupConfiguration(
                      1, 0, Map.of(), null, Optional.empty(), Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenPendingChangesIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new PartitionGroupConfiguration(
                      1, 0, Map.of(), Optional.empty(), null, Optional.empty()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenLastChangeIsNull() {
      // when / then
      assertThatThrownBy(
              () ->
                  new PartitionGroupConfiguration(
                      1, 0, Map.of(), Optional.empty(), Optional.empty(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class ImmutableCollections {

    @Test
    void shouldDefensivelyCopyMembersInMapConstructor() {
      // given — a mutable map passed to the constructor
      final var mutable = new HashMap<MemberId, BrokerPartitionState>();
      mutable.put(MEMBER_0, broker(1, 1));
      final var config = group(1, mutable);

      // when — the source map is mutated after construction
      mutable.put(MEMBER_1, broker(1, 2));

      // then — the record's view is unaffected
      assertThat(config.members()).containsOnlyKeys(MEMBER_0);
    }

    @Test
    void shouldReturnImmutableMembersMap() {
      // given
      final var config = group(1, Map.of(MEMBER_0, broker(1, 1)));

      // when / then
      assertThatThrownBy(() -> config.members().put(MEMBER_1, broker(1, 2)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class ConfigurationChangeNavigation {

    private static final DeleteHistoryOperation OP_0 = new DeleteHistoryOperation(MEMBER_0);
    private static final DeleteHistoryOperation OP_1 = new DeleteHistoryOperation(MEMBER_1);

    @Test
    void shouldReturnPendingChangeForTargetMemberOnly() {
      // given
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1, 2)))
              .startConfigurationChange(List.of(OP_1));

      // when / then
      assertThat(config.pendingChangesFor(MEMBER_1)).contains(OP_1);
      assertThat(config.pendingChangesFor(MEMBER_0)).isEmpty();
      assertThat(config.nextPendingOperation()).isEqualTo(OP_1);
    }

    @Test
    void shouldReturnEmptyPendingChangeWhenNoChangeInProgress() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when / then
      assertThat(config.pendingChangesFor(MEMBER_0)).isEmpty();
    }

    @Test
    void shouldApplyUpdaterAndCompleteChangeOnLastOperation() {
      // given — a single pending operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1))).startConfigurationChange(List.of(OP_0));
      final long versionAfterStart = config.version();

      // when — the operation completes with an updater that flips the broker mode
      final var advanced =
          config.advanceConfigurationChange(
              c -> c.updateMember(MEMBER_0, b -> b.setMode(Mode.RECOVERING)));

      // then — the updater's effect is visible, the change is completed and the version is bumped
      assertThat(advanced.getMember(MEMBER_0).mode()).isEqualTo(Mode.RECOVERING);
      assertThat(advanced.hasPendingChanges()).isFalse();
      assertThat(advanced.lastChange()).isPresent();
      assertThat(advanced.version()).isEqualTo(versionAfterStart + 1);
    }

    @Test
    void shouldStepPlanWithoutBumpingVersionWhileOperationsRemain() {
      // given — two operations pending
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1))).startConfigurationChange(List.of(OP_0, OP_1));
      final long versionAfterStart = config.version();

      // when
      final var advanced = config.advanceConfigurationChange(UnaryOperator.identity());

      // then
      assertThat(advanced.hasPendingChanges()).isTrue();
      assertThat(advanced.pendingChanges().get().pendingOperations()).containsExactly(OP_1);
      assertThat(advanced.version()).isEqualTo(versionAfterStart);
    }

    @Test
    void shouldCancelPendingChangesBumpingVersionByTwo() {
      // given
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1))).startConfigurationChange(List.of(OP_0));
      final long versionAfterStart = config.version();

      // when
      final var cancelled = config.cancelPendingChanges();

      // then
      assertThat(cancelled.hasPendingChanges()).isFalse();
      assertThat(cancelled.version()).isEqualTo(versionAfterStart + 2);
      assertThat(cancelled.lastChange()).isPresent();
    }

    @Test
    void shouldReturnSameConfigWhenCancellingWithoutPendingChange() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when / then
      assertThat(config.cancelPendingChanges()).isSameAs(config);
    }
  }
}
