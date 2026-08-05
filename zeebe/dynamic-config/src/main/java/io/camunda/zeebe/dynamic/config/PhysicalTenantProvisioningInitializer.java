/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.util.AdditivePartitionReassigner;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.PartitionReassignmentOperationsGenerator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provisions a brand-new physical tenant on the coordinator: if the local static configuration
 * lists a physical tenant (a {@link PartitionId#group()}) that has no corresponding entry in {@link
 * CurrentClusterConfiguration#partitionGroups()}, this adds an empty {@link
 * PartitionGroupConfiguration} for it and starts a configuration change directly on that group —
 * using {@link AdditivePartitionReassigner} to place its partitions and {@link
 * PartitionReassignmentOperationsGenerator} to turn that placement into operations.
 *
 * <p>The change is started directly on the new group via {@link
 * PartitionGroupConfiguration#startConfigurationChange(List)}, bypassing the top-level {@link
 * io.camunda.zeebe.dynamic.config.state.PhasedChangePlan} entirely: a phased change may already be
 * in progress for other groups when this runs, and this group is guaranteed to have no pending
 * change of its own yet since it doesn't exist before this modifier creates it.
 *
 * <p>A physical tenant whose group already exists is left completely untouched, even if it's since
 * been removed from the static configuration — this modifier only ever adds a group, never removes
 * one.
 *
 * <p>Multiple new physical tenants are provisioned in the same {@link #modify(Object)} call if
 * found, via a single joint {@link AdditivePartitionReassigner} call covering all of them at once —
 * not one call per tenant — so their partitions are placed with knowledge of each other's load, the
 * same way a single call already spreads a batch of new partitions for one tenant evenly. Calling
 * the reassigner separately per tenant would place each one only against the load already on disk,
 * blind to the other new tenants being provisioned in the same pass, and could easily pile several
 * brand-new tenants onto the same least-loaded brokers instead of spreading them out.
 *
 * <p>If placing this batch fails outright (e.g. the live cluster currently has fewer members than
 * the configured replication factor), the whole batch is skipped with a warning and retried on the
 * next configuration initialization; if the reassignment itself succeeds but a particular tenant's
 * target distribution ends up unchanged (e.g. it has no partitions configured at all), that one
 * tenant alone is skipped while the rest are still provisioned.
 */
public class PhysicalTenantProvisioningInitializer
    extends ClusterConfigurationModifier.CoordinatorOnly<CurrentClusterConfiguration> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantProvisioningInitializer.class);

  private final Map<String, List<PartitionId>> staticPartitionIdsByTenant;
  private final int replicationFactor;
  private final AdditivePartitionReassigner reassigner = new AdditivePartitionReassigner();
  private final StaticConfiguration staticConfiguration;

  public PhysicalTenantProvisioningInitializer(final StaticConfiguration staticConfiguration) {
    super(staticConfiguration.localMemberId());
    this.staticConfiguration = staticConfiguration;
    staticPartitionIdsByTenant =
        staticConfiguration.partitionIds().stream()
            .collect(Collectors.groupingBy(PartitionId::group));
    replicationFactor = staticConfiguration.replicationFactor();
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    final var newTenantIds =
        staticPartitionIdsByTenant.keySet().stream()
            .filter(tenantId -> !configuration.partitionGroups().containsKey(tenantId))
            .sorted()
            .toList();
    if (newTenantIds.isEmpty()) {
      return CompletableActorFuture.completed(configuration);
    }

    try {
      return CompletableActorFuture.completed(provisionTenants(configuration, newTenantIds));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to provision new physical tenants {}; skipping them for now, they will be "
              + "retried on the next configuration initialization",
          newTenantIds,
          e);
      return CompletableActorFuture.completed(configuration);
    }
  }

  /**
   * Computes the target distribution for every new tenant's partitions in a single {@link
   * AdditivePartitionReassigner} call, so their placement accounts for each other's load, then
   * splits the result into one configuration change per new group.
   */
  private CurrentClusterConfiguration provisionTenants(
      final CurrentClusterConfiguration configuration, final List<String> newTenantIds) {
    final Set<MemberId> targetMembers = liveMembers(configuration);
    final var existingPartitionIds =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(configuration).values().stream()
            .flatMap(Set::stream)
            .map(PartitionMetadata::id)
            .toList();
    final var newPartitionIds =
        newTenantIds.stream()
            .flatMap(tenantId -> staticPartitionIdsByTenant.get(tenantId).stream());
    final var targetPartitionIds =
        Stream.concat(existingPartitionIds.stream(), newPartitionIds).toList();

    final var targetDistribution =
        getTargetDistribution(configuration, targetMembers, targetPartitionIds);

    final Map<String, DynamicPartitionConfig> partitionConfigByNewGroup =
        newTenantIds.stream()
            .collect(
                Collectors.toMap(
                    tenantId -> tenantId, ignored -> staticConfiguration.partitionConfig()));
    final var operationsByGroup =
        PartitionReassignmentOperationsGenerator.generateOperations(
            configuration, targetDistribution, partitionConfigByNewGroup);

    var result = configuration;
    for (final var newTenantId : newTenantIds) {
      final List<PartitionGroupOperation> tenantOperations = operationsByGroup.get(newTenantId);
      if (tenantOperations == null || tenantOperations.isEmpty()) {
        LOG.warn(
            "No operations were generated to provision new physical tenant '{}'; skipping it for "
                + "now",
            newTenantId);
        continue;
      }
      result = addGroupAndStartChange(result, newTenantId, tenantOperations);
    }
    return result;
  }

  /**
   * The members eligible to host a new tenant's partitions: every broker known to the cluster
   * except those in {@link BrokerState.State#LEFT} or {@link BrokerState.State#UNINITIALIZED} —
   * mirroring {@link CurrentClusterConfiguration#clusterSize()}'s definition of "live". Using the
   * raw {@code globalConfiguration().members().keySet()} instead would let a decommissioned or
   * not-yet-joined broker be selected as a target, stalling provisioning once its bootstrap/join
   * operation can never complete.
   */
  private static Set<MemberId> liveMembers(final CurrentClusterConfiguration configuration) {
    return configuration.globalConfiguration().members().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().state() != BrokerState.State.LEFT
                    && entry.getValue().state() != BrokerState.State.UNINITIALIZED)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private Set<PartitionMetadata> getTargetDistribution(
      final CurrentClusterConfiguration configuration,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds) {

    final boolean isZoneAwareConfig =
        configuration
            .globalConfiguration()
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .isPresent();

    if (isZoneAwareConfig) {
      // AdditiveReassigner does not support zone-aware distribution, so we delegate to the
      // configured distributor instead. ZoneAwareDistribution uses round robin assignment that
      // means the existing tenants are also moved. Additive zone aware reassignment will be a
      // followup.
      return configuration
          .globalConfiguration()
          .partitionDistributorConfig()
          .get()
          .toDistributor()
          .distributePartitions(targetMembers, targetPartitionIds, replicationFactor);
    }

    return reassigner.reassignPartitions(
        configuration, targetMembers, targetPartitionIds, replicationFactor);
  }

  private CurrentClusterConfiguration addGroupAndStartChange(
      final CurrentClusterConfiguration configuration,
      final String newTenantId,
      final List<PartitionGroupOperation> tenantOperations) {
    final var updatedGroups = new HashMap<>(configuration.partitionGroups());
    updatedGroups.put(
        newTenantId,
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION));
    final var withEmptyGroup =
        new CurrentClusterConfiguration(
            configuration.version(),
            configuration.globalConfiguration(),
            updatedGroups,
            configuration.phasedChangeState());

    final List<ClusterConfigurationChangeOperation> operations = List.copyOf(tenantOperations);
    return withEmptyGroup.updatePartitionGroupConfig(
        newTenantId, group -> group.startConfigurationChange(operations));
  }
}
