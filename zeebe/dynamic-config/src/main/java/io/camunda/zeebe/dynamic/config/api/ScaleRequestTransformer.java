/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ScaleRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> members;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<TenantPartitionCount> newPartitionCount;
  private final Optional<String> zone;
  private final ArrayList<ClusterConfigurationChangeOperation> generatedOperations =
      new ArrayList<>();

  /**
   * @param members the complete member set the cluster should have once this request is applied, so
   *     a member being removed is simply absent from it
   * @param newReplicationFactor the replication factor every partition of every group should reach,
   *     or empty to keep the one currently in use
   * @param newPartitionCount the partition count one named physical tenant should reach, or empty
   *     to scale no tenant. The count carries the tenant it applies to rather than defaulting to
   *     the default group, so that a caller scaling no tenant names none and a caller scaling one
   *     has to say which; see {@link TenantPartitionCount}.
   * @param zone the zone whose brokers this request resizes, or empty on an unzoned cluster. Only
   *     selects the member from which the pre/post-scaling callbacks run — the partition placement
   *     spans the whole cluster either way.
   */
  public ScaleRequestTransformer(
      final Set<MemberId> members,
      final Optional<Integer> newReplicationFactor,
      final Optional<TenantPartitionCount> newPartitionCount,
      final Optional<String> zone) {
    this.members = members;
    this.newReplicationFactor = newReplicationFactor;
    this.newPartitionCount = newPartitionCount;
    this.zone = zone;
  }

  /**
   * Plans the change as pre-scaling and member joins, then the partition placement, then member
   * leaves and post-scaling, across every physical tenant's partition group.
   *
   * <p>{@link PartitionGroupScalingPhases} distributes every group's partitions together over
   * {@link #members}, rather than distributing the default group's alone. The member operations
   * have no tenant dimension, so they are unchanged. Only {@link #newPartitionCount} is scoped to
   * one group — the one it names.
   *
   * <p>The resulting phases are the same three {@code toPhases} derives from the flat operation
   * list today — a global phase, a partition-group phase, a global phase — with the two global runs
   * merged when there is no partition work, since {@code toPhases} would then see one uninterrupted
   * run of global operations. A single-tenant cluster therefore plans exactly what it planned
   * before.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }
    if (configuration.isFullyZoneAware() && members.stream().anyMatch(MemberId::isBare)) {
      return Either.left(
          new InvalidRequest(
              "Members without a zone cannot be added to a zone-aware cluster: "
                  + members.stream().filter(MemberId::isBare).sorted().toList()));
    }

    final var currentMembers = configuration.getMembers();
    final var joining =
        members.stream()
            .filter(member -> !currentMembers.contains(member))
            .sorted()
            .map(member -> (GlobalChangeOperation) new MemberJoinOperation(member))
            .toList();
    final var leaving =
        currentMembers.stream()
            .filter(member -> !members.contains(member))
            .sorted()
            .map(member -> (GlobalChangeOperation) new MemberLeaveOperation(member))
            .toList();

    // The callbacks act on the node-id state of the zone being scaled, and there is nothing to
    // prepare when the member set is unchanged.
    final var scalingExecutor =
        currentMembers.equals(members)
            ? Optional.<MemberId>empty()
            : selectPrePostScalingExecutor(currentMembers);

    final var before = new ArrayList<GlobalChangeOperation>();
    scalingExecutor.ifPresent(id -> before.add(new PreScalingOperation(id, members)));
    before.addAll(joining);
    final var after = new ArrayList<GlobalChangeOperation>(leaving);
    scalingExecutor.ifPresent(id -> after.add(new PostScalingOperation(id, members)));

    return PartitionGroupScalingPhases.phases(
            configuration, members, newPartitionCount, newReplicationFactor)
        .map(partitionPhases -> assemble(before, partitionPhases, after));
  }

  private static List<Phase> assemble(
      final List<GlobalChangeOperation> before,
      final List<Phase> partitionPhases,
      final List<GlobalChangeOperation> after) {
    if (partitionPhases.isEmpty()) {
      // Nothing separates the two runs of global operations, so they are one phase — exactly what
      // toPhases yields for the equivalent flat operation list.
      final var merged = new ArrayList<>(before);
      merged.addAll(after);
      return merged.isEmpty() ? List.of() : List.of(new GlobalPhase(List.copyOf(merged)));
    }
    final var phases = new ArrayList<Phase>();
    if (!before.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(before)));
    }
    phases.addAll(partitionPhases);
    if (!after.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(after)));
    }
    return phases;
  }

  private Optional<MemberId> selectPrePostScalingExecutor(final Set<MemberId> currentMembers) {
    final var coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.ofMembers(currentMembers);
    if (zone.isEmpty()) {
      return Optional.of(coordinatorSupplier.getDefaultCoordinator());
    }
    final var zoneName = zone.get();
    final var membersInZone =
        // pick a member from the current set of members
        currentMembers.stream().filter(m -> m.isInZone(zoneName)).collect(Collectors.toSet());
    if (membersInZone.isEmpty()) {
      // New zone with no existing brokers: the callbacks cannot run in the correct zone and the
      // new zone's brokers create their node-id leases on startup, so skip pre/post scaling.
      return Optional.empty();
    }
    return Optional.of(coordinatorSupplier.getNextCoordinator(membersInZone));
  }
}
