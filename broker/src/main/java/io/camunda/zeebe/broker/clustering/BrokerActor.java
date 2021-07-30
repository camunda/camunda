/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.partitioning.PartitionManagerActor;
import io.camunda.zeebe.broker.partitioning.PartitionManagerFactory;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.startup.AbstractStartupStep;
import io.camunda.zeebe.util.startup.StartupProcess;
import io.netty.util.NetUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BrokerActor {

  private final StartupProcess<BrokerStartupContext> startupProcess =
      new StartupProcess<>(
          "Broker",
          List.of(
              new StartActorSchedulerStep(),
              new StartHealthCheckService(),
              new StartClusteringServicesStep(),
              new StartPartitionManagerStep()));

  private ActorScheduler actorScheduler;
  private final BrokerHealthCheckService healthCheckService;
  private ClusterServicesActor clusterServicesActor;
  private PartitionManagerActor partitionManagerActor;

  public BrokerActor(
      final SpringBrokerBridge springBrokerBridge,
      final SystemContext systemContext,
      final BrokerCfg brokerCfg) {
    actorScheduler = systemContext.getScheduler();

    final BrokerInfo localBroker =
        new BrokerInfo(
            brokerCfg.getCluster().getNodeId(),
            NetUtil.toSocketAddressString(
                brokerCfg.getNetwork().getCommandApi().getAdvertisedAddress()));

    healthCheckService = new BrokerHealthCheckService(localBroker);

    springBrokerBridge.registerBrokerHealthCheckServiceSupplier(() -> healthCheckService);

    clusterServicesActor = new ClusterServicesActor(brokerCfg);

    final var snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(actorScheduler, brokerCfg.getCluster().getNodeId());

    final var raftPartitionGroup =
        PartitionManagerFactory.buildRaftPartitionGroup(brokerCfg, snapshotStoreFactory);

    partitionManagerActor =
        new PartitionManagerActor(
            raftPartitionGroup,
            clusterServicesActor.getMembershipService(),
            clusterServicesActor.getCommunicationService());
  }

  public CompletableFuture<Void> start() {
    return startupProcess
        .startup(
            new BrokerStartupContext(
                actorScheduler, healthCheckService, clusterServicesActor, partitionManagerActor))
        .thenApply(context -> null);
  }

  public CompletableFuture<Void> stop() {
    return startupProcess
        .shutdown(
            new BrokerStartupContext(
                actorScheduler, healthCheckService, clusterServicesActor, partitionManagerActor))
        .thenApply(
            context -> {
              actorScheduler = null;
              clusterServicesActor = null;
              partitionManagerActor = null;
              return null;
            });
  }

  private static final class StartActorSchedulerStep
      extends AbstractStartupStep<BrokerStartupContext> {

    @Override
    protected CompletableFuture<BrokerStartupContext> startupGuarded(
        final BrokerStartupContext context) {

      return CompletableFuture.runAsync(() -> context.getActorScheduler().start())
          .thenApply(nil -> context);
    }

    @Override
    protected CompletableFuture<BrokerStartupContext> shutdownGuarded(
        final BrokerStartupContext context) {
      return CompletableFuture.runAsync(() -> context.getActorScheduler().stop())
          .thenApply(nil -> context);
    }

    @Override
    public String getName() {
      return "Actor Scheduler";
    }
  }

  private static final class StartHealthCheckService
      extends AbstractStartupStep<BrokerStartupContext> {

    @Override
    protected CompletableFuture<BrokerStartupContext> startupGuarded(
        final BrokerStartupContext context) {

      return CompletableFuture.runAsync(
              () -> context.getActorScheduler().submitActor(context.getHealthCheckService()).join())
          .thenApply(nil -> context);
    }

    @Override
    protected CompletableFuture<BrokerStartupContext> shutdownGuarded(
        final BrokerStartupContext context) {

      return CompletableFuture.runAsync(() -> context.getHealthCheckService().closeAsync())
          .thenApply(nil -> context);
    }

    @Override
    public String getName() {
      return "Health Check Service";
    }
  }

  private static final class StartClusteringServicesStep
      extends AbstractStartupStep<BrokerStartupContext> {

    @Override
    protected CompletableFuture<BrokerStartupContext> startupGuarded(
        final BrokerStartupContext context) {
      return context.getClusterServicesActor().start().thenApply(nil -> context);
    }

    @Override
    protected CompletableFuture<BrokerStartupContext> shutdownGuarded(
        final BrokerStartupContext context) {
      return context.getClusterServicesActor().stop().thenApply(nil -> context);
    }

    @Override
    public String getName() {
      return "Clustering Services";
    }
  }

  private static final class StartPartitionManagerStep
      extends AbstractStartupStep<BrokerStartupContext> {

    @Override
    protected CompletableFuture<BrokerStartupContext> startupGuarded(
        final BrokerStartupContext context) {
      return context.getPartitionManagerActor().start().thenApply(nil -> context);
    }

    @Override
    protected CompletableFuture<BrokerStartupContext> shutdownGuarded(
        final BrokerStartupContext context) {
      return context.getPartitionManagerActor().stop().thenApply(nil -> context);
    }

    @Override
    public String getName() {
      return "Partition Manager";
    }
  }

  private static final class BrokerStartupContext {
    private final ActorScheduler actorScheduler;
    private final BrokerHealthCheckService healthCheckService;
    private final ClusterServicesActor clusterServicesActor;
    private final PartitionManagerActor partitionManagerActor;

    private BrokerStartupContext(
        final ActorScheduler actorScheduler,
        final BrokerHealthCheckService healthCheckService,
        final ClusterServicesActor clusterServicesActor,
        final PartitionManagerActor partitionManagerActor) {
      this.actorScheduler = actorScheduler;
      this.healthCheckService = healthCheckService;
      this.clusterServicesActor = clusterServicesActor;
      this.partitionManagerActor = partitionManagerActor;
    }

    private ActorScheduler getActorScheduler() {
      return actorScheduler;
    }

    public BrokerHealthCheckService getHealthCheckService() {
      return healthCheckService;
    }

    private ClusterServicesActor getClusterServicesActor() {
      return clusterServicesActor;
    }

    private PartitionManagerActor getPartitionManagerActor() {
      return partitionManagerActor;
    }

    @Override
    public String toString() {
      return "BrokerStartupContext{"
          + "actorScheduler="
          + actorScheduler
          + ", healthCheckService="
          + healthCheckService
          + ", clusterServicesActor="
          + clusterServicesActor
          + ", partitionManagerActor="
          + partitionManagerActor
          + '}';
    }
  }
}
