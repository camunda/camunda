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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.OperationGraph.PlannedOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Concurrency in this model is the absence of a dependency edge, so these tests are about which
 * operations become runnable and when — there is no index to advance and no barrier to release.
 */
final class DependencyChangePlanTest {

  private static final long PLAN_ID = 7L;

  private static MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }

  /** A partition operation, so that different partitions of one member have disjoint write sets. */
  private static ClusterConfigurationChangeOperation op(final int memberId, final int partitionId) {
    return new PartitionPreRestoreOperation(member(memberId), partitionId);
  }

  @Nested
  class Runnability {

    @Test
    void shouldMakeEveryUnblockedOperationRunnableAtOnce() {
      // given — three brokers with nothing depending on anything
      final var builder = OperationGraph.builder();
      builder.add(op(0, 1));
      builder.add(op(1, 1));
      builder.add(op(2, 1));
      final var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // when / then — every broker is eligible immediately; that is broker parallelism, with no
      // container expressing it
      assertThat(plan.runnableFor(member(0))).hasSize(1);
      assertThat(plan.runnableFor(member(1))).hasSize(1);
      assertThat(plan.runnableFor(member(2))).hasSize(1);
    }

    @Test
    void shouldOfferSeveralPartitionsOfOneBrokerAtOnce() {
      // given — one broker, three partitions, no edges between them
      final var builder = OperationGraph.builder();
      builder.add(op(0, 1));
      builder.add(op(0, 2));
      builder.add(op(0, 3));
      final var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // when / then — partition parallelism falls out of the same absence of edges, with no extra
      // level of nesting and no second eligibility tier
      assertThat(plan.runnableFor(member(0))).hasSize(3);
    }

    @Test
    void shouldNotOfferAnOperationUntilItsDependencyCompletes() {
      // given — the shape of bootstrap-then-join: different members, so disjoint write sets, but a
      // real ordering requirement that only the declared edge captures
      final var builder = OperationGraph.builder();
      final var bootstrap = builder.add(op(0, 1));
      final var join = builder.add(op(1, 1), Set.of(bootstrap));
      var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // then — member 1 waits
      assertThat(plan.runnableFor(member(0))).containsOnlyKeys(bootstrap);
      assertThat(plan.runnableFor(member(1))).isEmpty();
      assertThat(plan.blockedBy().get(join)).containsExactly(bootstrap);

      // when — the bootstrap completes
      plan = plan.completeOperation(bootstrap);

      // then — the join becomes runnable, and the bootstrap is no longer offered
      assertThat(plan.runnableFor(member(0))).isEmpty();
      assertThat(plan.runnableFor(member(1))).containsOnlyKeys(join);
      assertThat(plan.blockedBy().get(join)).isEmpty();
    }

    @Test
    void shouldWaitForEveryDependencyNotJustOne() {
      // given — a join-point: one operation gathering several predecessors, which is how a barrier
      // is expressed without a barrier primitive
      final var builder = OperationGraph.builder();
      final var first = builder.add(op(0, 1));
      final var second = builder.add(op(1, 1));
      final var after = builder.add(op(2, 1), Set.of(first, second));
      var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // when — only one predecessor completes
      plan = plan.completeOperation(first);

      // then
      assertThat(plan.isRunnable(after)).isFalse();
      assertThat(plan.blockedBy().get(after)).containsExactly(second);

      // when — the other completes too
      plan = plan.completeOperation(second);

      // then
      assertThat(plan.isRunnable(after)).isTrue();
    }

    @Test
    void shouldRefuseToCompleteAnOperationThatIsStillBlocked() {
      // given
      final var builder = OperationGraph.builder();
      final var first = builder.add(op(0, 1));
      final var second = builder.add(op(1, 1), Set.of(first));
      final var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // when / then — a broker must never be able to record work it was not yet allowed to start
      assertThatThrownBy(() -> plan.completeOperation(second))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("still waiting on");
    }

    @Test
    void shouldTreatCompletingTwiceAsANoOp() {
      // given — appliers are idempotent and may be retried, so a repeat completion must not throw
      final var builder = OperationGraph.builder();
      final var only = builder.add(op(0, 1));
      final var plan = DependencyChangePlan.init(PLAN_ID, builder.build()).completeOperation(only);

      // when / then
      assertThat(plan.completeOperation(only)).isEqualTo(plan);
    }
  }

  @Nested
  class SequentialEquivalence {

    @Test
    void shouldOfferOnlyOneOperationAtATime() {
      // given — the helper that reproduces today's behaviour for an unmigrated transformer
      final var plan =
          DependencyChangePlan.sequential(PLAN_ID, List.of(op(0, 1), op(1, 1), op(2, 1)));

      // when / then — exactly one broker eligible, as before this change
      assertThat(plan.runnableFor(member(0))).hasSize(1);
      assertThat(plan.runnableFor(member(1))).isEmpty();
      assertThat(plan.runnableFor(member(2))).isEmpty();
    }

    @Test
    void shouldReportPendingAndCompletedInPlanOrder() {
      // given
      var plan = DependencyChangePlan.sequential(PLAN_ID, List.of(op(0, 1), op(1, 1), op(2, 1)));

      // when
      plan = plan.completeOperation(OperationId.of(0));

      // then
      assertThat(plan.completedOperations())
          .extracting(CompletedOperation::operation)
          .containsExactly(op(0, 1));
      assertThat(plan.pendingOperations()).containsExactly(op(1, 1), op(2, 1));
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldRejectACycle() {
      // given — a cycle would leave every operation in it permanently un-runnable and the plan
      // would stall with no error at all, so it must not be constructible
      final var operations = new TreeMap<OperationId, PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new PlannedOperation(op(0, 1), new java.util.TreeSet<>(Set.of(OperationId.of(1)))));
      operations.put(
          OperationId.of(1),
          new PlannedOperation(op(1, 1), new java.util.TreeSet<>(Set.of(OperationId.of(0)))));

      // when / then
      assertThatThrownBy(() -> DependencyChangePlan.init(PLAN_ID, OperationGraph.of(operations)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cycle");
    }

    @Test
    void shouldRejectADependencyOnAnOperationOutsideThePlan() {
      // given
      final var operations = new TreeMap<OperationId, PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new PlannedOperation(op(0, 1), new java.util.TreeSet<>(Set.of(OperationId.of(99)))));

      // when / then
      assertThatThrownBy(() -> DependencyChangePlan.init(PLAN_ID, OperationGraph.of(operations)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not part of the graph");
    }

    @Test
    void shouldRejectACompletionWhoseDependenciesAreNotComplete() {
      // given — a two-operation chain where only the dependent operation is marked complete. No
      // execution path produces this, but a decoded or hand-built plan can, and blockedBy() would
      // then report the first operation as still to run after the second already has.
      final var builder = OperationGraph.builder();
      final var first = builder.add(op(0, 1));
      final var second = builder.add(op(0, 2), Set.of(first));
      final var graph = builder.build();

      // when / then
      assertThatThrownBy(
              () ->
                  new DependencyChangePlan(
                      PLAN_ID,
                      Status.IN_PROGRESS,
                      Instant.now(),
                      graph,
                      new TreeMap<>(Map.of(second, Instant.now()))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("the operations it depends on are not");
    }
  }

  @Nested
  class Completion {

    @Test
    void shouldStayPendingUntilEveryOperationIsDone() {
      // given
      final var builder = OperationGraph.builder();
      final var first = builder.add(op(0, 1));
      final var second = builder.add(op(1, 1));
      var plan = DependencyChangePlan.init(PLAN_ID, builder.build());

      // when / then
      assertThat(plan.hasPendingChanges()).isTrue();
      plan = plan.completeOperation(first);
      assertThat(plan.hasPendingChanges()).isTrue();
      plan = plan.completeOperation(second);
      assertThat(plan.hasPendingChanges()).isFalse();
    }

    @Test
    void shouldDeriveTheCompletionTimestampFromTheLastOperation() {
      // given — any broker may observe the plan complete, so the timestamp has to be a function of
      // converged state. A wall-clock read would give two brokers values that never reconcile,
      // because neither sub-configuration merge reconciles lastChange at equal versions.
      final var builder = OperationGraph.builder();
      final var first = builder.add(op(0, 1));
      final var second = builder.add(op(1, 1));
      final var plan =
          DependencyChangePlan.init(PLAN_ID, builder.build())
              .completeOperation(first)
              .completeOperation(second);

      // when — two brokers each derive the completed change independently
      final var byOne = plan.toCompletedChange();
      final var byAnother = plan.toCompletedChange();

      // then
      assertThat(byOne).isEqualTo(byAnother);
      assertThat(byOne.completedAt()).isEqualTo(plan.completed().get(second));
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldKeepBothBrokersCompletionsWhenTheyMeet() {
      // given — two brokers that each ran their own operation and know nothing of the other's
      final var builder = OperationGraph.builder();
      final var mine = builder.add(op(0, 1));
      final var theirs = builder.add(op(1, 1));
      final var base = DependencyChangePlan.init(PLAN_ID, builder.build());
      final var byOne = base.completeOperation(mine);
      final var byAnother = base.completeOperation(theirs);

      // when
      final var merged = byOne.merge(byAnother);

      // then — neither completion is lost, which is the property the whole design rests on
      assertThat(merged.isComplete(mine)).isTrue();
      assertThat(merged.isComplete(theirs)).isTrue();
      assertThat(merged.hasPendingChanges()).isFalse();
    }

    @Test
    void shouldRejectMergingSameIdDifferentGraphs() {
      // given — two self-believed coordinators derive the same plan id (the enclosing
      // sub-configuration's version) for two unrelated graphs; OperationGraph.Builder numbers
      // operations 0..n-1 in both, so the ids collide even though the operations do not
      final var builderA = OperationGraph.builder();
      final var opA = builderA.add(op(0, 1));
      final var planA = DependencyChangePlan.init(PLAN_ID, builderA.build()).completeOperation(opA);

      final var builderB = OperationGraph.builder();
      builderB.add(op(1, 1));
      final var planB = DependencyChangePlan.init(PLAN_ID, builderB.build());

      // when / then — merged blindly, planB would end up with opA (a different member's operation
      // under the same id) marked complete despite never having run
      assertThatThrownBy(() -> planA.merge(planB))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("different graphs");
    }

    @Test
    void shouldBeCommutativeAndIdempotent() {
      // given
      final var builder = OperationGraph.builder();
      final var mine = builder.add(op(0, 1));
      final var theirs = builder.add(op(1, 1));
      final var base = DependencyChangePlan.init(PLAN_ID, builder.build());
      final var byOne = base.completeOperation(mine);
      final var byAnother = base.completeOperation(theirs);

      // when / then
      assertThat(byOne.merge(byAnother)).isEqualTo(byAnother.merge(byOne));
      assertThat(byOne.merge(byOne)).isEqualTo(byOne);
      assertThat(byOne.merge(byAnother).merge(byAnother)).isEqualTo(byOne.merge(byAnother));
    }
  }

  /**
   * The restore graph, which is the first intended adopter. Written out here because it is the
   * clearest demonstration that declared edges express more parallelism than a barrier can: under a
   * barrier between the pre-restore and restore phases, no broker may begin restoring until every
   * broker has finished pre-restoring everything.
   */
  @Nested
  class RestoreShape {

    /** preRestore(m,p) → restore(m,p); modeChange(m) after all of m's restores. */
    private DependencyChangePlan restorePlan(final int brokers, final int partitionsPerBroker) {
      final var builder = OperationGraph.builder();
      for (int m = 0; m < brokers; m++) {
        final var restoresOfMember = new java.util.HashSet<OperationId>();
        for (int p = 1; p <= partitionsPerBroker; p++) {
          final var pre = builder.add(new PartitionPreRestoreOperation(member(m), p));
          restoresOfMember.add(
              builder.add(
                  new PartitionRestoreOperation(member(m), p, new java.util.TreeSet<>(Set.of(1L))),
                  Set.of(pre)));
        }
        builder.add(new ModeChangeOperation(member(m), Mode.PROCESSING), restoresOfMember);
      }
      return DependencyChangePlan.init(PLAN_ID, builder.build());
    }

    @Test
    void shouldOfferEveryBrokerEveryPartitionUpFront() {
      // given — three brokers, two partitions each
      final var plan = restorePlan(3, 2);

      // when / then — all three axes at once: three brokers, two partitions each, six operations
      // runnable immediately, with no container saying so
      assertThat(plan.runnableFor(member(0))).hasSize(2);
      assertThat(plan.runnableFor(member(1))).hasSize(2);
      assertThat(plan.runnableFor(member(2))).hasSize(2);
    }

    @Test
    void shouldLetOneBrokerRestoreWhileAnotherIsStillPreRestoring() {
      // given — broker 0 finishes only its own first pre-restore
      var plan = restorePlan(2, 2);
      final var brokerZeroPreRestores = plan.runnableFor(member(0));
      plan = plan.completeOperation(brokerZeroPreRestores.firstKey());

      // then — broker 0's matching restore is already runnable, even though broker 1 has not
      // pre-restored anything. A barrier between the two phases would have blocked this; the edge
      // is per (member, partition), so nothing false is being waited on.
      assertThat(plan.runnableFor(member(0)))
          .as("its own restore, plus its other pre-restore")
          .hasSize(2);
      assertThat(plan.runnableFor(member(1))).hasSize(2);
    }

    @Test
    void shouldHoldTheJoinPointUntilAllOfThatBrokersRestoresAreDone() {
      // given — the member-wide operation gathering that member's partition work
      var plan = restorePlan(1, 2);

      // when — everything except the last restore completes
      for (final var runnable : List.copyOf(plan.runnableFor(member(0)).keySet())) {
        plan = plan.completeOperation(runnable);
      }
      final var remaining = List.copyOf(plan.runnableFor(member(0)).keySet());
      plan = plan.completeOperation(remaining.get(0));

      // then — the mode change is still waiting on the other restore
      assertThat(plan.hasPendingChanges()).isTrue();
      assertThat(plan.runnableFor(member(0))).hasSize(1);
    }
  }

  @Nested
  class GraphValidation {

    @Test
    void shouldFlattenIntoDependencyOrderRatherThanIdOrder() {
      // given — a graph whose ids run against its edges: operation 0 waits for operation 1. The
      // builder cannot produce this (it only lets an operation depend on ids already issued), but a
      // graph decoded from the wire carries whatever ids the sender wrote.
      final var operations = new TreeMap<OperationId, PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new PlannedOperation(
              new UpdateIncarnationNumberOperation(member(0)),
              new java.util.TreeSet<>(Set.of(OperationId.of(1)))));
      operations.put(
          OperationId.of(1),
          new PlannedOperation(
              new UpdateIncarnationNumberOperation(member(1)), new java.util.TreeSet<>()));
      final var plan = DependencyChangePlan.init(PLAN_ID, OperationGraph.of(operations));

      // when / then — the queue a broker without the graph model executes head-first must respect
      // the edge. Id order would run operation 0 before the operation it waits for.
      assertThat(ClusterChangePlan.flatten(plan).pendingOperations())
          .containsExactly(
              new UpdateIncarnationNumberOperation(member(1)),
              new UpdateIncarnationNumberOperation(member(0)));
    }

    @Test
    void shouldOrderOnlyWhatIsOutstandingWhenFlattening() {
      // given — a chain whose first operation has completed, so its edge no longer constrains
      final var builder = OperationGraph.builder();
      final var first = builder.add(new UpdateIncarnationNumberOperation(member(0)));
      builder.add(new UpdateIncarnationNumberOperation(member(1)), Set.of(first));
      final var plan =
          new DependencyChangePlan(
              PLAN_ID,
              Status.IN_PROGRESS,
              Instant.EPOCH,
              builder.build(),
              new TreeMap<>(Map.of(first, Instant.EPOCH)));

      // when / then — a completed dependency is already satisfied, so the remaining operation is
      // runnable and appears alone
      assertThat(ClusterChangePlan.flatten(plan).pendingOperations())
          .containsExactly(new UpdateIncarnationNumberOperation(member(1)));
    }

    @Test
    void shouldDefaultAnOperationsTargetToTheEnclosingSubConfiguration() {
      // given / when — every adopter today builds a graph that lives inside the sub-configuration
      // it acts on, so no node names a group
      final var builder = OperationGraph.builder();
      builder.add(new UpdateIncarnationNumberOperation(member(0)));

      // then — absent, which is what makes adding the field cost no existing call site
      assertThat(builder.build().operations().values())
          .allSatisfy(planned -> assertThat(planned.groupId()).isEmpty());
    }

    @Test
    void shouldLetANodeNameThePartitionGroupItTargets() {
      // given — the prerequisite for collapsing phase boundaries into the graph: a graph spanning
      // sub-configurations, where a node's target is not implied by where the graph is stored
      final var builder = OperationGraph.builder();
      final var inTenantA =
          builder.add(
              new UpdateIncarnationNumberOperation(member(0)), Set.of(), Optional.of("tenant-a"));
      final var inTenantB =
          builder.add(
              new UpdateIncarnationNumberOperation(member(0)),
              Set.of(inTenantA),
              Optional.of("tenant-b"));

      // when
      final var graph = builder.build();

      // then — both nodes carry their own target, and the edge crosses between them
      assertThat(graph.operations().get(inTenantA).groupId()).contains("tenant-a");
      assertThat(graph.operations().get(inTenantB).groupId()).contains("tenant-b");
      assertThat(graph.operations().get(inTenantB).dependsOn()).containsExactly(inTenantA);
    }

    @Test
    void shouldRejectAClusterWideOperationTargetingAGroup() {
      // given / when / then — a cluster-wide operation has no group to target, so pairing one with
      // a group id is a planning mistake. Rejected rather than ignored: silently dropping the id
      // would let a graph claim a target it does not act on.
      assertThatThrownBy(
              () ->
                  new PlannedOperation(
                      new MemberJoinOperation(member(0)),
                      new java.util.TreeSet<>(),
                      Optional.of("tenant-a")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("targets no partition group");
    }

    @Test
    void shouldRejectADependencyCycle() {
      // given — a cycle would leave every operation in it permanently un-runnable and the change
      // would stall with no error at all, so it is rejected at construction
      final var operations = new java.util.TreeMap<OperationId, OperationGraph.PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new OperationGraph.PlannedOperation(
              new UpdateIncarnationNumberOperation(member(0)),
              new java.util.TreeSet<>(Set.of(OperationId.of(1)))));
      operations.put(
          OperationId.of(1),
          new OperationGraph.PlannedOperation(
              new UpdateIncarnationNumberOperation(member(1)),
              new java.util.TreeSet<>(Set.of(OperationId.of(0)))));

      // when / then
      assertThatThrownBy(() -> OperationGraph.of(operations))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("dependency cycle");
    }

    @Test
    void shouldRejectADependencyOnAnOperationOutsideTheGraph() {
      // given / when / then — an edge to an id the graph does not contain can never be satisfied
      final var operations = new java.util.TreeMap<OperationId, OperationGraph.PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new OperationGraph.PlannedOperation(
              new UpdateIncarnationNumberOperation(member(0)),
              new java.util.TreeSet<>(Set.of(OperationId.of(7)))));

      assertThatThrownBy(() -> OperationGraph.of(operations))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not part of the graph");
    }

    @Test
    void shouldAcceptAnyAcyclicGraphWithoutJudgingItsEdges() {
      // given — two operations writing the same member with no edge between them. This is very
      // likely a bug in whichever transformer produced it, and nothing here rejects it: the graph
      // validates that it can execute, not that executing it is correct. See OperationGraph's
      // javadoc — the edges are the author's responsibility.
      final var builder = OperationGraph.builder();
      builder.add(new UpdateIncarnationNumberOperation(member(0)));
      builder.add(new UpdateIncarnationNumberOperation(member(0)));

      // when / then — accepted, and both offered at once
      final var plan = DependencyChangePlan.init(PLAN_ID, builder.build());
      assertThat(plan.runnableFor(member(0))).hasSize(2);
    }

    @Test
    void shouldRejectAnEmptyGraphEvenThroughTheCanonicalConstructor() {
      // given / when / then — of() is not the only way to build one of these; the canonical
      // constructor must reject what of() rejects, or callers that bypass it (a decode path, a
      // direct `new`) could produce a graph with no timestamp to derive a completion from.
      assertThatThrownBy(() -> new OperationGraph(new java.util.TreeMap<>()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at least one operation");
    }

    @Test
    void shouldRejectADependencyCycleEvenThroughTheCanonicalConstructor() {
      // given — same cycle as shouldRejectADependencyCycle, built through `new` instead of of()
      final var operations = new java.util.TreeMap<OperationId, OperationGraph.PlannedOperation>();
      operations.put(
          OperationId.of(0),
          new OperationGraph.PlannedOperation(
              new UpdateIncarnationNumberOperation(member(0)),
              new java.util.TreeSet<>(Set.of(OperationId.of(1)))));
      operations.put(
          OperationId.of(1),
          new OperationGraph.PlannedOperation(
              new UpdateIncarnationNumberOperation(member(1)),
              new java.util.TreeSet<>(Set.of(OperationId.of(0)))));

      // when / then
      assertThatThrownBy(() -> new OperationGraph(operations))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("dependency cycle");
    }

    @Test
    void shouldRejectACompletedOperationIdNotInTheGraph() {
      // given — a plan whose completed map names an id its own graph does not contain, as a
      // decode path or a directly constructed plan could produce without this check:
      // hasPendingChanges
      // (a size comparison) would then disagree with pendingOperations (a containsKey filter)
      // about whether the plan is done.
      final var builder = OperationGraph.builder();
      builder.add(op(0, 1));
      final var graph = builder.build();
      final var foreignId = OperationId.of(99);

      // when / then
      assertThatThrownBy(
              () ->
                  new DependencyChangePlan(
                      PLAN_ID,
                      Status.IN_PROGRESS,
                      Instant.now(),
                      graph,
                      new TreeMap<>(Map.of(foreignId, Instant.now()))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not part of this plan");
    }
  }
}
