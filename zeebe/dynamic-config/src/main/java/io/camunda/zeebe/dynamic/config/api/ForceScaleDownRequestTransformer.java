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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Removes brokers that are already gone: every partition they replicated is force-reconfigured onto
 * whichever of the retained brokers still hold it, and the departed brokers are then dropped from
 * the member set. There is no data movement — a forced removal exists precisely because the
 * departing brokers cannot be asked to hand their partitions over.
 */
public class ForceScaleDownRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToRetain;

  private final MemberId coordinator;

  public ForceScaleDownRequestTransformer(
      final Set<MemberId> membersToRetain, final MemberId coordinator) {
    this.membersToRetain = membersToRetain;
    this.coordinator = coordinator;
  }

  /**
   * Plans the forced reconfiguration for every physical tenant's partitions rather than the default
   * tenant's only. Planning the default group alone left the other tenants' partitions on the
   * departed brokers, and the member removal that follows — which checks every partition group —
   * then refused the whole plan, so a cluster with more than one tenant could not force-remove a
   * broker at all.
   *
   * <p>One phase reconfigures every tenant in parallel, and a second removes the brokers, in that
   * order: a broker may only leave the member set once nothing replicates on it any more.
   *
   * <p>Partition ids restart at 1 in every physical tenant, so the partitions of different tenants
   * are resolved against their own group and never merged: two tenants each having a partition 1 is
   * two partitions, planned independently of one another.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    final var members = configuration.globalConfiguration().members().keySet();
    final var unknownRetainedMember = unknownRetainedMember(members);
    if (unknownRetainedMember.isPresent()) {
      return Either.left(unknownRetainedMember.get());
    }

    final SortedMap<String, SortedMap<Integer, SortedSet<MemberId>>> replicasByTenant =
        new TreeMap<>();
    configuration
        .partitionGroups()
        .forEach(
            (physicalTenantId, group) ->
                replicasByTenant.put(physicalTenantId, survivingReplicas(group)));

    final var orphanedByTenant = new TreeMap<String, List<Integer>>();
    replicasByTenant.forEach(
        (physicalTenantId, replicas) -> {
          final var orphaned = orphanedPartitions(replicas);
          if (!orphaned.isEmpty()) {
            orphanedByTenant.put(physicalTenantId, orphaned);
          }
        });
    if (!orphanedByTenant.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Expected to force configure and retain members '%s', but this will result in partitions '%s' having no replicas",
                  membersToRetain, orphanedByTenant)));
    }

    final Map<String, List<PartitionGroupOperation>> reconfigureByTenant = new TreeMap<>();
    replicasByTenant.forEach(
        (physicalTenantId, replicas) -> {
          if (!replicas.isEmpty()) {
            reconfigureByTenant.put(physicalTenantId, reconfigurePartitionsOf(replicas));
          }
        });

    final List<GlobalChangeOperation> removeMembers =
        members.stream()
            .filter(member -> !membersToRetain.contains(member))
            .sorted(MemberId.ID_COMPARATOR)
            .map(member -> (GlobalChangeOperation) new MemberRemoveOperation(coordinator, member))
            .toList();

    final List<Phase> phases = new ArrayList<>();
    if (!reconfigureByTenant.isEmpty()) {
      phases.add(PartitionGroupPhase.sequential(reconfigureByTenant));
    }
    if (!removeMembers.isEmpty()) {
      phases.add(new GlobalPhase(removeMembers));
    }
    return Either.right(phases);
  }

  @Override
  public boolean isForced() {
    return true;
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>> generateOperations(
      final SortedMap<Integer, SortedSet<MemberId>> partitionsWithNewMembers,
      final SortedSet<MemberId> memberToRemove) {

    final List<ClusterConfigurationChangeOperation> operations =
        new ArrayList<>(reconfigurePartitionsOf(partitionsWithNewMembers));

    final var memberRemoveOperations = forceRemoveMembers(memberToRemove);
    operations.addAll(memberRemoveOperations);

    return Either.right(operations);
  }

  private List<ClusterConfigurationChangeOperation> forceRemoveMembers(
      final SortedSet<MemberId> membersToRemove) {
    return membersToRemove.stream()
        .map(member -> new MemberRemoveOperation(coordinator, member))
        .map(ClusterConfigurationChangeOperation.class::cast)
        .toList();
  }

  /**
   * The first retained broker that the cluster does not know, if there is one. Retaining a broker
   * that is not a member is a typo in the request rather than an instruction, and answering it by
   * removing every other broker would be catastrophic.
   */
  private Optional<Exception> unknownRetainedMember(final Set<MemberId> members) {
    return membersToRetain.stream()
        .filter(member -> !members.contains(member))
        .findFirst()
        .map(
            member ->
                new InvalidRequest(
                    String.format(
                        "Expected to force configure while retaining broker '%s', but broker '%s' is not in the current cluster. Current members are '%s'",
                        member, member, members)));
  }

  private SortedMap<Integer, SortedSet<MemberId>> survivingReplicas(
      final PartitionGroupConfiguration group) {
    return survivingReplicas(
        group.members().entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, entry -> entry.getValue().partitions().keySet())));
  }

  private List<PartitionGroupOperation> reconfigurePartitionsOf(
      final SortedMap<Integer, SortedSet<MemberId>> survivingReplicas) {
    return survivingReplicas.entrySet().stream()
        .map(
            partition ->
                (PartitionGroupOperation)
                    new PartitionForceReconfigureOperation(
                        partition.getValue().stream().findFirst().orElseThrow(),
                        partition.getKey(),
                        partition.getValue()))
        .toList();
  }

  /**
   * The partitions of one physical tenant paired with the replicas that survive the removal: every
   * partition any broker of that tenant holds, mapped to the retained brokers among them.
   *
   * <p>A partition whose every replica is being removed maps to an empty set rather than dropping
   * out of the map — that is the case {@link #orphanedPartitions(SortedMap)} has to find.
   *
   * @param partitionsByMember which partitions each broker holds, for one physical tenant
   */
  private SortedMap<Integer, SortedSet<MemberId>> survivingReplicas(
      final Map<MemberId, ? extends Collection<Integer>> partitionsByMember) {
    final SortedMap<Integer, SortedSet<MemberId>> survivingReplicas = new TreeMap<>();
    partitionsByMember.forEach(
        (member, partitions) ->
            partitions.forEach(
                partitionId -> {
                  final var replicas =
                      survivingReplicas.computeIfAbsent(partitionId, ignored -> new TreeSet<>());
                  if (membersToRetain.contains(member)) {
                    replicas.add(member);
                  }
                }));
    return survivingReplicas;
  }

  /** The partitions that the removal would leave without a single replica. */
  private static List<Integer> orphanedPartitions(
      final SortedMap<Integer, SortedSet<MemberId>> survivingReplicas) {
    return survivingReplicas.entrySet().stream()
        .filter(partition -> partition.getValue().isEmpty())
        .map(Entry::getKey)
        .toList();
  }
}
