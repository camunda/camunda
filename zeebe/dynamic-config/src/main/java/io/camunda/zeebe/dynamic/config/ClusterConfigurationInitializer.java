/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.InitializerError.PersistedConfigurationIsBroken;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes configuration using different strategies.
 *
 * <h4>Initialization Process</h4>
 *
 * Each member is configured with a static configuration with initial set of cluster members and
 * partition distribution.
 *
 * <p>Both coordinator and other members first check the local persisted configuration to determine
 * the configuration. If one exists, that is used to initialize the configuration. See {@link
 * FileInitializer}. On bootstrap of the cluster, the local persisted configuration is empty.
 * <li>When the local configuration is empty, the coordinator queries cluster members in the static
 *     configuration for the current configuration. See {@link SyncInitializer}. If any member
 *     replies with a valid configuration, coordinator uses that one. If any member replies with an
 *     uninitialized configuration, coordinator generates a new configuration from the provided
 *     static configuration. See {@link StaticInitializer}.
 * <li>When the local configuration is empty, a non-coordinating member waits until it receives a
 *     valid configuration from the coordinator via gossip. See {@link GossipInitializer}.
 * <li>After initialization, the configuration can be modified using {@link
 *     ClusterConfigurationModifier}. For example, {@link ExporterStateInitializer} overwrites the
 *     local member's state to keep it in sync with the statically configured exporters.
 */
public interface ClusterConfigurationInitializer {
  Logger LOG = LoggerFactory.getLogger(ClusterConfigurationInitializer.class);

  /**
   * Initializes the cluster configuration.
   *
   * @return a future that completes with a configuration which can be initialized or uninitialized
   */
  ActorFuture<ClusterConfiguration> initialize();

  /**
   * Chain initializers in oder. If this initializer returns an uninitialized configuration, the
   * provided initializer is tried instead. If this initializer completes exceptionally, the
   * exceptions propagates. See {@link #recover(Class, ClusterConfigurationInitializer)} to handle
   * exceptions.
   *
   * @param after the next initializer used to initialize configuration if the current one did not
   *     succeed with an initialized configuration.
   * @return a chained ClusterConfigurationInitializer
   */
  default ClusterConfigurationInitializer orThen(final ClusterConfigurationInitializer after) {
    final ClusterConfigurationInitializer actual = this;
    return () -> {
      final ActorFuture<ClusterConfiguration> chainedInitialize = new CompletableActorFuture<>();
      actual
          .initialize()
          .onComplete(
              (configuration, error) -> {
                if (error != null) {
                  LOG.error("Failed to initialize configuration", error);
                  chainedInitialize.completeExceptionally(error);
                } else if (configuration.isUninitialized()) {
                  after.initialize().onComplete(chainedInitialize);
                } else {
                  chainedInitialize.complete(configuration);
                }
              });
      return chainedInitialize;
    };
  }

  /**
   * Chain a modifier that updates the configuration after it is initialized.
   *
   * @param modifier a modifier that updates the configuration
   * @return the chained initializer
   */
  default ClusterConfigurationInitializer andThen(final ClusterConfigurationModifier modifier) {
    final ClusterConfigurationInitializer actual = this;
    return () -> {
      final ActorFuture<ClusterConfiguration> chainedInitialize = new CompletableActorFuture<>();
      actual
          .initialize()
          .onComplete(
              (configuration, error) -> {
                if (error != null) {
                  LOG.error("Failed to initialize configuration", error);
                  chainedInitialize.completeExceptionally(error);
                } else if (configuration.isUninitialized()) {
                  // Do not modify uninitialized configuration. ClusterConfigurationManager will
                  // fail anyway when it sees an uninitialized configuration.
                  chainedInitialize.complete(configuration);
                } else {
                  modifier.modify(configuration).onComplete(chainedInitialize);
                }
              });
      return chainedInitialize;
    };
  }

