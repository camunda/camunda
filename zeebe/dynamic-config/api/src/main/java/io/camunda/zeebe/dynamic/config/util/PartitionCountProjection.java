/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.OperationGraph.PlannedOperation;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.SchemaInitializationOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Projects how many partitions each member starts on boot while changes run, by replaying the
 * replica state transitions of their operations on the current configuration.
 *
 * <p>The projection is an upper bound, since a broker restarting at any point during a change must
 * still be able to start:
 *
 * <ul>
 *   <li>Pending changes run concurrently with each other, and so do the partition groups within a
 *       phase, so their increases add up, and replicas one group removes free nothing for another.
 *   <li>Within a group whose operations each wait for the one before it, the transitions are
 *       replayed in order, so a replica that leaves before another joins frees its slot first. When
 *       operations may run concurrently, every replica any of them starts is counted.
 * </ul>
 */
@NullMarked
public final class PartitionCountProjection {

  private PartitionCountProjection() {}

  /** Counts each member's partitions across all partition groups, as a broker does at startup. */
  public static Map<MemberId, Integer> partitionsPerMember(
      final CurrentClusterConfiguration configuration) {
    return ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(configuration)
        .values()
        .stream()
        .flatMap(partitions -> partitions.stream())
        .flatMap(partition -> partition.members().stream())
        .collect(Collectors.toMap(member -> member, member -> 1, Integer::sum));
  }

  /**
   * Returns the highest increase in each member's partition count while the given phases run on
   * {@code configuration}. Members whose count never rises above its current value are omitted.
   */
  public static Map<MemberId, Integer> peakIncrease(
      final CurrentClusterConfiguration configuration, final List<Phase> phases) {
    final var simulation = new Simulation(configuration);
    phases.forEach(phase -> simulation.runPhase(groupRunsOf(phase)));
    return simulation.peakIncrease();
  }

  /**
   * Returns the highest increase in each member's partition count while the operations still
   * outstanding in every pending change of {@code configuration} run, summed over those changes.
   * Members whose count never rises above its current value are omitted.
   */
  public static Map<MemberId, Integer> pendingPeakIncrease(
      final CurrentClusterConfiguration configuration) {
    final Map<MemberId, Integer> increase = new HashMap<>();
    final Set<String> groupsOfPendingPlans = new HashSet<>();
    for (final var plan : configuration.phasedChangeState().pending().values()) {
      if (plan.currentPhase() instanceof final PartitionGroupPhase currentPhase) {
        groupsOfPendingPlans.addAll(currentPhase.groupGraphs().keySet());
      }
      final var simulation = new Simulation(configuration);
      outstandingGroupRuns(configuration, plan).forEach(simulation::runPhase);
      simulation
          .peakIncrease()
          .forEach((member, peak) -> increase.merge(member, peak, Integer::sum));
    }
    // A change can also be started on a group directly, e.g. when a physical tenant is provisioned
    configuration
        .partitionGroups()
        .forEach(
            (groupId, group) -> {
              if (!groupsOfPendingPlans.contains(groupId)) {
                group
                    .pendingChanges()
                    .ifPresent(
                        pending -> {
                          final var simulation = new Simulation(configuration);
                          simulation.runPhase(
                              List.of(groupRun(groupId, remainingOperations(pending))));
                          simulation
                              .peakIncrease()
                              .forEach(
                                  (member, peak) -> increase.merge(member, peak, Integer::sum));
                        });
              }
            });
    return increase;
  }

  private static List<List<GroupRun>> outstandingGroupRuns(
      final CurrentClusterConfiguration configuration, final PhasedChangePlan plan) {
    final List<List<GroupRun>> outstanding = new ArrayList<>();
    // A group's part of the current phase is pending in that group until it has drained, and
    // removed from it afterwards.
    if (plan.currentPhase() instanceof final PartitionGroupPhase currentPhase) {
      final List<GroupRun> remaining = new ArrayList<>();
      for (final var groupId : currentPhase.groupGraphs().keySet()) {
        final var group = configuration.partitionGroup(groupId);
        if (group != null) {
          group
              .pendingChanges()
              .map(pending -> groupRun(groupId, remainingOperations(pending)))
              .ifPresent(remaining::add);
        }
      }
      outstanding.add(remaining);
    }
    for (final var phase :
        plan.phases().subList(plan.currentPhaseIndex() + 1, plan.phases().size())) {
      outstanding.add(groupRunsOf(phase));
    }
    return outstanding;
  }

  private static List<GroupRun> groupRunsOf(final Phase phase) {
    return switch (phase) {
      case final GlobalPhase ignored -> List.of();
      case final PartitionGroupPhase groupPhase ->
          groupPhase.groupGraphs().entrySet().stream()
              .map(entry -> groupRun(entry.getKey(), entry.getValue().operations()))
              .toList();
    };
  }

