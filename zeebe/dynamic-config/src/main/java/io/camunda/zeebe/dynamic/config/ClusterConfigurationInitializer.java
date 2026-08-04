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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * <li>When the local configuration is empty, all members query known cluster members for the
 *     current configuration. See {@link SyncInitializer}. If any member replies with a valid
 *     configuration, it uses that one. If no initialized configuration is found before the
 *     bootstrap timeout, the coordinator generates a new configuration from the provided static
 *     configuration. See {@link StaticInitializer}.
 * <li>When the local configuration is empty, a non-coordinating member waits until it receives a
 *     valid configuration from the coordinator via gossip. See {@link GossipInitializer}.
 * <li>After initialization, the configuration can be modified using {@link
 *     ClusterConfigurationModifier}. For example, {@link ExporterStateInitializer} overwrites the
 *     local member's state to keep it in sync with the statically configured exporters.
 */
public interface ClusterConfigurationInitializer<T extends InitializableClusterConfiguration> {
  Logger LOG = LoggerFactory.getLogger(ClusterConfigurationInitializer.class);

  /**
   * Initializes the cluster configuration.
   *
   * @return a future that completes with a configuration which can be initialized or uninitialized
   */
  ActorFuture<T> initialize();

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
  default ClusterConfigurationInitializer<T> orThen(
      final ClusterConfigurationInitializer<T> after) {
    final ClusterConfigurationInitializer<T> actual = this;
    return () -> {
      final ActorFuture<T> chainedInitialize = new CompletableActorFuture<>();
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
  default ClusterConfigurationInitializer<T> andThen(
      final ClusterConfigurationModifier<T> modifier) {
    final ClusterConfigurationInitializer<T> actual = this;
    return () -> {
      final ActorFuture<T> chainedInitialize = new CompletableActorFuture<T>();
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
                  if (modifier.filter().canRunInitializer(configuration)) {
                    modifier.modify(configuration).onComplete(chainedInitialize);
                  } else {
                    chainedInitialize.complete(configuration);
                  }
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
  default ClusterConfigurationInitializer<T> recover(
      final Class<? extends InitializerError> exception,
      final ClusterConfigurationInitializer<T> recovery) {
    final ClusterConfigurationInitializer<T> actual = this;
    return () -> {
      final ActorFuture<T> chainedInitialize = new CompletableActorFuture<>();
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
  class FileInitializer<T extends InitializableClusterConfiguration>
      implements ClusterConfigurationInitializer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileInitializer.class);

    private final Path configurationFile;
    private final ClusterConfigurationSerializer serializer;
    private final ConfigurationReader<T> reader;

    public FileInitializer(
        final Path configurationFile,
        final ClusterConfigurationSerializer serializer,
        final ConfigurationReader<T> reader) {
      this.configurationFile = configurationFile;
      this.serializer = serializer;
      this.reader = reader;
    }

    public static FileInitializer<ClusterConfiguration> legacyFileInitializer(
        final Path configurationFile, final ClusterConfigurationSerializer serializer) {
      return new FileInitializer<>(
          configurationFile,
          serializer,
          (f, s) -> PersistedClusterConfiguration.ofFile(f, s).getConfiguration());
    }

    public static FileInitializer<CurrentClusterConfiguration> fromPersistedConfiguration(
        final Path configurationFile, final ClusterConfigurationSerializer serializer) {
      return new FileInitializer<CurrentClusterConfiguration>(
          configurationFile,
          serializer,
          (f, s) -> PersistedCurrentClusterConfiguration.ofFile(f, s).getConfiguration());
    }

    @Override
    public ActorFuture<T> initialize() {
      try {
        final var persistedTopology = reader.read(configurationFile, serializer);

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

    @FunctionalInterface
    public interface ConfigurationReader<T extends InitializableClusterConfiguration> {
      T read(Path configurationFile, ClusterConfigurationSerializer serializer) throws Exception;
    }
  }

  /**
   * Initializes local configuration from the configuration received from other members via gossip.
   * Initialization completes successfully, when it receives a valid initialized configuration from
   * any member. The future returned by initialize is never completed until a valid configuration is
   * received.
   */
  class GossipInitializer<T extends InitializableClusterConfiguration>
      implements ClusterConfigurationInitializer<T>, ClusterConfigurationUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GossipInitializer.class);
    private final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier;
    private final Supplier<T> persistedConfigurationSupplier;
    private final Consumer<T> configurationGossiper;
    private final ActorFuture<T> initialized;
    private final ConcurrencyControl executor;
    // Used only to discriminate, at runtime, which of the two onClusterConfigurationUpdated
    // overloads carries a T (see SyncInitializer for the same pattern).
    private final T uninitialized;

    public GossipInitializer(
        final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier,
        final Supplier<T> persistedConfigurationSupplier,
        final Consumer<T> configurationGossiper,
        final ConcurrencyControl executor,
        final T uninitialized) {
      this.clusterConfigurationUpdateNotifier = clusterConfigurationUpdateNotifier;
      this.persistedConfigurationSupplier = persistedConfigurationSupplier;
      this.configurationGossiper = configurationGossiper;
      this.executor = executor;
      this.uninitialized = uninitialized;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<T> initialize() {
      LOGGER.debug("Waiting for initial cluster configuration via gossip.");
      clusterConfigurationUpdateNotifier.addUpdateListener(this);
      final var persistedConfiguration = persistedConfigurationSupplier.get();
      if (persistedConfiguration.isUninitialized()) {
        // When uninitialized, the member should gossip uninitialized configuration so that the
        // coordinator is not waiting in SyncInitializer forever.

        // Check persisted cluster configuration directly, so as not to overwrite and concurrently
        // received gossip
        configurationGossiper.accept(persistedConfiguration);
      }
      return initialized;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
      if (uninitialized instanceof ClusterConfiguration) {
        configurationUpdated((T) clusterConfiguration);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onClusterConfigurationUpdated(
        final CurrentClusterConfiguration clusterConfiguration) {
      if (uninitialized instanceof CurrentClusterConfiguration) {
        configurationUpdated((T) clusterConfiguration);
      }
    }

    private void configurationUpdated(final T clusterConfiguration) {
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
   * Initializes configuration by sending sync requests to other members. If any of them returns a
   * valid configuration, it will be initialized. Uninitialized or failed responses are retried
   * until the bootstrap timeout, after which this initializer completes with an uninitialized
   * configuration, letting the caller's initializer chain decide how to proceed.
   */
  class SyncInitializer<T extends InitializableClusterConfiguration>
      implements ClusterConfigurationInitializer<T>, ClusterConfigurationUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncInitializer.class);
    private final Duration syncDelay;
    private final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier;
    private final ActorFuture<T> initialized;
    private final Supplier<List<MemberId>> knownMembersToSync;
    private final Duration bootstrapTimeout;
    private final Set<MemberId> uninitializedMembers = new HashSet<>();
    private final Set<MemberId> requestsInFlight = new HashSet<>();
    private boolean retryScheduled;
    private ScheduledTimer bootstrapTimeoutTimer;
    private final ConcurrencyControl executor;
    private final Function<MemberId, ActorFuture<T>> syncRequester;
    private final T uninitialized;

    SyncInitializer(
        final Duration syncDelay,
        final ClusterConfigurationUpdateNotifier clusterConfigurationUpdateNotifier,
        final Supplier<List<MemberId>> knownMembersToSync,
        final ConcurrencyControl executor,
        final Function<MemberId, ActorFuture<T>> syncRequester,
        final Duration bootstrapTimeout,
        final T uninitialized) {
      this.syncDelay = syncDelay;
      this.clusterConfigurationUpdateNotifier = clusterConfigurationUpdateNotifier;
      this.knownMembersToSync = knownMembersToSync;
      this.executor = executor;
      this.syncRequester = syncRequester;
      this.bootstrapTimeout = bootstrapTimeout;
      this.uninitialized = uninitialized;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<T> initialize() {
      if (knownMembersToSync.get().isEmpty()) {
        completeAsUninitialized("no known members to sync");
      } else {
        LOGGER.debug(
            "Querying members {} before initializing ClusterConfiguration",
            knownMembersToSync.get());
        clusterConfigurationUpdateNotifier.addUpdateListener(this);
        refreshAndSync();
      }
      return initialized;
    }

    private void refreshAndSync() {
      if (initialized.isDone()) {
        return;
      }
      final var members = knownMembersToSync.get();
      uninitializedMembers.retainAll(members);
      members.forEach(this::tryInitializeFrom);
    }

    private void tryInitializeFrom(final MemberId memberId) {
      if (initialized.isDone() || !requestsInFlight.add(memberId)) {
        return;
      }

      requestSync(memberId)
          .onComplete(
              (configuration, error) ->
                  executor.run(() -> handleSyncResponse(memberId, configuration, error)));
    }

    private void handleSyncResponse(
        final MemberId memberId, final T configuration, final Throwable error) {
      requestsInFlight.remove(memberId);
      if (initialized.isDone()) {
        return;
      }
      if (error != null) {
        LOGGER.trace(
            "Failed to get a response for cluster configuration sync query to {}. Will retry.",
            memberId,
            error);
        scheduleRetry();
      } else if (configuration == null) {
        LOGGER.trace("Received null cluster configuration from {}. Will retry.", memberId);
        // A null response (e.g. from a gateway member, which never gossips an explicit
        // uninitialized configuration) must still count towards the bootstrap timeout. Otherwise
        // a cluster with only such members would poll forever without ever falling back.
        armBootstrapTimeout();
        scheduleRetry();
      } else if (configuration.isUninitialized()) {
        LOGGER.trace("Cluster configuration is uninitialized in {}", memberId);
        armBootstrapTimeout();
        uninitializedMembers.add(memberId);
        final var members = knownMembersToSync.get();
        if (uninitializedMembers.containsAll(members)
            && requestsInFlight.stream().noneMatch(members::contains)) {
          completeAsUninitialized("Polled all members and none of them is initialized");
        } else {
          scheduleRetry();
        }
      } else {
        LOGGER.debug("Received cluster configuration {} from {}", configuration, memberId);
        configurationUpdated(configuration);
      }
    }

    private void scheduleRetry() {
      if (initialized.isDone() || retryScheduled) {
        return;
      }
      retryScheduled = true;
      executor.schedule(
          syncDelay,
          () -> {
            retryScheduled = false;
            refreshAndSync();
          });
    }

    private void completeAsUninitialized(final String cause) {
      if (!initialized.isDone()) {
        LOGGER.debug(
            "No initialized cluster configuration found: {}. Completing as uninitialized; "
                + "the initializer chain will decide how to proceed.",
            cause);
        initialized.complete(uninitialized);
        cancelBootstrapTimeout();
        clusterConfigurationUpdateNotifier.removeUpdateListener(this);
      }
    }

    private void armBootstrapTimeout() {
      if (bootstrapTimeoutTimer == null) {
        bootstrapTimeoutTimer =
            executor.schedule(
                bootstrapTimeout,
                () -> completeAsUninitialized("sync timeout (%s)".formatted(bootstrapTimeout)));
      }
    }

    private void cancelBootstrapTimeout() {
      if (bootstrapTimeoutTimer != null) {
        bootstrapTimeoutTimer.cancel();
        bootstrapTimeoutTimer = null;
      }
    }

    private ActorFuture<T> requestSync(final MemberId memberId) {
      return syncRequester.apply(memberId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
      if (uninitialized instanceof ClusterConfiguration) {
        configurationUpdated((T) clusterConfiguration);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onClusterConfigurationUpdated(
        final CurrentClusterConfiguration clusterConfiguration) {
      if (uninitialized instanceof CurrentClusterConfiguration) {
        configurationUpdated((T) clusterConfiguration);
      }
    }

    private void configurationUpdated(final T clusterConfiguration) {
      executor.run(
          () -> {
            if (initialized.isDone()) {
              return;
            }
            if (!clusterConfiguration.isUninitialized()) {
              initialized.complete(clusterConfiguration);
              cancelBootstrapTimeout();
              clusterConfigurationUpdateNotifier.removeUpdateListener(this);
            }
          });
    }
  }

  /** Initialized configuration from the given static partition distribution */
  class StaticInitializer<T extends InitializableClusterConfiguration>
      implements ClusterConfigurationInitializer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInitializer.class);
    private final Supplier<T> configurationSupplier;

    public StaticInitializer(final Supplier<T> configurationSupplier) {
      this.configurationSupplier = configurationSupplier;
    }

    public static StaticInitializer<ClusterConfiguration> legacyStaticInitializer(
        final StaticConfiguration staticConfiguration) {
      return new StaticInitializer<>(staticConfiguration::generateTopology);
    }

    @Override
    public ActorFuture<T> initialize() {
      try {
        final var configuration = configurationSupplier.get();
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