  /**
   * If this initializer completed exceptionally with the given exception, the recovery initializer
   * is used instead. If this initializer completed exceptionally with a different exception, the
   * recovery is not used and the exception is propagated.
   *
   * @param exception The class of the exceptions to recover from. If the exception is assignable
   *     from the given class, the recovery initializer is used.
   * @param recovery A regular {@link ClusterConfigurationInitializer}.
   * @return a {@link ClusterConfigurationInitializer} that can be used for further chaining with
   *     {@link #orThen(ClusterConfigurationInitializer)}.
   */
  default ClusterConfigurationInitializer recover(
      final Class<? extends InitializerError> exception,
      final ClusterConfigurationInitializer recovery) {
    final ClusterConfigurationInitializer actual = this;
    return () -> {
      final ActorFuture<ClusterConfiguration> chainedInitialize = new CompletableActorFuture<>();
      actual
          .initialize()
          .onComplete(
              (configuration, error) -> {
                if (error != null && exception.isAssignableFrom(error.getClass())) {
                  LOG.warn("Recovering from {} by falling back to {}", error, recovery);
                  recovery.initialize().onComplete(chainedInitialize);
                } else if (error != null) {
                  chainedInitialize.completeExceptionally(error);
                } else {
                  chainedInitialize.complete(configuration);
                }
              });
      return chainedInitialize;
    };
  }

