/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes a {@link CurrentClusterConfiguration} (the new multi-partition-group model), used by
 * {@link ClusterConfigurationManagerImpl} when running behind the {@code USE_NEW_CONFIG} feature
 * flag.
 *
 * <h4>Migration status (intermediate step)</h4>
 *
 * Unlike {@link ClusterConfigurationInitializer}, this interface currently has:
 *
 * <ul>
 *   <li>no {@code FileInitializer} counterpart (recovering a persisted configuration on restart)
 *   <li>no {@code GossipInitializer}/{@code SyncInitializer} counterparts (recovering the
 *       configuration from peers on bootstrap)
 *   <li>no chaining helpers ({@code orThen}/{@code andThen}/{@code recover})
 * </ul>
 *
 * {@link StaticInitializer} is the only implementation, and it is used unconditionally for both the
 * coordinator and non-coordinator role: every member independently (re)generates the same
 * configuration from static configuration on every start, instead of first trying to recover its
 * previously persisted or gossiped configuration. Since generation is a deterministic function of
 * the (identical, statically configured) cluster membership and partition distribution, every
 * member computes the same result.
 *
 * <p><b>TODO (follow-up):</b> add the remaining initializers and the chaining behavior, mirroring
 * {@link ClusterConfigurationInitializer}, so that a restarting broker resumes from its last known
 * configuration (file, then gossip/sync) instead of always regenerating it from static
 * configuration.
 */
public interface CurrentClusterConfigurationInitializer {

  Logger LOG = LoggerFactory.getLogger(CurrentClusterConfigurationInitializer.class);

  /**
   * Initializes the cluster configuration.
   *
   * @return a future that completes with the generated configuration
   */
  ActorFuture<CurrentClusterConfiguration> initialize();

  /** Initializes configuration from the given static partition distribution. */
  class StaticInitializer implements CurrentClusterConfigurationInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInitializer.class);

    private final StaticConfiguration staticConfiguration;

    public StaticInitializer(final StaticConfiguration staticConfiguration) {
      this.staticConfiguration = staticConfiguration;
    }

    @Override
    public ActorFuture<CurrentClusterConfiguration> initialize() {
      try {
        final var configuration = staticConfiguration.generateCurrentClusterConfiguration();
        LOGGER.debug(
            "Generated multi-group cluster configuration from provided static configuration. {}",
            configuration);
        return CompletableActorFuture.completed(configuration);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }
  }
}
