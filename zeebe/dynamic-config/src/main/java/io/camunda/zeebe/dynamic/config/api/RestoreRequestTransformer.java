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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Validates a {@link RestoreRequest} against the current cluster configuration and produces the
 * change plan for the restore of the request's physical tenant.
 *
 * <p>The plan is built from the request's own {@link PartitionGroupConfiguration}, where recovery
 * is tracked per broker within the group: only a broker holding a partition of that tenant takes
 * part, since a broker holding none was never transitioned into recovery for it.
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
        .map(graph -> List.of(new PartitionGroupPhase(Map.of(physicalTenantId, graph))));
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
   * The restore expressed as dependencies rather than list order, scoped to the partition each
   * operation actually concerns.
   *
   * <p>The unit of ordering is one broker's copy of one partition, not the plan. Wiping and
   * reloading a copy touches nothing else, so every {@code (broker, partition)} pair is an
   * independent chain: {@code m0} can be reloading partition 1 while {@code m2} is still wiping its
   * own copy of it.
   *
   * <p>The edges, per partition {@code k} and broker {@code m}:
   *
   * <ul>
   *   <li>{@code preRestore(m,k)} — nothing; every pre-restore of every partition starts at once.
   *   <li>{@code restore(m,k)} — only {@code preRestore(m,k)}. Wiping and reloading a broker's copy
   *       of a partition is local to that broker, so one broker may reload {@code k} while a peer
   *       is still wiping its own copy of {@code k}. What makes leaving these unordered safe is
   *       that neither step writes the group configuration — see {@code PartitionPreRestoreApplier}
   *       and {@code PartitionRestoreApplier}, which both apply {@code UnaryOperator.identity()}.
   *   <li>{@code modeChange(m)} — the restores of the partitions {@code m} holds. Partitions {@code
   *       m} does not hold cannot block it leaving recovery.
   *   <li>{@code awaitModeChange(m)} — every mode change and <em>every</em> restore. This one is
   *       deliberately a cluster-wide barrier: awaiting the transition observes the group as a
   *       whole, so no broker may start observing while any partition anywhere is still reloading.
   *   <li>{@code updateIncarnationNumber} — every await. It writes the group's own state, so it is
   *       ordered after everything by construction.
   * </ul>
   *
   * <p>Scoping the edges is only safe because no two operations that may run concurrently write the
   * same part of the group configuration: the wipes and reloads write nothing at all, a mode change
   * and its await write only broker {@code m}'s own mode and partition states, and the one
   * whole-group write, the incarnation number, is ordered after every await. An applier that starts
   * writing something wider has to be paired with edges here that order it against the operations
   * it now shares a field with.
   *
   * <p>So there is one cluster-wide barrier, at the awaits. Everything before it is scoped: the
   * {@code N·P} wipes and reloads become {@code N·P} independent chains rather than two
   * cluster-wide barriers, and a broker leaves recovery as soon as the partitions it holds are back
   * rather than waiting on partitions it does not hold. On a tenant where every broker replicates
   * every partition the mode-change edges collapse to cluster-wide anyway, since there every broker
   * holds everything; the wipe/reload chains do not, and that is where the I/O is.
   */
  private static OperationGraph restoreGraph(
      final SortedMap<MemberId, Set<Integer>> partitionsPerMember,
      final RestoreResolvedRequest resolved) {
    final var builder = OperationGraph.builder();

    final Map<MemberId, Map<Integer, OperationId>> preRestoreOf = new TreeMap<>();
    partitionsPerMember.forEach(
        (memberId, partitions) ->
            partitions.forEach(
                partitionId ->
                    preRestoreOf
                        .computeIfAbsent(memberId, ignored -> new TreeMap<>())
                        .put(
                            partitionId,
                            builder.add(new PartitionPreRestoreOperation(memberId, partitionId)))));

    final Map<Integer, Set<OperationId>> restoresOf = new TreeMap<>();
    partitionsPerMember.forEach(
        (memberId, partitions) ->
            partitions.forEach(
                partitionId ->
                    restoresOf
                        .computeIfAbsent(partitionId, ignored -> new HashSet<>())
                        .add(
                            builder.add(
                                new PartitionRestoreOperation(
                                    memberId,
                                    partitionId,
                                    toSortedSet(resolved.backups().get(partitionId))),
                                Set.of(
                                    Objects.requireNonNull(
                                        preRestoreOf.get(memberId).get(partitionId)))))));

    final SortedMap<MemberId, OperationId> modeChanges = new TreeMap<>();
    partitionsPerMember.forEach(
        (memberId, partitions) ->
            modeChanges.put(
                memberId,
                builder.add(
                    new ModeChangeOperation(memberId, Mode.PROCESSING),
                    operationsFor(partitions, restoresOf))));

    // Every await waits for every restore, cluster-wide, not only for the ones on its own broker or
    // its own partitions' replicas. Awaiting the transition observes the group as a whole, so a
    // broker must not start observing while any partition anywhere is still being reloaded.
    final Set<OperationId> beforeAwaits = new HashSet<>(modeChanges.values());
    restoresOf.values().forEach(beforeAwaits::addAll);

    final Set<OperationId> awaits = new HashSet<>();
    partitionsPerMember
        .keySet()
        .forEach(
            memberId ->
                awaits.add(
                    builder.add(
                        new AwaitModeChangeOperation(memberId, Mode.PROCESSING), beforeAwaits)));

    builder.add(new UpdateIncarnationNumberOperation(partitionsPerMember.firstKey()), awaits);
    return builder.build();
  }

  /** The union of the operations recorded for each of {@code partitions}. */
  private static Set<OperationId> operationsFor(
      final Set<Integer> partitions, final Map<Integer, Set<OperationId>> operationsPerPartition) {
    final Set<OperationId> union = new HashSet<>();
    partitions.forEach(
        partitionId -> union.addAll(operationsPerPartition.getOrDefault(partitionId, Set.of())));
    return union;
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
