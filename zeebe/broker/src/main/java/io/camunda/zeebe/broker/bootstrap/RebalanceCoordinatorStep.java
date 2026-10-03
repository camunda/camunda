/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.raft.RebalanceConfiguration;
import io.atomix.raft.partition.impl.LeadershipTransferClient;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionLeaders;
import io.camunda.zeebe.broker.system.configuration.RaftCfg;
import io.camunda.zeebe.rebalance.ClusterLoadCollector;
import io.camunda.zeebe.rebalance.ClusterRebalanceMetrics;
import io.camunda.zeebe.rebalance.LoadMeasure;
import io.camunda.zeebe.rebalance.PartitionBalanceMetrics;
import io.camunda.zeebe.rebalance.PartitionBalancePlanner;
import io.camunda.zeebe.rebalance.ProtoBufRebalanceSerializer;
import io.camunda.zeebe.rebalance.RebalanceCoordinator;
import io.camunda.zeebe.rebalance.RebalanceRequestServer;
import io.camunda.zeebe.rebalance.RebalanceScheduler;
import io.camunda.zeebe.rebalance.SequentialRebalanceRunner;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.util.schedule.Schedule.NoneSchedule;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the rebalance actor, coordinator, request server, and their registration as a cluster
 * configuration update listener. Runs after {@link RequestIdGeneratorStep} so the request id
 * generator and cluster services are already available, and is stopped before them since startup
 * steps shut down in reverse order.
 */
