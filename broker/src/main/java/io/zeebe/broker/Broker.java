/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.utils.net.Address;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import io.zeebe.broker.bootstrap.CloseProcess;
import io.zeebe.broker.bootstrap.StartProcess;
import io.zeebe.broker.clustering.atomix.AtomixFactory;
import io.zeebe.broker.clustering.topology.TopologyManagerImpl;
import io.zeebe.broker.clustering.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.zeebe.broker.system.EmbeddedGatewayService;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BackpressureCfg;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.zeebe.broker.system.monitoring.BrokerHttpServer;
import io.zeebe.broker.system.partitions.TypedRecordProcessorsFactory;
import io.zeebe.broker.system.partitions.ZeebePartition;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportFactory;
import io.zeebe.util.LogUtil;
import io.zeebe.util.VersionUtil;
import io.zeebe.util.exception.UncheckedExecutionException;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class Broker implements AutoCloseable {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private static final CollectorRegistry METRICS_REGISTRY = CollectorRegistry.defaultRegistry;

  static {
    // enable hotspot prometheus metric collection
    DefaultExports.initialize();
  }

  private final SystemContext brokerContext;
  private final List<PartitionListener> partitionListeners;
  private boolean isClosed = false;
  private Atomix atomix;

  private CompletableFuture<Broker> startFuture;
  private TopologyManagerImpl topologyManager;
  private LeaderManagementRequestHandler managementRequestHandler;
  private CommandApiService commandHandler;
  private ActorScheduler scheduler;
  private CloseProcess closeProcess;
  private EmbeddedGatewayService embeddedGatewayService;
  private ServerTransport serverTransport;
  private BrokerHealthCheckService healthCheckService;

  public Broker(final SystemContext systemContext) {
    this.brokerContext = systemContext;
    this.partitionListeners = new ArrayList<>();
  }

  public Broker(final BrokerCfg cfg, final String basePath, final ActorClock clock) {
    this(new SystemContext(cfg, basePath, clock));
  }

  public void addPartitionListener(final PartitionListener listener) {
    partitionListeners.add(listener);
  }

  public synchronized CompletableFuture<Broker> start() {
    if (startFuture == null) {
      startFuture = new CompletableFuture<>();
      LogUtil.doWithMDC(brokerContext.getDiagnosticContext(), this::internalStart);
    }
    return startFuture;
  }

  // TODO: Added this API for testing. When http endpoint is available, this can be removed.
  // https://github.com/zeebe-io/zeebe/issues/3833
  public boolean isHealthy() {
    if (healthCheckService != null) {
      return healthCheckService.isBrokerHealthy();
    }
    return false;
  }

  private void internalStart() {
    final BrokerCfg brokerCfg = getConfig();
    final StartProcess startProcess = initStart();

    try {
      closeProcess = startProcess.start();
      startFuture.complete(this);
    } catch (final Exception bootStrapException) {
      LOG.error(
          "Failed to start broker {}!", brokerCfg.getCluster().getNodeId(), bootStrapException);

      final UncheckedExecutionException exception =
          new UncheckedExecutionException("Failed to start broker", bootStrapException);
      startFuture.completeExceptionally(exception);
      throw exception;
    }
  }

  private StartProcess initStart() {
    final BrokerCfg brokerCfg = getConfig();
    final NetworkCfg networkCfg = brokerCfg.getNetwork();

    final ClusterCfg clusterCfg = brokerCfg.getCluster();
    final BrokerInfo localBroker =
        new BrokerInfo(
            clusterCfg.getNodeId(), networkCfg.getCommandApi().getAdvertisedAddress().toString());

    if (LOG.isInfoEnabled()) {
      LOG.info("Version: {}", VersionUtil.getVersion());
      LOG.info(
          "Starting broker {} with configuration {}", localBroker.getNodeId(), brokerCfg.toJson());
    }

    final StartProcess startContext = new StartProcess("Broker-" + localBroker.getNodeId());

    startContext.addStep("actor scheduler", this::actorSchedulerStep);
    startContext.addStep("membership and replication protocol", () -> atomixCreateStep(brokerCfg));
    startContext.addStep(
        "command api transport", () -> commandApiTransportStep(clusterCfg, localBroker));
    startContext.addStep(
        "command api handler", () -> commandApiHandlerStep(brokerCfg, localBroker));
    startContext.addStep("subscription api", () -> subscriptionAPIStep(localBroker));

    if (brokerCfg.getGateway().isEnable()) {
      startContext.addStep(
          "embedded gateway",
          () -> {
            embeddedGatewayService = new EmbeddedGatewayService(brokerCfg, scheduler, atomix);
            return embeddedGatewayService;
          });
    }
    startContext.addStep("cluster services", () -> atomix.start().join());
    startContext.addStep("topology manager", () -> topologyManagerStep(clusterCfg, localBroker));
    startContext.addStep("metric's server", () -> monitoringServerStep(networkCfg, localBroker));
    startContext.addStep(
        "leader management request handler", () -> managementRequestStep(localBroker));
    startContext.addStep(
        "zeebe partitions", () -> partitionsStep(brokerCfg, clusterCfg, localBroker));

    return startContext;
  }

  private AutoCloseable actorSchedulerStep() {
    scheduler = brokerContext.getScheduler();
    scheduler.start();
    return () ->
        scheduler.stop().get(brokerContext.getStepTimeout().toMillis(), TimeUnit.MILLISECONDS);
  }

  private AutoCloseable atomixCreateStep(final BrokerCfg brokerCfg) {
    atomix = AtomixFactory.fromConfiguration(brokerCfg);
    return () ->
        atomix.stop().get(brokerContext.getStepTimeout().toMillis(), TimeUnit.MILLISECONDS);
  }

  private AutoCloseable commandApiTransportStep(
      final ClusterCfg clusterCfg, final BrokerInfo localBroker) {

    final var nettyMessagingService =
        new NettyMessagingService(
            clusterCfg.getClusterName(),
            Address.from(localBroker.getCommandApiAddress()),
            new MessagingConfig());

    nettyMessagingService.start().join();
    LOG.debug("Bound command API to {} ", nettyMessagingService.address());

    final var transportFactory = new TransportFactory(scheduler);
    serverTransport =
        transportFactory.createServerTransport(localBroker.getNodeId(), nettyMessagingService);

    return () -> {
      serverTransport.close();
      nettyMessagingService.stop().join();
    };
  }

  private AutoCloseable commandApiHandlerStep(
      final BrokerCfg brokerCfg, final BrokerInfo localBroker) {

    final BackpressureCfg backpressure = brokerCfg.getBackpressure();
    PartitionAwareRequestLimiter limiter = PartitionAwareRequestLimiter.newNoopLimiter();
    if (backpressure.isEnabled()) {
      limiter =
          PartitionAwareRequestLimiter.newLimiter(
              backpressure.getAlgorithm(), backpressure.useWindowed());
    }

    commandHandler = new CommandApiService(serverTransport, localBroker, limiter);
    partitionListeners.add(commandHandler);
    scheduleActor(commandHandler);

    return commandHandler;
  }

  private AutoCloseable subscriptionAPIStep(final BrokerInfo localBroker) {
    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
        new SubscriptionApiCommandMessageHandlerService(localBroker, atomix);
    partitionListeners.add(messageHandlerService);
    scheduleActor(messageHandlerService);
    return messageHandlerService;
  }

  private void scheduleActor(final Actor actor) {
    brokerContext
        .getScheduler()
        .submitActor(actor)
        .join(brokerContext.getStepTimeout().toSeconds(), TimeUnit.SECONDS);
  }

  private AutoCloseable topologyManagerStep(
      final ClusterCfg clusterCfg, final BrokerInfo localBroker) {
    topologyManager = new TopologyManagerImpl(atomix, localBroker, clusterCfg);
    partitionListeners.add(topologyManager);
    scheduleActor(topologyManager);
    return topologyManager;
  }

  private AutoCloseable monitoringServerStep(
      final NetworkCfg networkCfg, final BrokerInfo localBroker) {
    healthCheckService = new BrokerHealthCheckService(localBroker, atomix);
    partitionListeners.add(healthCheckService);
    scheduleActor(healthCheckService);

    final SocketBindingCfg monitoringApi = networkCfg.getMonitoringApi();
    final BrokerHttpServer httpServer =
        new BrokerHttpServer(
            monitoringApi.getHost(), monitoringApi.getPort(), METRICS_REGISTRY, healthCheckService);
    return () -> {
      httpServer.close();
      healthCheckService.close();
    };
  }

  private AutoCloseable managementRequestStep(final BrokerInfo localBroker) {
    managementRequestHandler = new LeaderManagementRequestHandler(localBroker, atomix);
    scheduleActor(managementRequestHandler);
    partitionListeners.add(managementRequestHandler);
    return managementRequestHandler;
  }

  private AutoCloseable partitionsStep(
      final BrokerCfg brokerCfg, final ClusterCfg clusterCfg, final BrokerInfo localBroker)
      throws Exception {
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup)
            atomix.getPartitionService().getPartitionGroup(AtomixFactory.GROUP_NAME);

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

    final StartProcess partitionStartProcess = new StartProcess("Broker-" + nodeId + " partitions");

    for (final RaftPartition owningPartition : owningPartitions) {
      partitionStartProcess.addStep(
          "partition " + owningPartition.id().id(),
          () -> {
            final ZeebePartition zeebePartition =
                new ZeebePartition(
                    localBroker,
                    owningPartition,
                    partitionListeners,
                    atomix.getEventService(),
                    scheduler,
                    brokerCfg,
                    commandHandler,
                    createFactory(topologyManager, clusterCfg, atomix, managementRequestHandler));
            scheduleActor(zeebePartition);
            healthCheckService.registerMonitoredPartition(
                owningPartition.id().id(), zeebePartition);
            return zeebePartition;
          });
    }
    return partitionStartProcess.start();
  }

  private TypedRecordProcessorsFactory createFactory(
      final TopologyManagerImpl topologyManager,
      final ClusterCfg clusterCfg,
      final Atomix atomix,
      final LeaderManagementRequestHandler requestHandler) {
    return (ActorControl actor, ZeebeState zeebeState, ProcessingContext processingContext) -> {
      final LogStream stream = processingContext.getLogStream();

      final TopologyPartitionListenerImpl partitionListener =
          new TopologyPartitionListenerImpl(actor);
      topologyManager.addTopologyPartitionListener(partitionListener);

      final DeploymentDistributorImpl deploymentDistributor =
          new DeploymentDistributorImpl(
              clusterCfg, atomix, partitionListener, zeebeState.getDeploymentState(), actor);

      final PartitionCommandSenderImpl partitionCommandSender =
          new PartitionCommandSenderImpl(atomix, topologyManager, actor);
      final SubscriptionCommandSender subscriptionCommandSender =
          new SubscriptionCommandSender(stream.getPartitionId(), partitionCommandSender);

      final PushDeploymentRequestHandler deploymentRequestHandler =
          requestHandler.getPushDeploymentRequestHandler();

      final LongPollingJobNotification jobsAvailableNotification =
          new LongPollingJobNotification(atomix.getEventService());

      return EngineProcessors.createEngineProcessors(
          processingContext,
          clusterCfg.getPartitionsCount(),
          subscriptionCommandSender,
          deploymentDistributor,
          deploymentRequestHandler,
          jobsAvailableNotification::onJobsAvailable);
    };
  }

  public BrokerCfg getConfig() {
    return brokerContext.getBrokerConfiguration();
  }

  @Override
  public void close() {
    LogUtil.doWithMDC(
        brokerContext.getDiagnosticContext(),
        () -> {
          if (!isClosed && startFuture != null) {
            startFuture
                .thenAccept(
                    b -> {
                      closeProcess.closeReverse();
                      isClosed = true;
                      LOG.info("Broker shut down.");
                    })
                .join();
          }
        });
  }

  public EmbeddedGatewayService getEmbeddedGatewayService() {
    return embeddedGatewayService;
  }

  public Atomix getAtomix() {
    return atomix;
  }

  public SystemContext getBrokerContext() {
    return brokerContext;
  }
}
