/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the {@link PartitionDistributorConfig} from the local static configuration if it has
 * not already been set. Once the cluster has agreed on a distributor config via gossip, subsequent
 * restarts read it from the persisted state instead of falling back to static configuration.
 *
 * <p>Works with either the legacy {@link ClusterConfiguration} (where the distributor config lives
 * directly on the configuration) or the new {@link CurrentClusterConfiguration} (where it lives on
 * the cluster-wide {@code GlobalConfiguration}, since the partition distributor is not scoped to
 * any single partition group) via the injected accessor functions. See {@link
 * #legacyPartitionDistributorInitializer(StaticConfiguration)} and {@link
 * #currentClusterConfigurationPartitionDistributorInitializer(StaticConfiguration)}.
 */
@NullMarked
public class PartitionDistributorInitializer<T extends InitializableClusterConfiguration>
    extends ClusterConfigurationModifier.CoordinatorOnly<T> {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionDistributorInitializer.class);

  private final StaticConfiguration staticConfiguration;
  private final Function<T, Optional<PartitionDistributorConfig>> configGetter;
  private final BiFunction<T, PartitionDistributorConfig, T> configSetter;

  public PartitionDistributorInitializer(
      final StaticConfiguration staticConfiguration,
      final Function<T, Optional<PartitionDistributorConfig>> configGetter,
      final BiFunction<T, PartitionDistributorConfig, T> configSetter) {
    super(staticConfiguration.localMemberId());
    this.staticConfiguration = staticConfiguration;
    this.configGetter = configGetter;
    this.configSetter = configSetter;
  }

  public static PartitionDistributorInitializer<ClusterConfiguration>
      legacyPartitionDistributorInitializer(final StaticConfiguration staticConfiguration) {
    return new PartitionDistributorInitializer<>(
        staticConfiguration,
        ClusterConfiguration::partitionDistributorConfig,
        ClusterConfiguration::setPartitionDistributorConfig);
  }

  public static PartitionDistributorInitializer<CurrentClusterConfiguration>
      currentClusterConfigurationPartitionDistributorInitializer(
          final StaticConfiguration staticConfiguration) {
    return new PartitionDistributorInitializer<>(
        staticConfiguration,
        configuration -> configuration.globalConfiguration().partitionDistributorConfig(),
        (configuration, config) ->
            configuration.updateGlobalConfiguration(
                global -> global.setPartitionDistributorConfig(config)));
  }

  @Override
  public ActorFuture<T> modify(final T configuration) {
    if (configGetter.apply(configuration).isPresent()) {
      return CompletableActorFuture.completed(configuration);
    }
    final var config = toConfig(staticConfiguration.partitionDistributor());
    return CompletableActorFuture.completed(configSetter.apply(configuration, config));
  }

  private static PartitionDistributorConfig toConfig(final PartitionDistributor distributor) {
    return switch (distributor) {
      case final RoundRobinPartitionDistributor ignored ->
          new PartitionDistributorConfig.RoundRobinConfig();
      case final ZoneAwarePartitionDistributor zoneAware ->
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
