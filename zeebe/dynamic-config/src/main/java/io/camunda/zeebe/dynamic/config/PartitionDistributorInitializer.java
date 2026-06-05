/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the {@link PartitionDistributorConfig} of the cluster configuration from the local
 * static configuration if it has not already been set. Once the cluster has agreed on a distributor
 * config via gossip, subsequent restarts read it from the persisted state instead of falling back
 * to static configuration.
 */
@NullMarked
public class PartitionDistributorInitializer implements ClusterConfigurationModifier {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionDistributorInitializer.class);

  private final StaticConfiguration staticConfiguration;

  public PartitionDistributorInitializer(final StaticConfiguration staticConfiguration) {
    this.staticConfiguration = staticConfiguration;
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    if (configuration.partitionDistributorConfig().isPresent()) {
      return CompletableActorFuture.completed(configuration);
    }
    final var config = toConfig(staticConfiguration.partitionDistributor());
    return CompletableActorFuture.completed(configuration.setPartitionDistributorConfig(config));
  }

  private static PartitionDistributorConfig toConfig(final PartitionDistributor distributor) {
    return switch (distributor) {
      case final RoundRobinPartitionDistributor ignored ->
          new PartitionDistributorConfig.RoundRobinConfig();
      case final ZoneAwarePartitionDistributor zoneAware ->
          new PartitionDistributorConfig.ZoneAwareConfig(
              zoneAware.zoneSpecs().stream()
                  .map(
                      z ->
                          new PartitionDistributorConfig.ZoneSpec(
                              z.name(), z.numberOfReplicas(), z.priority()))
                  .toList());
          new PartitionDistributorConfig.ZoneAwareConfig(zoneAware.zoneSpecs());
      // TODO: FixedPartitionDistributor is not in this module: we need to move it to this module as
      // well and just use PartitionDistributorConfig directly in StaticConfiguration instead
      default -> {
        LOG.warn(
            "Unknown PartitionDistributor type '{}'; storing as FixedConfig",
            distributor.getClass().getSimpleName());
        yield new PartitionDistributorConfig.FixedConfig();
      }
    };
  }
}
