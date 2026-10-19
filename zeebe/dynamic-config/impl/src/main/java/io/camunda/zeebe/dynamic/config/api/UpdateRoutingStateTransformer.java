/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes the routing state, either for a single physical tenant or, when none is given, for the
 * default physical tenant. Unlike the other per-tenant requests, an absent {@code physicalTenantId}
 * does not mean "every tenant" here: it keeps the pre-existing behaviour of writing only the
 * default group, so unscoped callers see no change once physical tenants exist.
 */
public class UpdateRoutingStateTransformer implements ConfigurationChangeRequest {

  private final Optional<RoutingState> routingState;
  private final Optional<String> physicalTenantId;

  public UpdateRoutingStateTransformer(final Optional<RoutingState> routingState) {
    this(routingState, Optional.empty());
  }

  public UpdateRoutingStateTransformer(
      final Optional<RoutingState> routingState, final Optional<String> physicalTenantId) {
    this.routingState = routingState;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to update the routing state of physical tenant '%s', but there's no such tenant"
                  .formatted(physicalTenantId.get())));
    }

    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);
    final var group =
        Objects.requireNonNull(
            clusterConfiguration.partitionGroup(groupId),
            () -> "Expected partition group '%s' to exist".formatted(groupId));

    return lowestMemberWithPartitions(group, groupId)
        .map(
            memberId ->
                List.<Phase>of(
                    PartitionGroupPhase.sequential(
                        Map.of(groupId, List.of(new UpdateRoutingState(memberId, routingState))))));
  }

  /**
   * The lowest member id, among the members of {@code group} that hold at least one partition,
   * mirroring {@link ModeChangeRequestTransformer#membersToTransition}. The applier is registered
   * per group on the members that actually replicate it (see {@code
   * ClusterConfigurationManagerImpl#applyPartitionGroupConfigurationChangeOperation}, which
   * dispatches via {@code group.pendingChangesFor(localMemberId)}), so a member with no partitions
   * in the group would never pick up the operation and the change would stall.
   *
   * <p>Answers a request failure rather than throwing when the group has no such member: the
   * coordinator calls this while planning, and an exception thrown there escapes the request
   * instead of failing it.
   */
  private Either<Exception, MemberId> lowestMemberWithPartitions(
      final PartitionGroupConfiguration group, final String groupId) {
    return group.members().entrySet().stream()
        .filter(member -> !member.getValue().partitions().isEmpty())
        .map(Entry::getKey)
        .min(MemberId.ID_COMPARATOR)
        .<Either<Exception, MemberId>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new InvalidState(
                        "Cannot update the routing state of physical tenant '%s': none of its members hold any partitions"
                            .formatted(groupId))));
  }
}
