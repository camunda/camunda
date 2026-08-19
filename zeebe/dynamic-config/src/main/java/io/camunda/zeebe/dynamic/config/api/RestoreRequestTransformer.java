/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InternalError;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupGraphPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Validates a {@link RestoreRequest} against the current cluster configuration and produces the
 * change plan for the restore of the request's physical tenant.
 *
 * <p>{@link #phases(CurrentClusterConfiguration)} is the entry point the new-model coordinator
 * drives: the plan is built from the request's own {@link PartitionGroupConfiguration}, where
 * recovery is tracked per broker within the group. {@link #operations(ClusterConfiguration)} is the
 * legacy single-group entry point, which only ever has one, ungrouped configuration to work with.
 *
 * <p>Validation failures are returned as an {@link Either#left(Object)} carrying a {@link
 * ClusterConfigurationRequestFailedException}, which the coordinator surfaces as an error response.
 */
public final class RestoreRequestTransformer implements ConfigurationChangeRequest {

  private final RestoreRequest request;
  private final RequestValidatorRegistry registry;

  public RestoreRequestTransformer(
      final RestoreRequest request, final RequestValidatorRegistry registry) {
    this.request = request;
    this.registry = registry;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (!isClusterRecovering(clusterConfiguration)) {
      return Either.left(
          new ConcurrentModificationException(
              "Restore is only allowed while the cluster is in recovery mode."));
    }
    final var members = recoveringMembers(clusterConfiguration);
    // The single-group path has no dependency execution, so the graph is flattened back into the
    // sequential ordering it degrades to. Slower, but the same end state.
    return restorePlan(members).map(graph -> List.copyOf(graph.inOrder()));
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var physicalTenantId = request.physicalTenantId();
    final var partitionGroup = clusterConfiguration.partitionGroup(physicalTenantId);
    if (partitionGroup == null) {
      return Either.left(
          new NotFound(
              "Expected to restore physical tenant '%s', but there's no such tenant"
                  .formatted(physicalTenantId)));
    }
    return groupGraph(partitionGroup)
        .map(graph -> List.of(new PartitionGroupGraphPhase(Map.of(physicalTenantId, graph))));
  }

  /**
   * The restore plan for the request's physical tenant on the new model, built from that tenant's
   * partition group alone. Shared with {@link ClusterRestoreRequestTransformer}, which combines the
   * plans of several physical tenants into one phase.
   */
  Either<Exception, OperationGraph> groupGraph(final PartitionGroupConfiguration partitionGroup) {
    if (!isGroupRecovering(partitionGroup)) {
      return Either.left(
          new ConcurrentModificationException(
              "Restore is only allowed while physical tenant '%s' is in recovery mode."
                  .formatted(request.physicalTenantId())));
    }
    final var members = recoveringMembers(partitionGroup);
    return restorePlan(members);
  }

  /**
   * Resolves the request's backup selection, then turns it into the restore plan for the given
   * brokers. A validator rejection, and anything the plan builder throws over a partition the
   * validator did not resolve a selection for, is mapped to a request failure.
   */
  private Either<Exception, OperationGraph> restorePlan(
      final SortedMap<MemberId, Set<Integer>> partitionsPerMember) {
    final var validator = registry.getValidator(request.physicalTenantId(), RestoreRequest.class);
    if (validator.isEmpty()) {
      return Either.left(new InternalError("A validator is required but not present"));
    }
    final var resolved = validator.get().validate(request);
    if (resolved.isLeft()) {
      return Either.left(mapFailure(resolved.getLeft()));
    }
    try {
      return Either.right(
          restoreGraph(partitionsPerMember, (RestoreResolvedRequest) resolved.get()));
    } catch (final Exception e) {
      return Either.left(mapFailure(e));
    }
  }

  /**
   * The brokers to restore, each mapped to the partitions it holds, ordered by broker id. On the
   * legacy model recovery is a broker-wide state, so every recovering broker takes part — including
   * one holding no partition, which contributes no partition operation but still has to exit
   * recovery, since entering it transitioned that broker too.
   */
  private static SortedMap<MemberId, Set<Integer>> recoveringMembers(
      final ClusterConfiguration clusterConfiguration) {
    final SortedMap<MemberId, Set<Integer>> partitionsPerMember = new TreeMap<>();
    clusterConfiguration
        .members()
        .forEach(
            (memberId, member) -> {
              if (member.state() == State.RECOVERING) {
                partitionsPerMember.put(memberId, member.partitions().keySet());
              }
            });
    return partitionsPerMember;
  }

  /**
   * The brokers to restore, each mapped to the partitions it holds in the group, ordered by broker
   * id. Only brokers holding a partition of the group take part: recovery is scoped to a broker
   * within a group, so a broker holding none was never transitioned into it, and awaiting a mode
   * change from it would never complete (see {@code
   * ModeChangeRequestTransformer#membersToTransition}).
   */
  private static SortedMap<MemberId, Set<Integer>> recoveringMembers(
      final PartitionGroupConfiguration partitionGroup) {
    final SortedMap<MemberId, Set<Integer>> partitionsPerMember = new TreeMap<>();
    partitionGroup
        .members()
        .forEach(
            (memberId, broker) -> {
              if (!broker.partitions().isEmpty()) {
                partitionsPerMember.put(memberId, broker.partitions().keySet());
              }
            });
    return partitionsPerMember;
  }

  /**
   * The same phase-major shape as before — every partition pre-restored, then every partition
   * restored, then every broker out of recovery, then one incarnation bump — but expressed as
   * dependencies instead of list order, so within each stage nothing takes turns.
   *
   * <p>All the pre-restores run at once across every broker and every partition, then all the
   * restores. For {@code N} brokers holding {@code P} partitions each that is four barriers instead
   * of {@code 2·N·P + 2·N + 1} serialised round trips.
   *
   * <p>The stage boundaries are kept whole on purpose. Narrower edges are expressible and would be
   * faster still — a broker's restore only obviously needs its own pre-restore, not everyone's —
   * but partitions are replicated across brokers, so whether a broker may leave recovery while a
   * peer is still restoring the same partition is a recovery-semantics question, not a scheduling
   * one. Nothing in the current code answers it, so the existing ordering is preserved until the
   * restore owner says which of these edges are real. Relaxing one is a one-line change here.
   */
  private static OperationGraph restoreGraph(
      final SortedMap<MemberId, Set<Integer>> partitionsPerMember,
      final RestoreResolvedRequest resolved) {
    final var builder = OperationGraph.builder();

    final Set<OperationId> preRestores = new HashSet<>();
    partitionsPerMember.forEach(
        (memberId, partitions) ->
            partitions.forEach(
                partitionId ->
                    preRestores.add(
                        builder.add(new PartitionPreRestoreOperation(memberId, partitionId)))));

    final Set<OperationId> restores = new HashSet<>();
    partitionsPerMember.forEach(
        (memberId, partitions) ->
            partitions.forEach(
                partitionId ->
                    restores.add(
                        builder.add(
                            new PartitionRestoreOperation(
                                memberId,
                                partitionId,
                                toSortedSet(resolved.backups().get(partitionId))),
                            preRestores))));

    final Set<OperationId> modeChanges = new HashSet<>();
    partitionsPerMember
        .keySet()
        .forEach(
            memberId ->
                modeChanges.add(
                    builder.add(new ModeChangeOperation(memberId, Mode.PROCESSING), restores)));

    final Set<OperationId> awaits = new HashSet<>();
    partitionsPerMember
        .keySet()
        .forEach(
            memberId ->
                awaits.add(
                    builder.add(
                        new AwaitModeChangeOperation(memberId, Mode.PROCESSING), modeChanges)));

    builder.add(new UpdateIncarnationNumberOperation(partitionsPerMember.firstKey()), awaits);
    return builder.build();
  }

  /** Whether every initialized broker of the cluster is in recovery. */
  private static boolean isClusterRecovering(final ClusterConfiguration clusterConfiguration) {
    final var initializedMembers =
        clusterConfiguration.members().values().stream()
            .filter(member -> member.state() != State.UNINITIALIZED && member.state() != State.LEFT)
            .toList();
    return !initializedMembers.isEmpty()
        && initializedMembers.stream().allMatch(member -> member.state() == State.RECOVERING);
  }

  /**
   * Whether every broker holding a partition of the group is in recovery.
   *
   * <p>Only brokers holding a partition are considered: the group's brokers carry no cluster-wide
   * lifecycle state, and a broker holding no partition of the group has nothing to restore and is
   * never transitioned into recovery for it. Counting such a broker would reject every restore of a
   * physical tenant hosted on a subset of the cluster's brokers.
   */
  private static boolean isGroupRecovering(final PartitionGroupConfiguration groupConfiguration) {
    final var brokersHoldingPartitions =
        groupConfiguration.members().values().stream()
            .filter(broker -> !broker.partitions().isEmpty())
            .toList();
    return !brokersHoldingPartitions.isEmpty()
        && brokersHoldingPartitions.stream().allMatch(broker -> broker.mode() == Mode.RECOVERING);
  }

  private static SortedSet<Long> toSortedSet(final long[] backupIds) {
    final var set = new TreeSet<Long>();
    for (final long id : backupIds) {
      set.add(id);
    }
    return set;
  }

  private static Exception mapFailure(final Exception exception) {
    return switch (exception) {
      case final ClusterConfigurationRequestFailedException e -> (Exception) e;
      case final IllegalArgumentException e -> new InvalidRequest(e.getMessage());
      case final NoSuchElementException e -> new NotFound(e.getMessage(), e);
      case final IllegalStateException e -> new InvalidState(e.getMessage(), e);
      default -> new InternalError(exception);
    };
  }
}
