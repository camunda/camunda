/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.ClusterTopologyManager.InconsistentTopologyListener;
import io.camunda.zeebe.topology.ClusterTopologyManagerService;
import io.camunda.zeebe.topology.changes.PartitionChangeExecutor;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.util.TopologyUtil;
import java.nio.file.Path;
import java.time.Duration;

public class DynamicClusterTopologyService implements ClusterTopologyService {

  private PartitionDistribution partitionDistribution;

  private ClusterTopologyManagerService clusterTopologyManagerService;

  @Override
  public PartitionDistribution getPartitionDistribution() {
    return partitionDistribution;
  }

  @Override
  public void registerPartitionChangeExecutor(final PartitionChangeExecutor executor) {
    if (clusterTopologyManagerService != null) {
      clusterTopologyManagerService.registerPartitionChangeExecutor(executor);
    } else {
      throw new IllegalStateException(
          "Cannot register partition change executor before the topology manager is started");
    }
  }

  @Override
  public void removePartitionChangeExecutor() {
    if (clusterTopologyManagerService != null) {
      clusterTopologyManagerService.removePartitionChangeExecutor();
    }
  }

  @Override
  public ActorFuture<Void> start(final BrokerStartupContext brokerStartupContext) {
    final CompletableActorFuture<Void> started = new CompletableActorFuture<>();

    clusterTopologyManagerService = getClusterTopologyManagerService(brokerStartupContext);

    final var topologyManagerStartedFuture =
        startClusterTopologyManager(brokerStartupContext, clusterTopologyManagerService);

    topologyManagerStartedFuture.onComplete(
        (ignore, topologyManagerFailed) -> {
          if (topologyManagerFailed != null) {
            started.completeExceptionally(topologyManagerFailed);
          } else {
            clusterTopologyManagerService.addUpdateListener(
                brokerStartupContext.getBrokerClient().getTopologyManager());
            clusterTopologyManagerService
                .getClusterTopology()
                .onComplete(
                    (topology, error) -> {
                      if (error != null) {
                        started.completeExceptionally(error);
                      } else {
                        try {
                          partitionDistribution =
                              new PartitionDistribution(
                                  TopologyUtil.getPartitionDistributionFrom(
                                      topology, PartitionManagerImpl.GROUP_NAME));
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
  public void registerTopologyChangeListener(final InconsistentTopologyListener listener) {
    if (clusterTopologyManagerService != null) {
      clusterTopologyManagerService.registerTopologyChangedListener(listener);
    } else {
      throw new IllegalStateException(
          "Cannot register topology change listener before the topology manager is started");
    }
  }

  @Override
  public void removeTopologyChangeListener() {
    if (clusterTopologyManagerService != null) {
      clusterTopologyManagerService.removeTopologyChangedListener();
    }
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    partitionDistribution = null;
    if (clusterTopologyManagerService != null) {
      return clusterTopologyManagerService.closeAsync();
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  private static ActorFuture<Void> startClusterTopologyManager(
      final BrokerStartupContext brokerStartupContext,
      final ClusterTopologyManagerService clusterTopologyManagerService) {
    final BrokerCfg brokerConfiguration = brokerStartupContext.getBrokerConfiguration();
    final var localMember =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();

    final var staticConfiguration =
        PartitionDistributionResolver.getStaticConfiguration(
            brokerConfiguration.getCluster(),
            brokerConfiguration.getExperimental().getPartitioning(),
            localMember);

    return clusterTopologyManagerService.start(
        brokerStartupContext.getActorSchedulingService(), staticConfiguration);
  }

  private ClusterTopologyManagerService getClusterTopologyManagerService(
      final BrokerStartupContext brokerStartupContext) {
    final var rootDirectory =
        Path.of(brokerStartupContext.getBrokerConfiguration().getData().getDirectory());
    return new ClusterTopologyManagerService(
        rootDirectory,
        brokerStartupContext.getClusterServices().getCommunicationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        getDefaultClusterTopologyGossipConfig(), // TODO: allow user specified config
        brokerStartupContext.getMeterRegistry());
  }

  private ClusterTopologyGossiperConfig getDefaultClusterTopologyGossipConfig() {
    return new ClusterTopologyGossiperConfig(
        true, Duration.ofSeconds(10), Duration.ofSeconds(2), 2);
  }
}
