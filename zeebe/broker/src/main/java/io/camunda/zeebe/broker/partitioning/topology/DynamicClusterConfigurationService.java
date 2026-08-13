/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.impl.LegacyExportingStateReader;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.rebalance.ProtoBufRebalanceSerializer;
import io.camunda.zeebe.rebalance.RebalanceCoordinator;
import io.camunda.zeebe.rebalance.RebalanceRequestServer;
import io.camunda.zeebe.rebalance.RebalanceRunner;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicClusterConfigurationService
    implements ClusterConfigurationService, ClusterConfigurationUpdateListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DynamicClusterConfigurationService.class);
  private static final int ERROR_CODE_ON_INCONSISTENT_TOPOLOGY = 3;

  private final Map<String, PartitionDistribution> partitionDistributionPerPhysicalTenant =
      new HashMap<>();

  private volatile CurrentClusterConfiguration initialClusterConfiguration;
  private volatile CurrentClusterConfiguration currentClusterConfiguration;

  private ClusterConfigurationManagerService clusterConfigurationManagerService;
  private RebalanceCoordinator rebalanceCoordinator;
  private Actor rebalanceCoordinatorActor;
  private RebalanceRequestServer rebalanceRequestServer;
  private final ClusterChangeExecutor clusterChangeExecutor;

  public DynamicClusterConfigurationService(final ClusterChangeExecutor clusterChangeExecutor) {
    this.clusterChangeExecutor = clusterChangeExecutor;
  }

  @Override
  public PartitionDistribution getPartitionDistribution(final String physicalTenantId) {
    return partitionDistributionPerPhysicalTenant.getOrDefault(
        physicalTenantId, new PartitionDistribution(Set.of()));
  }

  @Override
  public Map<String, PartitionDistribution> getPartitionDistribution() {
    return partitionDistributionPerPhysicalTenant;
  }

  @Override
  public void registerPartitionChangeExecutors(
      final String physicalTenantId,
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final RestoreChangeExecutor restoreChangeExecutor) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerPartitionChangeExecutors(
          physicalTenantId,
          partitionChangeExecutor,
          partitionScalingChangeExecutor,
          restoreChangeExecutor);
    } else {
      throw new IllegalStateException(
          "Cannot register change executor before the topology manager is started");
    }
  }

  @Override
  public void removePartitionChangeExecutor(final String physicalTenantId) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removePartitionChangeExecutor(physicalTenantId);
    }
  }

  @Override
  public void registerModeChangeExecutor(
      final String physicalTenantId, final ModeChangeExecutor modeChangeExecutor) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerModeChangeExecutor(
          physicalTenantId, modeChangeExecutor);
    } else {
      throw new IllegalStateException(
          "Cannot register mode change executor before the topology manager is started");
    }
  }

  @Override
  public void removeModeChangeExecutor(final String physicalTenantId) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removeModeChangeExecutor(physicalTenantId);
    }
  }

  @Override
  public ActorFuture<Void> start(final BrokerStartupContext brokerStartupContext) {
    final CompletableActorFuture<Void> started = new CompletableActorFuture<>();

    clusterConfigurationManagerService = getClusterTopologyManagerService(brokerStartupContext);

    final var topologyManagerStartedFuture =
        startClusterTopologyManager(brokerStartupContext, clusterConfigurationManagerService);

    topologyManagerStartedFuture.onComplete(
        (ignore, topologyManagerFailed) -> {
          if (topologyManagerFailed != null) {
            started.completeExceptionally(topologyManagerFailed);
          } else {
            clusterConfigurationManagerService.addUpdateListener(
                brokerStartupContext.getBrokerClient().getTopologyManager());
            clusterConfigurationManagerService.addUpdateListener(this);
            registerInconsistentConfigurationListener(
                inconsistentConfigurationListener(brokerStartupContext));
            startRebalanceCoordinator(brokerStartupContext);
            clusterConfigurationManagerService
                .getClusterConfiguration()
                .onComplete(
                    (configuration, error) -> {
                      if (error != null) {
                        started.completeExceptionally(error);
                      } else {
                        try {
                          populatePartitionDistribution(configuration);
                          initialClusterConfiguration = configuration;
                          if (currentClusterConfiguration == null) {
                            currentClusterConfiguration = configuration;
                          }
                          started.complete(null);
                        } catch (final Exception topologyConversionFailed) {
                          started.completeExceptionally(topologyConversionFailed);
                        }
                      }
                    });
          }
        });
    return started;
  }

  @Override
  public void registerInconsistentConfigurationListener(
      final InconsistentConfigurationListener listener) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerTopologyChangedListener(listener);
    } else {
      throw new IllegalStateException(
          "Cannot register topology change listener before the topology manager is started");
    }
  }

  @Override
  public void removeInconsistentConfigurationListener() {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removeTopologyChangedListener();
    }
  }

  @Override
  public void registerRequestValidator(
      final @Nullable String physicalTenantId,
      final ClusterConfigurationRequestValidator<?, ?> validator) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerRequestValidator(physicalTenantId, validator);
    } else {
      throw new IllegalStateException(
          "Cannot register a request validator before the topology manager is started");
    }
  }

  @Override
  public void removeRequestValidator(
      final @Nullable String physicalTenantId,
      final Class<? extends ClusterConfigurationManagementRequest> requestType) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removeRequestValidator(physicalTenantId, requestType);
    }
  }

  @Override
  public CurrentClusterConfiguration getInitialClusterConfiguration() {
    return initialClusterConfiguration;
  }

  @Override
  public CurrentClusterConfiguration getCurrentClusterConfiguration() {
    return currentClusterConfiguration;
  }

  @Override
  public ClusterChangeExecutor getClusterChangeExecutor() {
    return clusterChangeExecutor;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> getLatestClusterConfiguration() {
    if (clusterConfigurationManagerService != null) {
      return clusterConfigurationManagerService.getClusterConfiguration();
    } else {
      final CompletableActorFuture<CurrentClusterConfiguration> future =
          new CompletableActorFuture<>();
      future.completeExceptionally(
          new IllegalStateException("ClusterConfigurationService is not started"));
      return future;
    }
  }

  private void populatePartitionDistribution(final CurrentClusterConfiguration configuration) {
    final Map<String, Set<PartitionMetadata>> perPtConfig =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(configuration);
    partitionDistributionPerPhysicalTenant.putAll(
        perPtConfig.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> new PartitionDistribution(entry.getValue()))));
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    partitionDistributionPerPhysicalTenant.clear();
    currentClusterConfiguration = null;
    removeInconsistentConfigurationListener();
    if (rebalanceRequestServer != null) {
      rebalanceRequestServer.close();
    }
    if (rebalanceCoordinator != null && clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removeUpdateListener(rebalanceCoordinator);
    }
    final ActorFuture<Void> rebalanceCoordinatorClosed =
        rebalanceCoordinatorActor != null
            ? rebalanceCoordinatorActor.closeAsync()
            : CompletableActorFuture.completed(null);
    if (clusterConfigurationManagerService != null) {
      return rebalanceCoordinatorClosed.andThen(
          clusterConfigurationManagerService::closeAsync, Runnable::run);
    } else {
      return rebalanceCoordinatorClosed;
    }
  }

  /**
   * Builds the listener that shuts the broker down when its own state was changed in the local
   * configuration without its participation, e.g. by a force-* operation while this broker was
   * unreachable. Registered once per broker in {@link #start(BrokerStartupContext)}, covering every
   * physical tenant's partition group, rather than once per {@code PartitionManagerStep} gated to
   * the default tenant only.
   */
  private InconsistentConfigurationListener inconsistentConfigurationListener(
      final BrokerStartupContext brokerStartupContext) {
    final var memberId =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();
    final var springBrokerBridge = brokerStartupContext.getSpringBrokerBridge();

    return new InconsistentConfigurationListener() {
      @Override
      public void onInconsistentConfiguration(
          final ClusterConfiguration newTopology, final ClusterConfiguration oldTopology) {
        shutdownOnInconsistentTopology(memberId, springBrokerBridge, newTopology, oldTopology);
      }

      @Override
      public void onInconsistentConfiguration(
          final CurrentClusterConfiguration newConfiguration,
          final CurrentClusterConfiguration oldConfiguration) {
        LOGGER.warn(
            "Received a newer cluster configuration which differs for this broker across partition groups. Shutting down broker. oldVersion={}, newVersion={}",
            oldConfiguration.version(),
            newConfiguration.version());
        springBrokerBridge.initiateShutdown(
            ERROR_CODE_ON_INCONSISTENT_TOPOLOGY,
            "Inconsistent cluster topology detected - topology was changed while broker was"
                + " unreachable or broker encountered data loss");
      }
    };
  }

  private void shutdownOnInconsistentTopology(
      final MemberId memberId,
      final SpringBrokerBridge springBrokerBridge,
      final ClusterConfiguration newTopology,
      final ClusterConfiguration oldTopology) {
    LOGGER.warn(
        """
          Received a newer topology which has a different state for this broker.
          State of this broker in new topology :'{}'
          State of this broker in old topology: '{}'
          This usually happens when the topology was changed forcefully when this broker was unreachable or this broker encountered a data loss. Shutting down the broker. Please restart the broker to use the new topology.
        """,
        newTopology.getMember(memberId),
        oldTopology.getMember(memberId));
    springBrokerBridge.initiateShutdown(
        ERROR_CODE_ON_INCONSISTENT_TOPOLOGY,
        "Inconsistent cluster topology detected - topology was changed while broker was"
            + " unreachable or broker encountered data loss");
  }

  private static ActorFuture<Void> startClusterTopologyManager(
      final BrokerStartupContext brokerStartupContext,
      final ClusterConfigurationManagerService clusterConfigurationManagerService) {
    final BrokerCfg brokerConfiguration = brokerStartupContext.getBrokerConfiguration();
    final var localMember =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();

    final Map<String, BrokerCfg> physicalTenantConfigs =
        brokerStartupContext.getPhysicalTenantIds().known().stream()
            .collect(
                Collectors.toMap(
                    id -> id, id -> brokerStartupContext.getPhysicalTenantContext(id).config()));

    final var staticConfiguration =
        StaticConfigurationGenerator.getStaticConfiguration(
            brokerConfiguration, physicalTenantConfigs, localMember);

    final var legacyExportingStates =
        LegacyExportingStateReader.readLegacyExportingStates(
            brokerConfiguration.getData().getDirectory());

    return clusterConfigurationManagerService.start(
        brokerStartupContext.getActorSchedulingService(),
        staticConfiguration,
        legacyExportingStates);
  }

  private ClusterConfigurationManagerService getClusterTopologyManagerService(
      final BrokerStartupContext brokerStartupContext) {
    final var rootDirectory =
        Path.of(brokerStartupContext.getBrokerConfiguration().getData().getDirectory());
    return new ClusterConfigurationManagerService(
        rootDirectory,
        brokerStartupContext.getClusterServices().getCommunicationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        brokerStartupContext.getBrokerConfiguration().getCluster().getConfigManager().gossip(),
        clusterChangeExecutor,
        brokerStartupContext.getMeterRegistry());
  }

  private void startRebalanceCoordinator(final BrokerStartupContext brokerStartupContext) {
    final var localMember =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();
    rebalanceCoordinatorActor = Actor.newActor().name("RebalanceCoordinator").build();
    brokerStartupContext
        .getActorSchedulingService()
        .submitActor(rebalanceCoordinatorActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                LOGGER.error("Failed to start the rebalance coordinator actor", error);
                return;
              }
              rebalanceCoordinator =
                  new RebalanceCoordinator(
                      localMember,
                      rebalanceCoordinatorActor,
                      RebalanceRunner.none(),
                      () -> brokerStartupContext.getRequestIdGenerator().nextId());
              rebalanceRequestServer =
                  new RebalanceRequestServer(
                      brokerStartupContext.getClusterServices().getCommunicationService(),
                      new ProtoBufRebalanceSerializer(),
                      rebalanceCoordinator);
              rebalanceRequestServer.start();
              clusterConfigurationManagerService.addUpdateListener(rebalanceCoordinator);
            });
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    // NOOP
  }

  @Override
  public void onClusterConfigurationUpdated(
      final CurrentClusterConfiguration clusterConfiguration) {
    currentClusterConfiguration = clusterConfiguration;
  }
}
