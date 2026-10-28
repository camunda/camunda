/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.StaticConfiguration;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PhysicalTenantConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ApplyPhysicalTenantConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.StopPhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Groups;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a {@link PhysicalTenantConfigRequest} into the change of the cluster configuration that the
 * coordinator starts: the tenant's {@link PhysicalTenantConfigReference} moves to the new version,
 * and the tenant's group starts a rolling change that runs the tenant with that version on every
 * live member, one member at a time.
 *
 * <ul>
 *   <li>Create: a tenant without a group gets one, its partitions are placed like a tenant
 *       provisioned at startup, and they are bootstrapped once every member runs the tenant. A
 *       tenant provisioned at startup from the file is registered with its first version and no
 *       change: it already runs, and each member records the version once it sees that it runs the
 *       same configuration.
 *   <li>Update: the reference moves to the next version; a configuration with the same hash changes
 *       nothing.
 *   <li>Delete: the tenant is disabled, stopped on every member, and then removed. Its data stays
 *       on disk and its id stays reserved.
 * </ul>
 *
 * <p>A tenant is changed only through the input that owns it. Only one tenant changes at a time
 * across the cluster: a request that arrives while another tenant change runs is rejected, not
 * queued.
 */
@NullMarked
public final class PhysicalTenantConfigChanges {

  private final MemberId coordinator;
  private final Supplier<String> incarnationIds;

  public PhysicalTenantConfigChanges(
      final MemberId coordinator, final Supplier<String> incarnationIds) {
    this.coordinator = coordinator;
    this.incarnationIds = incarnationIds;
  }

  public Either<Exception, CurrentClusterConfiguration> apply(
      final CurrentClusterConfiguration configuration,
      final PhysicalTenantConfigRequest request,
      final String sha256) {
    final var tenantId = request.physicalTenantId();
    final var inProgress = changeInProgress(configuration, tenantId);
    if (inProgress.isPresent()) {
      return Either.left(new ConcurrentModificationException(inProgress.get()));
    }
    return switch (request.change()) {
      case CREATE -> create(configuration, request, sha256);
      case UPDATE -> update(configuration, request, sha256);
      case DELETE -> delete(configuration, request);
    };
  }

  private Either<Exception, CurrentClusterConfiguration> create(
      final CurrentClusterConfiguration configuration,
      final PhysicalTenantConfigRequest request,
      final String sha256) {
    final var tenantId = request.physicalTenantId();
    final var group = configuration.partitionGroup(tenantId);
    if (group != null && group.isRemoved()) {
      return Either.left(
          new InvalidRequest(
              "Physical tenant '%s' was removed; its id stays reserved until its data is purged"
                  .formatted(tenantId)));
    }
    if (group != null && (group.tenantConfig().isPresent() || request.owner() != Owner.FILE)) {
      return Either.left(
          new InvalidRequest("Physical tenant '%s' already exists".formatted(tenantId)));
    }

    final var reference =
        PhysicalTenantConfigReference.initial(sha256, request.owner(), incarnationIds.get());
    if (group != null) {
      return Either.right(
          configuration.updatePartitionGroupConfig(tenantId, g -> g.setTenantConfig(reference)));
    }
    final var applyOperations = applyOperations(configuration, reference.version());

    if (request.partitionCount() < 1) {
      return Either.left(
          new InvalidRequest(
              "Expected a new physical tenant to have at least one partition, but '%s' has %d"
                  .formatted(tenantId, request.partitionCount())));
    }
    final List<PartitionGroupOperation> placement;
    try {
      placement = placementOperations(configuration, request);
    } catch (final RuntimeException e) {
      return Either.left(new InvalidRequest(e));
    }
    final var operations = new ArrayList<PartitionGroupOperation>(applyOperations);
    operations.addAll(placement);
    final var withGroup =
        configuration
            .initPartitionGroup(tenantId)
            .updatePartitionGroupConfig(
                tenantId,
                g ->
                    g.setRoutingState(
                        RoutingState.initializeWithPartitionCount(request.partitionCount())));
    return Either.right(
        startChange(withGroup, tenantId, g -> g.setTenantConfig(reference), operations));
  }

  private Either<Exception, CurrentClusterConfiguration> update(
      final CurrentClusterConfiguration configuration,
      final PhysicalTenantConfigRequest request,
      final String sha256) {
    final var tenantId = request.physicalTenantId();
    final var current = changeableReference(configuration, request);
    if (current.isLeft()) {
      return Either.left(current.getLeft());
    }
    final var reference = current.get();
    if (request.expectedVersion() != 0 && request.expectedVersion() != reference.version()) {
      return Either.left(
          new ConcurrentModificationException(
              "Expected physical tenant '%s' at version %d, but it is at version %d"
                  .formatted(tenantId, request.expectedVersion(), reference.version())));
    }
    if (reference.sha256().equals(sha256)) {
      return Either.right(configuration);
    }
    final var next = reference.next(sha256);
    return Either.right(
        startChange(
            configuration,
            tenantId,
            g -> g.setTenantConfig(next),
            applyOperations(configuration, next.version())));
  }

