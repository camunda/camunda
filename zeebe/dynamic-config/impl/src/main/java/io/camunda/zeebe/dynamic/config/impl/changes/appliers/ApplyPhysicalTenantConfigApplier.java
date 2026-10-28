/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.PhysicalTenantConfigFetcher;
import io.camunda.zeebe.dynamic.config.impl.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ApplyPhysicalTenantConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Applier for {@link ApplyPhysicalTenantConfigOperation}: gets the configuration the tenant's
 * reference names, from this member's disk or from a peer, hands it to the {@link
 * PhysicalTenantConfigChangeExecutor}, and records the version as applied by this member.
 *
 * <p>Peers are asked in this order: the coordinator, which stored the configuration when it
 * accepted the change, then the members that already applied it, then the others.
 */
@NullMarked
public final class ApplyPhysicalTenantConfigApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final String physicalTenantId;
  private final MemberId memberId;
  private final long version;
  private final PhysicalTenantConfigFetcher fetcher;
  private final PhysicalTenantConfigChangeExecutor executor;
  private @Nullable PhysicalTenantConfigReference reference;
  private List<MemberId> peers = List.of();

  public ApplyPhysicalTenantConfigApplier(
      final String physicalTenantId,
      final ApplyPhysicalTenantConfigOperation operation,
      final PhysicalTenantConfigFetcher fetcher,
      final PhysicalTenantConfigChangeExecutor executor) {
    this.physicalTenantId = physicalTenantId;
    memberId = operation.memberId();
    version = operation.version();
    this.fetcher = fetcher;
    this.executor = executor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final var current = currentPartitionGroupConfiguration.tenantConfig().orElse(null);
    if (current == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply the configuration of physical tenant '%s', but it has none"
                  .formatted(physicalTenantId)));
    }
    if (current.version() != version) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply version %d of the configuration of physical tenant '%s', but the"
                  + " cluster configuration names version %d"
                      .formatted(version, physicalTenantId, current.version())));
    }
    reference = current;
    peers = peersToAsk(currentGlobalConfiguration, current);
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
    final var applying = reference;
    if (applying == null) {
      result.completeExceptionally(new IllegalStateException("Applied before initialised"));
      return result;
    }
    fetcher
        .fetch(physicalTenantId, applying, peers)
        .thenCompose(configuration -> executor.apply(physicalTenantId, applying, configuration))
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(
                    group ->
                        group.updateTenantConfig(
                            current -> current.withApplied(memberId, version)));
              }
            });
    return result;
  }

  private List<MemberId> peersToAsk(
      final GlobalConfiguration global, final PhysicalTenantConfigReference current) {
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(global.members().keySet())
            .getDefaultCoordinator();
    return global.members().keySet().stream()
        .filter(member -> !member.equals(memberId))
        .sorted(
            Comparator.comparing((MemberId member) -> !member.equals(coordinator))
                .thenComparing(member -> current.appliedVersion(member) < version)
                .thenComparing(MemberId.ID_COMPARATOR))
        .toList();
  }
}
