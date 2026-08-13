/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

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
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Validates a {@link RestoreRequest} against the current cluster configuration and produces the
 * change plan for the restore.
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

    // Restore is only allowed in recovery
    if (!isClusterRecovering(clusterConfiguration)) {
      return Either.left(
          new ConcurrentModificationException(
              "Restore is only allowed while the cluster is in recovery mode."));
    }

    final var validator = registry.getValidator(request.physicalTenantId(), RestoreRequest.class);
    if (validator.isEmpty()) {
      return Either.left(new InternalError("A validator is required but not present"));
    }
    final var res = validator.get().validate(request);

    if (res.isLeft()) {
      return Either.left(mapFailure(res.getLeft()));
    }
    try {
      return Either.right(
          buildOperations(clusterConfiguration, (RestoreResolvedRequest) res.get()));
    } catch (final Exception e) {
      return Either.left(mapFailure(e));
    }
  }

  private static List<ClusterConfigurationChangeOperation> buildOperations(
      final ClusterConfiguration clusterConfiguration, final RestoreResolvedRequest resolved) {
    final var members =
        clusterConfiguration.members().entrySet().stream()
            .filter(entry -> entry.getValue().state() == State.RECOVERING)
            .map(Entry::getKey)
            .sorted()
            .toList();

    final var operations = new ArrayList<ClusterConfigurationChangeOperation>();
    for (final var memberId : members) {
      for (final var partitionId : clusterConfiguration.getMember(memberId).partitions().keySet()) {
        operations.add(new PartitionPreRestoreOperation(memberId, partitionId));
      }
    }
    for (final var memberId : members) {
      for (final var partitionId : clusterConfiguration.getMember(memberId).partitions().keySet()) {
        final var backupIds = resolved.backups().get(partitionId);
        operations.add(
            new PartitionRestoreOperation(memberId, partitionId, toSortedSet(backupIds)));
      }
    }
    for (final var memberId : members) {
      operations.add(new ModeChangeOperation(memberId, Mode.PROCESSING));
    }
    for (final var memberId : members) {
      operations.add(new AwaitModeChangeOperation(memberId, Mode.PROCESSING));
    }
    operations.add(new UpdateIncarnationNumberOperation(members.getFirst()));
    return operations;
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

  /**
   * Whether every broker holding a partition to restore is in recovery.
   *
   * <p>Only brokers that hold a partition are considered. On the multi-partition-group model this
   * configuration is the projection of a single partition group, in which a broker that holds no
   * partition of that group keeps its cluster-wide {@link State#ACTIVE} state: entering recovery
   * never transitions it, because it has no partition to transition and awaiting a mode change from
   * it would never complete (see {@code ModeChangeRequestTransformer#membersToTransition}).
   * Counting such a broker would reject every restore of a physical tenant hosted on a subset of
   * the cluster's brokers. Excluding it is equally correct on the legacy single-group model, where
   * a partition-less broker has nothing to restore either.
   */
  private static boolean isClusterRecovering(final ClusterConfiguration clusterConfiguration) {
    final var membersHoldingPartitions =
        clusterConfiguration.members().values().stream()
            .filter(member -> member.state() != State.UNINITIALIZED && member.state() != State.LEFT)
            .filter(member -> !member.partitions().isEmpty())
            .toList();
    return !membersHoldingPartitions.isEmpty()
        && membersHoldingPartitions.stream().allMatch(member -> member.state() == State.RECOVERING);
  }
}