  private Either<Exception, CurrentClusterConfiguration> delete(
      final CurrentClusterConfiguration configuration, final PhysicalTenantConfigRequest request) {
    final var current = changeableReference(configuration, request);
    if (current.isLeft()) {
      return Either.left(current.getLeft());
    }
    final var operations = new ArrayList<PartitionGroupOperation>();
    sortedLiveMembers(configuration)
        .forEach(member -> operations.add(new StopPhysicalTenantOperation(member)));
    operations.add(new RemovePhysicalTenantOperation(coordinator));
    return Either.right(
        startChange(
            configuration,
            request.physicalTenantId(),
            PartitionGroupConfiguration::disable,
            operations));
  }

  private Either<Exception, PhysicalTenantConfigReference> changeableReference(
      final CurrentClusterConfiguration configuration, final PhysicalTenantConfigRequest request) {
    final var tenantId = request.physicalTenantId();
    final var group = configuration.partitionGroup(tenantId);
    if (group == null || group.isRemoved() || group.tenantConfig().isEmpty()) {
      return Either.left(new NotFound("Physical tenant '%s' does not exist".formatted(tenantId)));
    }
    final var reference = group.tenantConfig().get();
    if (reference.owner() != request.owner()) {
      return Either.left(
          new OperationNotAllowed(
              "Physical tenant '%s' is owned by %s and cannot be changed through %s"
                  .formatted(tenantId, reference.owner(), request.owner())));
    }
    return Either.right(reference);
  }

  private List<PartitionGroupOperation> placementOperations(
      final CurrentClusterConfiguration configuration, final PhysicalTenantConfigRequest request) {
    final var tenantId = request.physicalTenantId();
    final var exporters =
        request.exporterIds().stream()
            .collect(
                Collectors.toMap(
                    id -> id,
                    id -> new ExporterState(0, ExporterState.State.ENABLED, Optional.empty())));
    final var staticConfiguration =
        new StaticConfiguration(
            new RoundRobinPartitionDistributor(),
            configuration.liveMembers(),
            coordinator,
            IntStream.rangeClosed(1, request.partitionCount())
                .mapToObj(partition -> new PartitionId(tenantId, partition))
                .toList(),
            Math.max(1, configuration.minReplicationFactor()),
            Map.of(
                tenantId,
                new DynamicPartitionConfig(
                    new ExportingConfig(ExportingState.EXPORTING, Map.copyOf(exporters)))),
            configuration.clusterId().orElse(null));
    return new PhysicalTenantProvisioningInitializer(staticConfiguration)
        .placementOperations(configuration, List.of(tenantId))
        .getOrDefault(tenantId, List.of());
  }

  private static CurrentClusterConfiguration startChange(
      final CurrentClusterConfiguration configuration,
      final String tenantId,
      final UnaryOperator<PartitionGroupConfiguration> update,
      final List<? extends PartitionGroupOperation> operations) {
    return configuration.updatePartitionGroupConfig(
        tenantId,
        group ->
            update
                .apply(group)
                .startGraphConfigurationChange(OperationGraph.sequential(List.copyOf(operations))));
  }

  private static List<ApplyPhysicalTenantConfigOperation> applyOperations(
      final CurrentClusterConfiguration configuration, final long version) {
    return sortedLiveMembers(configuration).stream()
        .map(member -> new ApplyPhysicalTenantConfigOperation(member, version))
        .toList();
  }

  private static List<MemberId> sortedLiveMembers(final CurrentClusterConfiguration configuration) {
    return configuration.liveMembers().stream().sorted(MemberId.ID_COMPARATOR).toList();
  }

  private static Optional<String> changeInProgress(
      final CurrentClusterConfiguration configuration, final String tenantId) {
    final var target = configuration.partitionGroup(tenantId);
    if (target != null && target.pendingChanges().isPresent()) {
      return Optional.of(
          "A change of physical tenant '%s' is in progress; retry once it completed"
              .formatted(tenantId));
    }
    final var otherTenant =
        configuration.partitionGroups().entrySet().stream()
            .filter(entry -> isChangingTenantConfig(entry.getValue()))
            .map(Map.Entry::getKey)
            .findFirst();
    if (otherTenant.isPresent()) {
      return Optional.of(
          "A change of physical tenant '%s' is in progress; physical tenants change one at a time"
              .formatted(otherTenant.get()));
    }
    final var scope = new Groups(Set.of(tenantId));
    return configuration.phasedChangeState().pending().values().stream()
        .filter(plan -> PhasedChangePlan.conflicts(scope, plan.scope()))
        .findFirst()
        .map(
            plan ->
                "Configuration change %d involving physical tenant '%s' is in progress"
                    .formatted(plan.id(), tenantId));
  }

  private static boolean isChangingTenantConfig(final PartitionGroupConfiguration group) {
    return group
        .pendingChanges()
        .map(
            plan ->
                plan.pendingOperations().stream()
                    .anyMatch(
                        operation ->
                            operation instanceof ApplyPhysicalTenantConfigOperation
                                || operation instanceof StopPhysicalTenantOperation))
        .orElse(false);
  }
}