public class RebalanceCoordinatorStep implements StartupStep<BrokerStartupContext> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RebalanceCoordinatorStep.class);

  private @Nullable Actor rebalanceCoordinatorActor;
  private @Nullable RebalanceCoordinator rebalanceCoordinator;
  private @Nullable RebalanceRequestServer rebalanceRequestServer;
  private @Nullable LeadershipTransferClient leadershipTransferClient;
  private @Nullable PartitionBalanceMetrics partitionBalanceMetrics;
  private @Nullable ClusterLoadCollector loadCollector;
  private @Nullable RebalanceScheduler rebalanceScheduler;

  @Override
  public String getName() {
    return "Rebalance Coordinator";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> started =
        brokerStartupContext.getConcurrencyControl().createFuture();

    final var localMember =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();
    final var partitionLeaders =
        new TopologyPartitionLeaders(brokerStartupContext.getBrokerClient().getTopologyManager());
    final var newLeadershipTransferClient =
        new LeadershipTransferClient(
            brokerStartupContext.getClusterServices().getCommunicationService(),
            brokerStartupContext
                .getBrokerConfiguration()
                .getExperimental()
                .getRaft()
                .getRequestTimeout());
    final var rebalanceMetrics =
        new ClusterRebalanceMetrics(brokerStartupContext.getMeterRegistry());
    final var newPartitionBalanceMetrics =
        new PartitionBalanceMetrics(brokerStartupContext.getMeterRegistry(), partitionLeaders);
    rebalanceCoordinatorActor = Actor.newActor().name("RebalanceCoordinator").build();

    brokerStartupContext
        .getActorSchedulingService()
        .submitActor(rebalanceCoordinatorActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                LOGGER.error("Failed to start the rebalance coordinator actor", error);
                started.completeExceptionally(error);
                return;
              }
              leadershipTransferClient = newLeadershipTransferClient;
              partitionBalanceMetrics = newPartitionBalanceMetrics;
              brokerStartupContext
                  .getClusterConfigurationService()
                  .addUpdateListener(partitionBalanceMetrics);
              final var raftCfg =
                  brokerStartupContext.getBrokerConfiguration().getCluster().getRaft();
              rebalanceCoordinator =
                  new RebalanceCoordinator(
                      localMember,
                      rebalanceCoordinatorActor,
                      new SequentialRebalanceRunner(
                          localMember,
                          rebalanceCoordinatorActor,
                          partitionLeaders,
                          leadershipTransferClient,
                          rebalanceMetrics,
                          raftCfg.getRebalanceLeaderWaitTimeout(),
                          new RebalanceConfiguration(
                              raftCfg.getRebalanceReplicationLagThreshold().toBytes(),
                              raftCfg.getRebalanceReplicationTimeout(),
                              raftCfg.getRebalanceMaxTransferAttempts()),
                          brokerStartupContext
                              .getBrokerConfiguration()
                              .getCluster()
                              .getHeartbeatInterval(),
                          brokerStartupContext
                              .getBrokerConfiguration()
                              .getCluster()
                              .getElectionTimeout()),
                      new PartitionBalancePlanner(partitionLeaders),
                      () -> brokerStartupContext.getRequestIdGenerator().nextId(),
                      Clock.systemUTC(),
                      rebalanceMetrics);
              rebalanceRequestServer =
                  new RebalanceRequestServer(
                      brokerStartupContext.getClusterServices().getCommunicationService(),
                      new ProtoBufRebalanceSerializer(),
                      rebalanceCoordinator);
              rebalanceRequestServer.start();
              loadCollector =
                  new ClusterLoadCollector(
                      localMember,
                      rebalanceCoordinatorActor,
                      brokerStartupContext.getLoadCounters(),
                      brokerStartupContext.getClusterServices().getCommunicationService(),
                      Clock.systemUTC());
              loadCollector.start();
              brokerStartupContext
                  .getClusterConfigurationService()
                  .addUpdateListener(rebalanceCoordinator);
              startScheduler(brokerStartupContext, raftCfg, rebalanceMetrics);
              started.complete(brokerStartupContext);
            });

    return started;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> stopped =
        brokerStartupContext.getConcurrencyControl().createFuture();

    stopScheduler(brokerStartupContext);
    if (loadCollector != null) {
      loadCollector.close();
      loadCollector = null;
    }
    if (rebalanceRequestServer != null) {
      rebalanceRequestServer.close();
      rebalanceRequestServer = null;
    }
    if (rebalanceCoordinator != null) {
      brokerStartupContext
          .getClusterConfigurationService()
          .removeUpdateListener(rebalanceCoordinator);
    }
    if (partitionBalanceMetrics != null) {
      brokerStartupContext
          .getClusterConfigurationService()
          .removeUpdateListener(partitionBalanceMetrics);
      partitionBalanceMetrics = null;
    }
    closeRebalanceCoordinator()
        .onComplete(
            (ok, error) -> {
              rebalanceCoordinator = null;
              rebalanceCoordinatorActor = null;
              if (leadershipTransferClient != null) {
                leadershipTransferClient.close();
                leadershipTransferClient = null;
              }
              if (error != null) {
                stopped.completeExceptionally(error);
              } else {
                stopped.complete(brokerStartupContext);
              }
            });

    return stopped;
  }

  private void startScheduler(
      final BrokerStartupContext brokerStartupContext,
      final RaftCfg raftCfg,
      final ClusterRebalanceMetrics rebalanceMetrics) {
    final var schedule = raftCfg.getRebalanceSchedule();
    if (schedule instanceof NoneSchedule) {
      return;
    }
    final var actor = Objects.requireNonNull(rebalanceCoordinatorActor);
    final var loadWindow = raftCfg.getRebalanceLoadWindow();
    final Map<LoadMeasure, Double> maxRates = new EnumMap<>(LoadMeasure.class);
    final var maxProcessInstances = raftCfg.getRebalanceMaxProcessInstancesPerSecond();
    if (maxProcessInstances != null) {
      maxRates.put(LoadMeasure.ROOT_PROCESS_INSTANCES, maxProcessInstances);
    }
    final var maxCommands = raftCfg.getRebalanceMaxCommandsPerSecond();
    if (maxCommands != null) {
      maxRates.put(LoadMeasure.PROCESSED_COMMANDS, maxCommands);
    }

    rebalanceScheduler =
        new RebalanceScheduler(
            actor,
            schedule,
            Objects.requireNonNull(rebalanceCoordinator),
            Objects.requireNonNull(loadCollector),
            maxRates,
            loadWindow,
            Clock.systemUTC(),
            rebalanceMetrics);
    brokerStartupContext.getClusterConfigurationService().addUpdateListener(rebalanceScheduler);
    rebalanceScheduler.start();
    LOGGER.info("Scheduling rebalances on {} with load limits {}", schedule, maxRates);
  }

  private void stopScheduler(final BrokerStartupContext brokerStartupContext) {
    if (rebalanceScheduler != null) {
      brokerStartupContext
          .getClusterConfigurationService()
          .removeUpdateListener(rebalanceScheduler);
      rebalanceScheduler.close();
      rebalanceScheduler = null;
    }
  }

  private ActorFuture<Void> closeRebalanceCoordinator() {
    if (rebalanceCoordinatorActor == null) {
      return CompletableActorFuture.completed(null);
    }
    if (rebalanceCoordinator == null) {
      return rebalanceCoordinatorActor.closeAsync();
    }
    return rebalanceCoordinator
        .shutdown()
        .andThen(rebalanceCoordinatorActor::closeAsync, Runnable::run);
  }
}