  private static SortedMap<OperationId, PlannedOperation> remainingOperations(
      final DependencyChangePlan plan) {
    final SortedMap<OperationId, PlannedOperation> remaining = new TreeMap<>();
    plan.operations()
        .forEach(
            (id, planned) -> {
              if (!plan.isComplete(id)) {
                remaining.put(id, planned);
              }
            });
    return remaining;
  }

  private static GroupRun groupRun(
      final String groupId, final SortedMap<OperationId, PlannedOperation> operations) {
    final List<PartitionGroupOperation> ordered = new ArrayList<>();
    boolean sequential = true;
    @Nullable OperationId previous = null;
    for (final var entry : operations.entrySet()) {
      if (previous != null && !entry.getValue().dependsOn().contains(previous)) {
        sequential = false;
      }
      previous = entry.getKey();
      if (entry.getValue().operation() instanceof final PartitionGroupOperation operation) {
        ordered.add(operation);
      }
    }
    return new GroupRun(groupId, ordered, sequential);
  }

  private static boolean isStartedOnBoot(final @Nullable State state) {
    return state != null && state.isStartedOnBoot();
  }

  /** The operations of one group in a phase, in order, and whether each waits for the previous. */
  private record GroupRun(
      String groupId, List<PartitionGroupOperation> operations, boolean sequential) {}

  private record Replica(String groupId, MemberId memberId, int partitionId) {}

  /** Moves a replica to a new state; a {@code null} state removes the replica. */
  private record Transition(Replica replica, @Nullable State state) {}

  /** How far a group raised each member's count at most, and by how much once it finished. */
  private record GroupResult(Map<MemberId, Integer> peak, Map<MemberId, Integer> net) {}

  private static final class Simulation {

    private final CurrentClusterConfiguration configuration;
    private final Map<Replica, Optional<State>> changedStates = new HashMap<>();
    private final Map<MemberId, Integer> running = new HashMap<>();
    private final Map<MemberId, Integer> peak = new HashMap<>();

    private Simulation(final CurrentClusterConfiguration configuration) {
      this.configuration = configuration;
    }

    private Map<MemberId, Integer> peakIncrease() {
      final Map<MemberId, Integer> increase = new HashMap<>(peak);
      increase.values().removeIf(value -> value <= 0);
      return increase;
    }

    private void runPhase(final List<GroupRun> groups) {
      final Map<MemberId, Integer> phasePeak = new HashMap<>();
      final Map<MemberId, Integer> phaseNet = new HashMap<>();
      for (final var group : groups) {
        final var result = group.sequential() ? runSequential(group) : runConcurrent(group);
        result.peak().forEach((member, value) -> phasePeak.merge(member, value, Integer::sum));
        result.net().forEach((member, value) -> phaseNet.merge(member, value, Integer::sum));
      }
      phasePeak.forEach(
          (member, value) ->
              peak.merge(member, running.getOrDefault(member, 0) + value, Math::max));
      phaseNet.forEach((member, value) -> running.merge(member, value, Integer::sum));
    }

    private GroupResult runSequential(final GroupRun group) {
      final Map<MemberId, Integer> net = new HashMap<>();
      final Map<MemberId, Integer> peak = new HashMap<>();
      for (final var operation : group.operations()) {
        for (final var step : steps(group.groupId(), operation)) {
          for (final var transition : step) {
            final var member = transition.replica().memberId();
            final int value = net.merge(member, apply(transition), Integer::sum);
            peak.merge(member, Math.max(0, value), Math::max);
          }
        }
      }
      return new GroupResult(peak, net);
    }

    private GroupResult runConcurrent(final GroupRun group) {
      final Map<MemberId, Integer> net = new HashMap<>();
      final Set<Replica> startedBefore = new HashSet<>();
      final Set<Replica> touched = new HashSet<>();
      final Set<Replica> started = new HashSet<>();
      for (final var operation : group.operations()) {
        for (final var step : steps(group.groupId(), operation)) {
          for (final var transition : step) {
            final var replica = transition.replica();
            if (touched.add(replica) && isStartedOnBoot(stateOf(replica))) {
              startedBefore.add(replica);
            }
            if (isStartedOnBoot(transition.state())) {
              started.add(replica);
            }
            net.merge(replica.memberId(), apply(transition), Integer::sum);
          }
        }
      }
      started.removeAll(startedBefore);
      final Map<MemberId, Integer> peak = new HashMap<>();
      started.forEach(replica -> peak.merge(replica.memberId(), 1, Integer::sum));
      return new GroupResult(peak, net);
    }

