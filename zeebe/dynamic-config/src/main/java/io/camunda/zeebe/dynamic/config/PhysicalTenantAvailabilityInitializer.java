/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disables a physical tenant on the coordinator once it is no longer present in the local static
 * configuration, and re-enables it if it reappears — without ever removing its {@link
 * PartitionGroupConfiguration} or touching its partition assignment, so a re-added tenant resumes
 * where it left off.
 *
 * <p>Runs on every configuration initialization, comparing every existing group in {@link
 * CurrentClusterConfiguration#partitionGroups()} against the coordinator's own local {@link
 * StaticConfiguration#partitionIds()}. This mirrors {@link PhysicalTenantProvisioningInitializer}'s
 * existing reliance on the coordinator's own local configuration as the source of truth for "which
 * physical tenants are configured" — there is no cross-broker aggregation of this information
 * anywhere in the system, so a rolling config change can move coordinatorship to a broker with a
 * different local configuration and flip a tenant's availability accordingly. This is an accepted,
 * pre-existing class of hazard for this subsystem, not one introduced here.
 *
 * <p>Deliberately a separate modifier from {@link PhysicalTenantProvisioningInitializer} rather
 * than a change to it: the two operate on disjoint key sets (provisioning only ever touches tenant
 * ids missing from {@code partitionGroups()}; this only ever touches ids present in it) and have
 * different failure shapes (provisioning can fail an entire batch on a reassignment computation;
 * toggling a flag cannot fail that way).
 */
public class PhysicalTenantAvailabilityInitializer
    extends ClusterConfigurationModifier.CoordinatorOnly<CurrentClusterConfiguration> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantAvailabilityInitializer.class);

  private final Set<String> staticTenantIds;

  public PhysicalTenantAvailabilityInitializer(final StaticConfiguration staticConfiguration) {
    super(staticConfiguration.localMemberId());
    staticTenantIds =
        staticConfiguration.partitionIds().stream()
            .map(PartitionId::group)
            .collect(Collectors.toSet());
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    var result = configuration;
    for (final var groupId : configuration.partitionGroups().keySet()) {
      final boolean isKnownLocally = staticTenantIds.contains(groupId);
      if (isKnownLocally && configuration.partitionGroup(groupId).isRemoved()) {
        LOG.warn(
            "Physical tenant '{}' is listed in the local static configuration, but it was "
                + "explicitly removed; removal is permanent, so re-adding '{}' to the static "
                + "configuration has no effect. Delete it from the static configuration.",
            groupId,
            groupId);
      }
      result =
          result.updatePartitionGroupConfig(
              groupId,
              isKnownLocally
                  ? PartitionGroupConfiguration::enable
                  : PartitionGroupConfiguration::disable);
    }
    return CompletableActorFuture.completed(result);
  }
}
