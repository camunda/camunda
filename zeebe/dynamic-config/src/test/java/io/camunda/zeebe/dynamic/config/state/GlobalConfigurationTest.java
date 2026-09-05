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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.FixedConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
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
    void shouldMergePendingChangesByUnioningCompletions() {
      // given — same config version, the same plan as two brokers see it: each has completed a
      // different operation of it
      final var builder = OperationGraph.builder();
      final var first = builder.add(new DeleteHistoryOperation(MEMBER_0));
      final var second = builder.add(new DeleteHistoryOperation(MEMBER_1));
      final var plan =
          new DependencyChangePlan(
              1, Status.IN_PROGRESS, Instant.EPOCH, builder.build(), new TreeMap<>());
      final var left =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.empty(),
              Optional.of(
                  new DependencyChangePlan(
                      plan.id(),
                      plan.status(),
                      plan.startedAt(),
                      plan.graph(),
                      new TreeMap<>(Map.of(first, Instant.EPOCH)))),
              Optional.empty());
      final var right =
          new GlobalConfiguration(
              1,
              Optional.empty(),
              Map.of(),
              Optional.empty(),
              Optional.of(
                  new DependencyChangePlan(
                      plan.id(),
                      plan.status(),
                      plan.startedAt(),
                      plan.graph(),
                      new TreeMap<>(Map.of(second, Instant.EPOCH)))),
              Optional.empty());

      // when
      final var merged = left.merge(right);

      // then — both completions survive. Merging by a plan version instead would keep one broker's
      // progress and silently discard the other's, which is what a graph change makes possible:
      // several brokers record against the same plan at the same config version.
      assertThat(merged.pendingChanges().orElseThrow().completed()).containsOnlyKeys(first, second);
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

  @Nested
  class ConfigurationChangeNavigation {

    @Test
    void shouldOfferAnOperationOnlyToTheMemberItNames() {
      // given
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE), MEMBER_1, broker(0, State.LEAVING)))
              .startConfigurationChange(List.of(new MemberLeaveOperation(MEMBER_1)));

      // when / then
      assertThat(config.runnableFor(MEMBER_1).values())
          .containsExactly(new MemberLeaveOperation(MEMBER_1));
      assertThat(config.runnableFor(MEMBER_0)).isEmpty();
    }

    @Test
    void shouldOfferNothingWhenNoChangeIsInProgress() {
      // given
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when / then
      assertThat(config.runnableFor(MEMBER_0)).isEmpty();
    }

    @Test
    void shouldOfferOnlyOneOperationAtATime() {
      // given — two operations for the same member
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE), MEMBER_1, broker(0, State.ACTIVE)))
              .startConfigurationChange(
                  List.of(new MemberJoinOperation(MEMBER_1), new MemberLeaveOperation(MEMBER_1)));

      // when / then — the second waits for the first, because a cluster-wide change is planned as a
      // sequential graph. Offering both at once would run two broker lifecycle steps concurrently,
      // which nothing here has established is safe.
      assertThat(config.runnableFor(MEMBER_1).values())
          .containsExactly(new MemberJoinOperation(MEMBER_1));

      final var first = config.pendingChanges().orElseThrow().graph().operations().firstKey();
      final var afterFirst = config.completeOperation(first, UnaryOperator.identity());
      assertThat(afterFirst.runnableFor(MEMBER_1).values())
          .containsExactly(new MemberLeaveOperation(MEMBER_1));
    }

    @Test
    void shouldRecordAnOperationWithoutBumpingVersionWhileOperationsRemain() {
      // given — two operations pending
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)))
              .startConfigurationChange(
                  List.of(new MemberJoinOperation(MEMBER_1), new MemberLeaveOperation(MEMBER_0)));
      final long versionAfterStart = config.version();
      final var first = config.pendingChanges().orElseThrow().graph().operations().firstKey();

      // when — record the first operation with a no-op updater
      final var advanced = config.completeOperation(first, UnaryOperator.identity());

      // then — one operation remains, and the version has not moved: it must not, or a peer
      // recording the other operation of the same plan would lose its progress on merge
      assertThat(advanced.hasPendingChanges()).isTrue();
      assertThat(advanced.pendingChanges().orElseThrow().pendingOperations())
          .containsExactly(new MemberLeaveOperation(MEMBER_0));
      assertThat(advanced.version()).isEqualTo(versionAfterStart);
    }

    @Test
    void shouldCompleteChangeAndRemoveLeftMembersOnceDrained() {
      // given — MEMBER_1 is leaving; a single operation targets it
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE), MEMBER_1, broker(0, State.LEAVING)))
              .startConfigurationChange(List.of(new MemberLeaveOperation(MEMBER_1)));
      final var only = config.pendingChanges().orElseThrow().graph().operations().firstKey();

      // when — the operation completes and marks MEMBER_1 as LEFT
      final var advanced =
          config
              .completeOperation(only, c -> c.updateMember(MEMBER_1, b -> b.setState(State.LEFT)))
              .completeGraphChangeIfDrained();

      // then — the change is completed, MEMBER_1 is removed, version is bumped, lastChange is set
      assertThat(advanced.hasPendingChanges()).isFalse();
      assertThat(advanced.pendingChanges()).isEmpty();
      assertThat(advanced.hasMember(MEMBER_1)).isFalse();
      assertThat(advanced.hasMember(MEMBER_0)).isTrue();
      assertThat(advanced.version()).isEqualTo(6);
      assertThat(advanced.lastChange()).isPresent();
    }

    @Test
    void shouldConvergeWhenTwoBrokersFinishTheSameDrainedChange() {
      // given — a change with two unordered operations, and a member on its way out. Built with the
      // canonical constructor because startConfigurationChange only builds chains, and what is
      // under test is what happens when two brokers each record a different operation.
      final var builder = OperationGraph.builder();
      final var first = builder.add(new MemberLeaveOperation(MEMBER_1));
      final var second = builder.add(new MemberJoinOperation(MEMBER_0));
      final var config =
          new GlobalConfiguration(
              4,
              Optional.empty(),
              Map.of(MEMBER_0, broker(0, State.ACTIVE), MEMBER_1, broker(0, State.LEAVING)),
              Optional.empty(),
              Optional.of(
                  new DependencyChangePlan(
                      5, Status.IN_PROGRESS, Instant.EPOCH, builder.build(), new TreeMap<>())),
              Optional.empty());

      // when — each broker records one operation, and gossip unions the two
      final var onOneBroker =
          config.completeOperation(
              first, c -> c.updateMember(MEMBER_1, b -> b.setState(State.LEFT)));
      final var onTheOther = config.completeOperation(second, UnaryOperator.identity());
      final var converged = onOneBroker.merge(onTheOther);

      // and — both then observe it drained and finish it independently, with no coordinator
      final var finishedHere = converged.completeGraphChangeIfDrained();
      final var finishedThere = converged.completeGraphChangeIfDrained();

      // then — the two results are identical, which is what makes moving completion off the
      // last-operation broker safe: the record is stamped from the last operation's own completion
      // time, not from each broker's clock, so there is nothing left to disagree about. The LEFT
      // member is pruned on both.
      assertThat(finishedHere).isEqualTo(finishedThere);
      assertThat(finishedHere.hasMember(MEMBER_1)).isFalse();
      assertThat(finishedHere.pendingChanges()).isEmpty();
      assertThat(finishedHere.lastChange()).isPresent();
    }

    @Test
    void shouldLeaveAnUndrainedChangeAlone() {
      // given — two operations, one recorded
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE), MEMBER_1, broker(0, State.ACTIVE)))
              .startConfigurationChange(
                  List.of(new MemberJoinOperation(MEMBER_1), new MemberLeaveOperation(MEMBER_0)));
      final var first = config.pendingChanges().orElseThrow().graph().operations().firstKey();
      final var partial = config.completeOperation(first, UnaryOperator.identity());

      // when / then — every broker calls this on every merge, so it must be a no-op until the
      // change has actually drained
      assertThat(partial.completeGraphChangeIfDrained()).isEqualTo(partial);
    }

    @Test
    void shouldThrowWhenRecordingWithoutPendingChange() {
      // given
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when / then
      assertThatThrownBy(
              () -> config.completeOperation(OperationId.of(0), UnaryOperator.identity()))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCancelPendingChangesBumpingVersionByTwo() {
      // given
      final var config =
          config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)))
              .startConfigurationChange(List.of(new MemberLeaveOperation(MEMBER_0)));
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
      final var config = config(4, Map.of(MEMBER_0, broker(0, State.ACTIVE)));

      // when / then
      assertThat(config.cancelPendingChanges()).isSameAs(config);
    }
  }
}