  /** Initialized configuration from the locally persisted configuration */
  class FileInitializer implements ClusterConfigurationInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileInitializer.class);

    private final Path configurationFile;
    private final ClusterConfigurationSerializer serializer;

    public FileInitializer(
        final Path configurationFile, final ClusterConfigurationSerializer serializer) {
      this.configurationFile = configurationFile;
      this.serializer = serializer;
    }

    @Override
    public ActorFuture<ClusterConfiguration> initialize() {
      try {
        final var persistedTopology =
            PersistedClusterConfiguration.ofFile(configurationFile, serializer).getConfiguration();
        if (!persistedTopology.isUninitialized()) {
          LOGGER.debug(
              "Initialized cluster configuration '{}' from file '{}'",
              persistedTopology,
              configurationFile);
        }
        return CompletableActorFuture.completed(persistedTopology);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(
            new PersistedConfigurationIsBroken(configurationFile, e));
      }
    }
  }

  /**
   * Initializes local configuration from the configuration received from other members via gossip.
   * Initialization completes successfully, when it receives a valid initialized configuration from
   * any member. The future returned by initialize is never completed until a valid configuration is
   * received.
   */
  class GossipInitializer
      implements ClusterConfigurationInitializer, ClusterConfigurationUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GossipInitializer.class);
    private final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier;
    private final PersistedClusterConfiguration persistedClusterConfiguration;
    private final Consumer<ClusterConfiguration> configurationGossiper;
    private final ActorFuture<ClusterConfiguration> initialized;

    private final ConcurrencyControl executor;

    public GossipInitializer(
        final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier,
        final PersistedClusterConfiguration persistedClusterConfiguration,
        final Consumer<ClusterConfiguration> configurationGossiper,
        final ConcurrencyControl executor) {
      this.clusterConfigurationUpdateNotifier = clusterConfigurationUpdateNotifier;
      this.persistedClusterConfiguration = persistedClusterConfiguration;
      this.configurationGossiper = configurationGossiper;
      this.executor = executor;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<ClusterConfiguration> initialize() {
      LOGGER.debug("Waiting for initial cluster configuration via gossip.");
      clusterConfigurationUpdateNotifier.addUpdateListener(this);
      if (persistedClusterConfiguration.isUninitialized()) {
        // When uninitialized, the member should gossip uninitialized configuration so that the
        // coordinator is not waiting in SyncInitializer forever.

        // Check persisted cluster configuration directly, so as not to overwrite and concurrently
        // received gossip
        configurationGossiper.accept(persistedClusterConfiguration.getConfiguration());
      }
      return initialized;
    }

    @Override
    public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
      executor.run(
          () -> {
            if (initialized.isDone()) {
              return;
            }
            if (!clusterConfiguration.isUninitialized()) {
              LOGGER.debug("Received cluster configuration {} via gossip.", clusterConfiguration);
              initialized.complete(clusterConfiguration);
              clusterConfigurationUpdateNotifier.removeUpdateListener(this);
            }
          });
    }
  }

  /**
   * Initializes configuration by sending sync requests to other members. If any of them return a
   * valid configuration, it will be initialized. If any of them returns an uninitialized
   * configuration, the future returned by initialize completes as failed.
   */
  class SyncInitializer
      implements ClusterConfigurationInitializer, ClusterConfigurationUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncInitializer.class);
    private static final Duration SYNC_QUERY_RETRY_DELAY = Duration.ofSeconds(5);
    private final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier;
    private final ActorFuture<ClusterConfiguration> initialized;
    private final List<MemberId> knownMembersToSync;
    private final ConcurrencyControl executor;
    private final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester;

    public SyncInitializer(
        final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier,
        final List<MemberId> knownMembersToSync,
        final ConcurrencyControl executor,
        final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester) {
      this.clusterConfigurationUpdateNotifier = clusterConfigurationUpdateNotifier;
      this.knownMembersToSync = knownMembersToSync;
      this.executor = executor;
      this.syncRequester = syncRequester;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<ClusterConfiguration> initialize() {
      if (knownMembersToSync.isEmpty()) {
        initialized.complete(ClusterConfiguration.uninitialized());
      } else {
        LOGGER.debug(
            "Querying members {} before initializing ClusterConfiguration", knownMembersToSync);
        clusterConfigurationUpdateNotifier.addUpdateListener(this);
        knownMembersToSync.forEach(this::tryInitializeFrom);
      }
      return initialized;
    }

    private void tryInitializeFrom(final MemberId memberId) {
      requestSync(memberId)
          .onComplete(
              (configuration, error) -> {
                if (initialized.isDone()) {
                  return;
                }
                if (error != null) {
                  LOGGER.trace(
                      "Failed to get a response for cluster configuration sync query to {}. Will retry.",
                      memberId,
                      error);
                } else if (configuration == null) {
                  LOGGER.trace(
                      "Received null cluster configuration from {}. Will retry.", memberId);
                } else if (configuration.isUninitialized()) {
                  LOGGER.trace("Cluster configuration is uninitialized in {}", memberId);
                  initialized.complete(configuration);
                  return;
                } else {
                  LOGGER.debug(
                      "Received cluster configuration {} from {}", configuration, memberId);
                  onClusterConfigurationUpdated(configuration);
                  return;
                }
                // retry
                if (!initialized.isDone()) {
                  executor.schedule(SYNC_QUERY_RETRY_DELAY, () -> tryInitializeFrom(memberId));
                }
              });
    }

    private ActorFuture<ClusterConfiguration> requestSync(final MemberId memberId) {
      return syncRequester.apply(memberId);
    }

    @Override
    public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
      executor.run(
          () -> {
            if (initialized.isDone()) {
              return;
            }
            if (!clusterConfiguration.isUninitialized()) {
              initialized.complete(clusterConfiguration);
              clusterConfigurationUpdateNotifier.removeUpdateListener(this);
            }
          });
    }
  }

  /** Initialized configuration from the given static partition distribution */
  class StaticInitializer implements ClusterConfigurationInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInitializer.class);

    private final StaticConfiguration staticConfiguration;

    public StaticInitializer(final StaticConfiguration staticConfiguration) {
      this.staticConfiguration = staticConfiguration;
    }

    @Override
    public ActorFuture<ClusterConfiguration> initialize() {
      try {
        final var configuration = staticConfiguration.generateTopology();
        LOGGER.debug(
            "Generated cluster configuration from provided configuration. {}", configuration);
        return CompletableActorFuture.completed(configuration);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }
  }

  sealed interface InitializerError permits PersistedConfigurationIsBroken {
    final class PersistedConfigurationIsBroken extends RuntimeException
        implements InitializerError {

      public PersistedConfigurationIsBroken(final Path file, final Throwable cause) {
        super("File %s is corrupted".formatted(file), cause);
      }
    }
  }
}
