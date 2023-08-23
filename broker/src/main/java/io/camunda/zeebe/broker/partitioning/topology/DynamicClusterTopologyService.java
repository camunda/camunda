/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.ClusterTopologyManagerService;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
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
  public ActorFuture<Void> start(final BrokerStartupContext brokerStartupContext) {
    final CompletableActorFuture<Void> started = new CompletableActorFuture<>();

    final PartitionDistributionResolver partitionDistributionResolver =
        new PartitionDistributionResolver();

    clusterTopologyManagerService = getClusterTopologyManagerService(brokerStartupContext);

    final var topologyManagerStartedFuture =
        startClusterTopologyManager(
            brokerStartupContext, clusterTopologyManagerService, partitionDistributionResolver);

    topologyManagerStartedFuture.onComplete(
        (ignore, topologyManagerFailed) -> {
          if (topologyManagerFailed != null) {
            started.completeExceptionally(topologyManagerFailed);
          } else {
            clusterTopologyManagerService
                .getClusterTopology()
                .onComplete(
                    (topology, error) -> {
                      if (error != null) {
                        started.completeExceptionally(error);
                      } else {
                        try {
                          partitionDistribution =
                              partitionDistributionResolver.resolvePartitionDistribution(topology);
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
      final ClusterTopologyManagerService clusterTopologyManagerService,
      final PartitionDistributionResolver partitionDistributionResolver) {
    final BrokerCfg brokerConfiguration = brokerStartupContext.getBrokerConfiguration();
    final var allMembers =
        PartitionDistributionResolver.getRaftGroupMembers(brokerConfiguration.getCluster());
    final var localMemberId =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();

    final var otherMembers = allMembers.stream().filter(id -> !id.equals(localMemberId)).toList();

    return clusterTopologyManagerService.start(
        brokerStartupContext.getActorSchedulingService(),
        otherMembers,
        () ->
            partitionDistributionResolver
                .resolvePartitionDistribution(
                    brokerConfiguration.getExperimental().getPartitioning(),
                    brokerConfiguration.getCluster())
                .partitions());
  }

  private ClusterTopologyManagerService getClusterTopologyManagerService(
      final BrokerStartupContext brokerStartupContext) {
    final var rootDirectory =
        Path.of(brokerStartupContext.getBrokerConfiguration().getData().getDirectory());
    return new ClusterTopologyManagerService(
        rootDirectory,
        brokerStartupContext.getClusterServices().getCommunicationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        getDefaultClusterTopologyGossipConfig() // TODO: allow user specified config
        );
  }

  private ClusterTopologyGossiperConfig getDefaultClusterTopologyGossipConfig() {
    return new ClusterTopologyGossiperConfig(Duration.ofSeconds(10), Duration.ofSeconds(2), 2);
  }
}