    /** Moves the replica to its new state and returns by how much its member's count changed. */
    private int apply(final Transition transition) {
      final boolean before = isStartedOnBoot(stateOf(transition.replica()));
      changedStates.put(transition.replica(), Optional.ofNullable(transition.state()));
      return Boolean.compare(isStartedOnBoot(transition.state()), before);
    }

    private @Nullable State stateOf(final Replica replica) {
      final var changed = changedStates.get(replica);
      if (changed != null) {
        return changed.orElse(null);
      }
      final var group = configuration.partitionGroup(replica.groupId());
      final var member = group == null ? null : group.getMember(replica.memberId());
      final var partition = member == null ? null : member.getPartition(replica.partitionId());
      return partition == null ? null : partition.state();
    }

    /** The group's replicas, of all partitions or only of the given one. */
    private List<Replica> replicasOf(final String groupId, final @Nullable Integer partitionId) {
      final Set<Replica> candidates = new HashSet<>();
      final var group = configuration.partitionGroup(groupId);
      if (group != null) {
        group
            .members()
            .forEach(
                (member, broker) ->
                    broker
                        .partitions()
                        .keySet()
                        .forEach(id -> candidates.add(new Replica(groupId, member, id))));
      }
      changedStates.keySet().stream()
          .filter(replica -> replica.groupId().equals(groupId))
          .forEach(candidates::add);
      return candidates.stream()
          .filter(replica -> partitionId == null || replica.partitionId() == partitionId)
          .toList();
    }

    /**
     * The transitions an operation makes, as consecutive steps. A broker restarting between two
     * steps starts the partitions whose state the earlier step left started on boot.
     */
    private List<List<Transition>> steps(
        final String groupId, final PartitionGroupOperation operation) {
      return switch (operation) {
        case final PartitionJoinOperation join -> {
          final var replica = new Replica(groupId, join.memberId(), join.partitionId());
          yield List.of(
              List.of(new Transition(replica, State.JOINING)),
              List.of(new Transition(replica, join.asLearner() ? State.LEARNER : State.ACTIVE)));
        }
        case final PartitionBootstrapOperation bootstrap -> {
          final var replica = new Replica(groupId, bootstrap.memberId(), bootstrap.partitionId());
          yield List.of(
              List.of(new Transition(replica, State.BOOTSTRAPPING)),
              List.of(new Transition(replica, State.ACTIVE)));
        }
        case final PartitionLeaveOperation leave -> {
          final var replica = new Replica(groupId, leave.memberId(), leave.partitionId());
          yield List.of(
              List.of(new Transition(replica, State.LEAVING)),
              List.of(new Transition(replica, null)));
        }
        case final PartitionDemoteOperation demote ->
            List.of(
                List.of(
                    new Transition(
                        new Replica(groupId, demote.memberId(), demote.partitionId()),
                        State.LEAVING)));
        case final PartitionPromoteOperation promote ->
            List.of(
                List.of(
                    new Transition(
                        new Replica(groupId, promote.memberId(), promote.partitionId()),
                        State.ACTIVE)));
        case final PartitionForceReconfigureOperation reconfigure ->
            List.of(
                replicasOf(groupId, reconfigure.partitionId()).stream()
                    .filter(replica -> !reconfigure.members().contains(replica.memberId()))
                    .map(replica -> new Transition(replica, null))
                    .toList());
        case final RemovePhysicalTenantOperation ignored ->
            List.of(
                replicasOf(groupId, null).stream()
                    .map(replica -> new Transition(replica, null))
                    .toList());
        case final PartitionReconfigurePriorityOperation ignored -> List.of();
        case final PartitionDisableExporterOperation ignored -> List.of();
        case final PartitionDeleteExporterOperation ignored -> List.of();
        case final PartitionEnableExporterOperation ignored -> List.of();
        case final PartitionPreRestoreOperation ignored -> List.of();
        case final PartitionRestoreOperation ignored -> List.of();
        case final StartPartitionScaleUp ignored -> List.of();
        case final AwaitRedistributionCompletion ignored -> List.of();
        case final AwaitRelocationCompletion ignored -> List.of();
        case final DeleteHistoryOperation ignored -> List.of();
        case final UpdateRoutingState ignored -> List.of();
        case final UpdateIncarnationNumberOperation ignored -> List.of();
        case final ModeChangeOperation ignored -> List.of();
        case final ExportingStateChangeOperation ignored -> List.of();
        case final AwaitModeChangeOperation ignored -> List.of();
        case final SchemaInitializationOperation ignored -> List.of();
      };
    }
  }
}
