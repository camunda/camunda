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
import static org.assertj.core.api.Assertions.entry;

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
  private static final MemberId MEMBER_2 = MemberId.from("2");

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

  private static BrokerPartitionState brokerWithPriorities(
      final Map<Integer, Integer> partitionPriorities) {
    final Map<Integer, PartitionState> partitions = new HashMap<>();
    partitionPriorities.forEach((id, priority) -> partitions.put(id, partition(priority)));
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  private static BrokerPartitionState brokerWith(
      final int partitionId, final PartitionState state) {
    return new BrokerPartitionState(1, Instant.EPOCH, Map.of(partitionId, state), Mode.PROCESSING);
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

  /**
   * Two operations with no edge between them, so both are runnable from the start and either broker
   * can complete its own without waiting — the divergence the merge tests need.
   */
  private static OperationGraph twoIndependentOps() {
    final var builder = OperationGraph.builder();
    builder.add(new DeleteHistoryOperation(MEMBER_0));
    builder.add(new DeleteHistoryOperation(MEMBER_1));
    return builder.build();
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
    void shouldUnionCompletionsOfTheSamePendingChange() {
      // given — same config version, and two brokers that each completed a *different* operation of
      // the same plan: the shape a graph change produces, since several brokers progress it at once
      // and completeOperation deliberately does not move the group version
      final var started = group(1, Map.of()).startGraphConfigurationChange(twoIndependentOps());
      final var leftDid = started.completeOperation(OperationId.of(0), UnaryOperator.identity());
      final var rightDid = started.completeOperation(OperationId.of(1), UnaryOperator.identity());

      // when
      final var merged = leftDid.merge(rightDid);

      // then — both completions survive; neither side's progress is dropped in favour of the
      // receiver's own copy
      assertThat(merged.pendingChanges().orElseThrow().completed())
          .containsOnlyKeys(OperationId.of(0), OperationId.of(1));
      assertThat(merged.hasPendingChanges()).isFalse();
    }

    @Test
    void shouldUnionCompletionsRegardlessOfMergeDirection() {
      // given — the same divergence as above
      final var started = group(1, Map.of()).startGraphConfigurationChange(twoIndependentOps());
      final var leftDid = started.completeOperation(OperationId.of(0), UnaryOperator.identity());
      final var rightDid = started.completeOperation(OperationId.of(1), UnaryOperator.identity());

      // when / then — merge is commutative on completions, so gossip converges whichever broker
      // receives whose state first
      assertThat(leftDid.merge(rightDid).pendingChanges())
          .isEqualTo(rightDid.merge(leftDid).pendingChanges());
    }

    @Test
    void shouldThrowWhenMergingSameChangeIdWithDifferentGraphs() {
      // given — two brokers that each derived plan id 2 (the group version they started from) for a
      // genuinely different graph, which the forced-request path can produce by bypassing the
      // single-coordinator check
      final var left =
          group(1, Map.of())
              .startGraphConfigurationChange(
                  OperationGraph.sequential(List.of(new DeleteHistoryOperation(MEMBER_0))));
      final var right =
          group(1, Map.of())
              .startGraphConfigurationChange(
                  OperationGraph.sequential(List.of(new DeleteHistoryOperation(MEMBER_1))));

      // when / then — refused rather than unioned: operation ids restart at 0 per graph, so a blind
      // union would mark one graph's operation complete because the other's ran
      assertThatThrownBy(() -> left.merge(right)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenMembersConflictAtEqualVersion() {
      // given — same config version, MEMBER_0 at the same per-member version on both sides but
      // with genuinely different content: the shape two brokers applying concurrent, same-member
      // writes under a graph change can produce (see OperationGraph's class javadoc on why the
      // graph model does not protect against this)
      final var left = group(3, Map.of(MEMBER_0, broker(5, 1)));
      final var right = group(3, Map.of(MEMBER_0, broker(5, 2)));

      // when / then — rejected outright by BrokerPartitionState#merge, not silently resolved by
      // picking one; this is the throw the change-view/reporting fixes assume when they describe
      // this as a permanent-non-convergence failure mode rather than a silent one
      assertThatThrownBy(() -> left.merge(right)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> right.merge(left)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldConvergeLastChangeDeterministicallyWhenBrokersMintDifferentTimestamps() {
      // given — same config version, both brokers minted a lastChange for the same completed
      // change (id 7) a moment apart: exactly what two brokers independently running
      // completeGraphChangeIfDrained can produce, since neither is gated by a coordinator
      final var earlier =
          new CompletedChange(
              7, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
      final var later =
          new CompletedChange(
              7, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH.plusSeconds(5));
      final var left =
          new PartitionGroupConfiguration(
              3, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.of(earlier));
      final var right =
          new PartitionGroupConfiguration(
              3, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.of(later));

      // when / then — the earlier timestamp wins regardless of which side is the receiver, so two
      // brokers merging in either order converge on the same value instead of each keeping its own
      assertThat(left.merge(right).lastChange()).contains(earlier);
      assertThat(right.merge(left).lastChange()).contains(earlier);
    }

    @Test
    void shouldPreferHigherChangeIdForLastChangeWhenIdsDiffer() {
      // given — same config version, but the two sides disagree on which change last completed on
      // this group: a genuinely later, unrelated completion on one side, not a re-stamp of the
      // same one
      // The higher id also carries the *earlier* completedAt, so a merge that compared timestamps
      // first and only broke ties on id would pick `older` here and fail. With both stamped at the
      // same instant the two rules are indistinguishable.
      final var older =
          new CompletedChange(
              3, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH.plusSeconds(9));
      final var newer =
          new CompletedChange(
              4, ClusterChangePlan.Status.COMPLETED, Instant.EPOCH, Instant.EPOCH.plusSeconds(2));
      final var left =
          new PartitionGroupConfiguration(
              3, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.of(older));
      final var right =
          new PartitionGroupConfiguration(
              3, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.of(newer));

      // when / then — ids are monotonic, so the higher one is always the newer change
      assertThat(left.merge(right).lastChange()).contains(newer);
      assertThat(right.merge(left).lastChange()).contains(newer);
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

    @Test
    void shouldMergeAvailabilityByItsOwnVersionWhenConfigVersionsAreEqual() {
      // given — same config version, but the right side was disabled more recently
      final var left = group(3, Map.of()).disable(); // availability version 1, disabled
      final var right = group(3, Map.of()).disable().enable(); // availability version 2, enabled

      // when / then — availability's own version decides, independent of member/config state
      assertThat(left.merge(right).isDisabled()).isFalse();
      assertThat(right.merge(left).isDisabled()).isFalse();
    }

    @Test
    void shouldPreserveHigherVersionedAvailabilityAcrossAWholeRecordVersionMismatch() {
      // given — the lower config-version side was disabled more recently than the higher side ever
      // toggled its own availability; a plain top-level-version-wins merge would otherwise silently
      // drop the more recent disable when the higher-config-version side wins wholesale
      final var lower = group(1, Map.of()).disable();
      final var higher = group(2, Map.of());

      // when / then — the higher config version wins for everything else, but the more recently
      // toggled availability (owned independently of the top-level version) survives
      assertThat(lower.merge(higher))
          .usingRecursiveComparison()
          .ignoringFields("availability")
          .isEqualTo(higher);
      assertThat(lower.merge(higher).isDisabled()).isTrue();
      assertThat(higher.merge(lower).isDisabled()).isTrue();
    }
  }

  @Nested
  class Recovering {

    @Test
    void shouldNotBeRecoveringWithNoMembers() {
      // given
      final var config = group(1, Map.of());

      // when / then
      assertThat(config.isRecovering()).isFalse();
    }

    @Test
    void shouldNotBeRecoveringWhenEveryMemberIsProcessing() {
      // given
      final var config = group(1, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1, 1)));

      // when / then
      assertThat(config.isRecovering()).isFalse();
    }

    @Test
    void shouldBeRecoveringWhenAnyMemberIsRecovering() {
      // given
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, broker(1, 1),
                  MEMBER_1, broker(1, 1).setMode(Mode.RECOVERING)));

      // when / then
      assertThat(config.isRecovering()).isTrue();
    }
  }

  @Nested
  class Availability {

    @Test
    void shouldBeEnabledByDefault() {
      // given
      final var config = group(1, Map.of());

      // when / then
      assertThat(config.isDisabled()).isFalse();
    }

    @Test
    void shouldDisableWithoutChangingGroupVersionOrMembers() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when
      final var disabled = config.disable();

      // then
      assertThat(disabled.isDisabled()).isTrue();
      assertThat(disabled.version()).isEqualTo(config.version());
      assertThat(disabled.members()).isEqualTo(config.members());
    }

    @Test
    void shouldReturnSameInstanceWhenAlreadyDisabled() {
      // given
      final var disabled = group(1, Map.of()).disable();

      // when / then
      assertThat(disabled.disable()).isSameAs(disabled);
    }

    @Test
    void shouldReturnSameInstanceWhenAlreadyEnabled() {
      // given
      final var config = group(1, Map.of());

      // when / then
      assertThat(config.enable()).isSameAs(config);
    }

    @Test
    void shouldReEnableWithoutChangingGroupVersionOrMembers() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));
      final var disabled = config.disable();

      // when
      final var reEnabled = disabled.enable();

      // then
      assertThat(reEnabled.isDisabled()).isFalse();
      assertThat(reEnabled.version()).isEqualTo(config.version());
      assertThat(reEnabled.members()).isEqualTo(config.members());
    }

    /**
     * Unlike {@code disable()}/{@code enable()}, removal clears the old assignment rather than
     * preserving it; see {@link PartitionGroupConfiguration#remove()}.
     */
    @Test
    void shouldRemoveClearingMembersWithoutChangingGroupVersion() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1))).disable();

      // when
      final var removed = config.remove();

      // then
      assertThat(removed.isRemoved()).isTrue();
      assertThat(removed.isDisabled()).describedAs("a removed tenant stays disabled").isTrue();
      assertThat(removed.version()).isEqualTo(config.version());
      assertThat(removed.members()).isEmpty();
    }

    @Test
    void shouldReturnSameInstanceWhenAlreadyRemoved() {
      // given
      final var removed = group(1, Map.of(MEMBER_0, broker(1, 1))).disable().remove();

      // when / then
      assertThat(removed.remove()).isSameAs(removed);
    }
  }

  @Nested
  class StartConfigurationChange {

    private static final OperationGraph ONE_OP =
        OperationGraph.sequential(List.of(new DeleteHistoryOperation(MEMBER_0)));

    @Test
    void shouldSetPendingChangesAndIncrementVersion() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when
      final var updated = config.startGraphConfigurationChange(ONE_OP);

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
      final var updated = config.startGraphConfigurationChange(ONE_OP);

      // then
      assertThat(updated.pendingChanges().get().id()).isEqualTo(5);
    }

    @Test
    void shouldThrowWhenChangeAlreadyInProgress() {
      // given
      final var config = group(4, Map.of()).startGraphConfigurationChange(ONE_OP);

      // when / then
      assertThatThrownBy(() -> config.startGraphConfigurationChange(ONE_OP))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenPreviousChangeIsDrainedButNotYetCleared() {
      // given — every operation has completed, but completeGraphChangeIfDrained has not run yet, so
      // the plan is still there with a lastChange to record and members to prune
      final var drained =
          group(4, Map.of())
              .startGraphConfigurationChange(ONE_OP)
              .completeOperation(OperationId.of(0), UnaryOperator.identity());
      assertThat(drained.hasPendingChanges()).isFalse();

      // when / then — refused on the plan's presence, not its content: starting here would discard
      // that unfinished bookkeeping
      assertThatThrownBy(() -> drained.startGraphConfigurationChange(ONE_OP))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenNoOperations() {
      // given
      final var config = group(4, Map.of());

      // when / then
      assertThatThrownBy(
              () -> config.startGraphConfigurationChange(OperationGraph.sequential(List.of())))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class CompleteChange {

    private static final DeleteHistoryOperation OP_1 = new DeleteHistoryOperation(MEMBER_0);
    private static final DeleteHistoryOperation OP_2 = new DeleteHistoryOperation(MEMBER_1);
    private static final OperationId ID_1 = OperationId.of(0);
    private static final OperationId ID_2 = OperationId.of(1);

    @Test
    void shouldReturnSameConfigWhenNoPendingChange() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when / then — unguarded on purpose: every broker calls this on every merge, so a group with
      // nothing running has to be a no-op rather than a throw
      assertThat(config.completeGraphChangeIfDrained()).isSameAs(config);
    }

    @Test
    void shouldRecordCompletionWithoutMovingVersionWhileOperationsRemain() {
      // given — a plan with two operations, the second behind the first
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1, OP_2)));
      final var versionAfterStart = config.version();

      // when
      final var advanced =
          config.completeOperation(ID_1, UnaryOperator.identity()).completeGraphChangeIfDrained();

      // then — the change is still pending and the version has not moved, so a peer's concurrent
      // progress still merges structurally instead of being overwritten wholesale
      assertThat(advanced.hasPendingChanges()).isTrue();
      assertThat(advanced.pendingChanges().orElseThrow().pendingOperations()).containsExactly(OP_2);
      assertThat(advanced.version()).isEqualTo(versionAfterStart);
    }

    @Test
    void shouldCompleteChangeWhenLastOperationIsRecorded() {
      // given — a plan with a single operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1)));
      final var planId = config.pendingChanges().orElseThrow().id();
      final var versionAfterStart = config.version();

      // when
      final var advanced =
          config.completeOperation(ID_1, UnaryOperator.identity()).completeGraphChangeIfDrained();

      // then — pending changes are cleared, the completed change is recorded, version is bumped
      assertThat(advanced.hasPendingChanges()).isFalse();
      assertThat(advanced.pendingChanges()).isEmpty();
      assertThat(advanced.lastChange()).isPresent();
      assertThat(advanced.lastChange().orElseThrow().id()).isEqualTo(planId);
      assertThat(advanced.version()).isEqualTo(versionAfterStart + 1);
    }

    @Test
    void shouldRemoveMembersWithEmptyPartitionsOnCompletion() {
      // given — MEMBER_0 still hosts partition 1, MEMBER_1 hosts none; a single-op plan
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1)));

      // when
      final var advanced =
          config.completeOperation(ID_1, UnaryOperator.identity()).completeGraphChangeIfDrained();

      // then — on completion the member with no partitions is removed, the other is kept
      assertThat(advanced.members()).containsOnlyKeys(MEMBER_0);
      assertThat(advanced.hasMember(MEMBER_1)).isFalse();
    }

    @Test
    void shouldNotRemoveEmptyMembersWhileOperationsRemain() {
      // given — MEMBER_1 hosts no partitions, but the plan still has a pending operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1, OP_2)));

      // when
      final var advanced =
          config.completeOperation(ID_1, UnaryOperator.identity()).completeGraphChangeIfDrained();

      // then — members are untouched until the whole change is done
      assertThat(advanced.members()).containsOnlyKeys(MEMBER_0, MEMBER_1);
    }

    @Test
    void shouldStampCompletionWithTheLastOperationRatherThanTheWallClock() {
      // given — a drained two-operation plan
      final var drained =
          group(4, Map.of())
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1, OP_2)))
              .completeOperation(ID_1, UnaryOperator.identity())
              .completeOperation(ID_2, UnaryOperator.identity());
      final var lastCompletion = drained.pendingChanges().orElseThrow().completed().get(ID_2);

      // when
      final var completed = drained.completeGraphChangeIfDrained();

      // then — two brokers observing the drain a moment apart both derive this same value from the
      // plan, instead of each stamping its own "now" and never converging
      assertThat(completed.lastChange().orElseThrow().completedAt()).isEqualTo(lastCompletion);
    }

    @Test
    void shouldThrowWhenRecordingAnOperationStillWaitingOnItsDependency() {
      // given — OP_2 sits behind OP_1 in a sequential graph
      final var config =
          group(4, Map.of())
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_1, OP_2)));

      // when / then
      assertThatThrownBy(() -> config.completeOperation(ID_2, UnaryOperator.identity()))
          .isInstanceOf(IllegalStateException.class);
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
    void shouldOfferOnlyTheOperationsTargetingTheGivenMember() {
      // given — two independent operations, one per member, so neither is blocked by the other
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1, 2)))
              .startGraphConfigurationChange(twoIndependentOps());

      // when / then — each member sees its own operation and nothing else; the graph offers both at
      // once precisely because there is no edge between them
      assertThat(config.runnableFor(MEMBER_0)).containsExactly(entry(OperationId.of(0), OP_0));
      assertThat(config.runnableFor(MEMBER_1)).containsExactly(entry(OperationId.of(1), OP_1));
      assertThat(config.runnableFor(MEMBER_2)).isEmpty();
    }

    @Test
    void shouldOfferNothingRunnableWhenNoChangeInProgress() {
      // given
      final var config = group(4, Map.of(MEMBER_0, broker(1, 1)));

      // when / then
      assertThat(config.runnableFor(MEMBER_0)).isEmpty();
    }

    @Test
    void shouldNotOfferAnOperationWhoseDependencyHasNotCompleted() {
      // given — OP_1 sits behind OP_0, and both target their own member
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1), MEMBER_1, broker(1, 2)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_0, OP_1)));

      // when / then — MEMBER_1's operation stays hidden until MEMBER_0's has been recorded
      assertThat(config.runnableFor(MEMBER_1)).isEmpty();
      final var afterFirst = config.completeOperation(OperationId.of(0), UnaryOperator.identity());
      assertThat(afterFirst.runnableFor(MEMBER_1)).containsExactly(entry(OperationId.of(1), OP_1));
    }

    @Test
    void shouldApplyUpdaterAndCompleteChangeOnLastOperation() {
      // given — a single pending operation
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_0)));
      final long versionAfterStart = config.version();

      // when — the operation completes with an updater that flips the broker mode
      final var advanced =
          config
              .completeOperation(
                  OperationId.of(0), c -> c.updateMember(MEMBER_0, b -> b.setMode(Mode.RECOVERING)))
              .completeGraphChangeIfDrained();

      // then — the updater's effect is visible, the change is completed and the version is bumped
      assertThat(advanced.getMember(MEMBER_0).mode()).isEqualTo(Mode.RECOVERING);
      assertThat(advanced.hasPendingChanges()).isFalse();
      assertThat(advanced.lastChange()).isPresent();
      assertThat(advanced.version()).isEqualTo(versionAfterStart + 1);
    }

    @Test
    void shouldCancelPendingChangesBumpingVersionByTwo() {
      // given
      final var config =
          group(4, Map.of(MEMBER_0, broker(1, 1)))
              .startGraphConfigurationChange(OperationGraph.sequential(List.of(OP_0)));
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

  @Nested
  class DesiredLeader {

    @Test
    void shouldReturnHighestPriorityBrokerAsDesiredLeader() {
      // given
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWithPriorities(Map.of(1, 1)),
                  MEMBER_1, brokerWithPriorities(Map.of(1, 3)),
                  MEMBER_2, brokerWithPriorities(Map.of(1, 2))));

      // when / then
      assertThat(config.getDesiredLeader(1)).contains(MEMBER_1);
    }

    @Test
    void shouldReturnEmptyWhenNoBrokerReplicatesPartition() {
      // given
      final var config = group(1, Map.of(MEMBER_0, brokerWithPriorities(Map.of(1, 1))));

      // when / then
      assertThat(config.getDesiredLeader(2)).isEmpty();
    }

    @Test
    void shouldBreakPriorityTiesDeterministicallyByMemberId() {
      // given
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_1, brokerWithPriorities(Map.of(1, 5)),
                  MEMBER_0, brokerWithPriorities(Map.of(1, 5))));

      // when / then
      assertThat(config.getDesiredLeader(1)).contains(MEMBER_0);
    }

    @Test
    void shouldDeriveDesiredLeaderPerPartition() {
      // given
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWithPriorities(Map.of(1, 3, 2, 1)),
                  MEMBER_1, brokerWithPriorities(Map.of(1, 1, 2, 3))));

      // when / then
      assertThat(config.desiredLeaders())
          .containsExactly(Map.entry(1, MEMBER_0), Map.entry(2, MEMBER_1));
    }

    @Test
    void shouldReturnEmptyDesiredLeadersWhenGroupHasNoPartitions() {
      // given
      final var config = group(1, Map.of());

      // when / then
      assertThat(config.desiredLeaders()).isEmpty();
    }

    @Test
    void shouldSkipALearnerEvenWithTheHighestPriority() {
      // given — a learner cannot vote and can therefore never actually lead; it must not be
      // reported as the desired leader even though it outranks the only real candidate
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0,
                  brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                  brokerWith(
                      1, PartitionState.joining(9, DynamicPartitionConfig.init()).toLearner())));

      // when / then
      assertThat(config.getDesiredLeader(1)).contains(MEMBER_0);
    }

    @Test
    void shouldSkipALeavingMemberEvenWithTheHighestPriority() {
      // given — a member on its way out must not be handed the leadership it is about to give up
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0,
                  brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                  brokerWith(
                      1, PartitionState.active(9, DynamicPartitionConfig.init()).toLeaving())));

      // when / then
      assertThat(config.getDesiredLeader(1)).contains(MEMBER_0);
    }

    @Test
    void shouldTreatARecoveringMemberAsAValidCandidate() {
      // given — recovery only pauses stream processing, it does not affect raft voting rights
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0,
                  brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                  brokerWith(
                      1, PartitionState.active(9, DynamicPartitionConfig.init()).toRecovering())));

      // when / then
      assertThat(config.getDesiredLeader(1)).contains(MEMBER_1);
    }

    @Test
    void shouldReturnEmptyWhenOnlyALearnerReplicatesPartition() {
      // given — no member is currently eligible to lead
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0,
                  brokerWith(
                      1, PartitionState.joining(1, DynamicPartitionConfig.init()).toLearner())));

      // when / then
      assertThat(config.getDesiredLeader(1)).isEmpty();
    }
  }

  @Nested
  class PrimaryForPartition {

    @Test
    void shouldReturnHighestPriorityActiveBroker() {
      // given
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWithPriorities(Map.of(1, 1)),
                  MEMBER_1, brokerWithPriorities(Map.of(1, 3)),
                  MEMBER_2, brokerWithPriorities(Map.of(1, 2))));

      // when / then
      assertThat(config.getPrimaryForPartition(1)).contains(MEMBER_1);
    }

    @Test
    void shouldSkipALearnerEvenWithTheHighestPriority() {
      // given — a learner cannot vote and can therefore never actually lead; it must not be
      // reported as primary even though it outranks the only real candidate
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                      brokerWith(
                          1,
                          PartitionState.joining(9, DynamicPartitionConfig.init()).toLearner())));

      // when / then
      assertThat(config.getPrimaryForPartition(1)).contains(MEMBER_0);
    }

    @Test
    void shouldSkipALeavingMemberEvenWithTheHighestPriority() {
      // given — a member on its way out must not be handed back as the (future) primary
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                      brokerWith(
                          1, PartitionState.active(9, DynamicPartitionConfig.init()).toLeaving())));

      // when / then
      assertThat(config.getPrimaryForPartition(1)).contains(MEMBER_0);
    }

    @Test
    void shouldTreatARecoveringMemberAsAValidCandidate() {
      // given — recovery only pauses stream processing, it does not affect raft voting rights
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0, brokerWith(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                  MEMBER_1,
                      brokerWith(
                          1,
                          PartitionState.active(9, DynamicPartitionConfig.init()).toRecovering())));

      // when / then
      assertThat(config.getPrimaryForPartition(1)).contains(MEMBER_1);
    }

    @Test
    void shouldReturnEmptyWhenNoBrokerReplicatesPartition() {
      // given
      final var config = group(1, Map.of(MEMBER_0, brokerWithPriorities(Map.of(1, 1))));

      // when / then
      assertThat(config.getPrimaryForPartition(2)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenOnlyALearnerReplicatesPartition() {
      // given — no member is currently eligible to be primary
      final var config =
          group(
              1,
              Map.of(
                  MEMBER_0,
                  brokerWith(
                      1, PartitionState.joining(1, DynamicPartitionConfig.init()).toLearner())));

      // when / then
      assertThat(config.getPrimaryForPartition(1)).isEmpty();
    }
  }
}
