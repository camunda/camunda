/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import io.zeebe.broker.bootstrap.CloseHelper;
import io.zeebe.broker.bootstrap.StartHelper;
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
import io.zeebe.broker.transport.commandapi.CommandApiMessageHandler;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.LogUtil;
import io.zeebe.util.exception.UncheckedExecutionException;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class Broker implements AutoCloseable {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private static final int TRANSPORT_BUFFER_FACTOR = 16;
  private static final String VERSION;

  private static final CollectorRegistry METRICS_REGISTRY = CollectorRegistry.defaultRegistry;

  static {
    // enable hotspot prometheus metric collection
    DefaultExports.initialize();
  }

  static {
    final String version = Broker.class.getPackage().getImplementationVersion();
    VERSION = version != null ? version : "development";
  }

  private final SystemContext brokerContext;
  private final List<PartitionListener> partitionListeners;
  private boolean isClosed = false;
  private Atomix atomix;

  private volatile CompletableFuture<Broker> startFuture = null;
  private TopologyManagerImpl topologyManager;
  private LeaderManagementRequestHandler managementRequestHandler;
  private CommandApiService commandHandler;
  private ActorScheduler scheduler;
  private CloseHelper closeHelper;

  public Broker(final String configFileLocation, final String basePath, final ActorClock clock) {
    this(new SystemContext(configFileLocation, basePath, clock));
  }

  public Broker(final SystemContext systemContext) {
    this.brokerContext = systemContext;
    this.partitionListeners = new ArrayList<>();
  }

  public Broker(final InputStream configStream, final String basePath, final ActorClock clock) {
    this(new SystemContext(configStream, basePath, clock));
  }

  public Broker(final BrokerCfg cfg, final String basePath, final ActorClock clock) {
    this(new SystemContext(cfg, basePath, clock));
  }

  public void addPartitionListener(PartitionListener listener) {
    partitionListeners.add(listener);
  }

  public Broker awaitStarting() {
    if (startFuture == null) {
      start();
    }
    return startFuture.join();
  }

  public void start() {
    if (startFuture == null) {
      startFuture = new CompletableFuture<>();
      LogUtil.doWithMDC(brokerContext.getDiagnosticContext(), this::internalStart);
    }
  }

  private void internalStart() {
    final BrokerCfg brokerCfg = getConfig();
    final StartHelper startHelper = initStart();

    try {
      closeHelper = startHelper.start();
      startFuture.complete(this);
    } catch (Exception bootStrapException) {
      LOG.error("Failed to start broker {}!", brokerCfg.getCluster().getNodeId(),
        bootStrapException);

      final UncheckedExecutionException exception = new UncheckedExecutionException(
        "Failed to start broker", bootStrapException);
      startFuture.completeExceptionally(exception);
      throw exception;
    }
  }

  private StartHelper initStart() {
    final BrokerCfg brokerCfg = getConfig();
    final NetworkCfg networkCfg = brokerCfg.getNetwork();

    final ClusterCfg clusterCfg = brokerCfg.getCluster();
    final BrokerInfo localBroker =
      new BrokerInfo(
        clusterCfg.getNodeId(), networkCfg.getCommandApi().getAdvertisedAddress().toString());

    if (LOG.isInfoEnabled()) {
      LOG.info("Version: {}", VERSION);
      LOG.info(
        "Starting broker {} with configuration {}", localBroker.getNodeId(), brokerCfg.toJson());
    }

    final StartHelper startContext = new StartHelper(localBroker.getNodeId());

    startContext.addStep(
      "starting actor scheduler", this::actorSchedulerStep);
    startContext.addStep(
      "creating membership and replication protocol", () -> atomixCreateStep(brokerCfg));
    startContext.addStep(
      "starting command api",
      () -> commandAPIStep(brokerCfg, networkCfg, localBroker));
    startContext.addStep(
      "starting subscription api", () -> subscriptionAPIStep(localBroker));

    if (brokerCfg.getGateway().isEnable()) {
      startContext.addStep(
        "starting embedded gateway",
        () -> new EmbeddedGatewayService(brokerCfg, scheduler,
          atomix));
    }
    startContext.addStep("joining cluster", () -> atomix.start().join());
    startContext.addStep(
      "starting topology manager", () -> topologyManagerStep(clusterCfg, localBroker));
    startContext.addStep(
      "starting metric's server", () -> metricsServerStep(networkCfg, localBroker));
    startContext.addStep(
      "starting leader management request handler", () -> managementRequestStep(localBroker));
    startContext.addStep(
      "starting zeebe partitions", () -> partitionsStep(brokerCfg, clusterCfg, localBroker));

    return startContext;
  }


  private AutoCloseable actorSchedulerStep() {
    scheduler = brokerContext.getScheduler();
    scheduler.start();
    return () ->
      scheduler.stop().get(brokerContext.getCloseTimeout().toMillis(), TimeUnit.MILLISECONDS);
  }

  private AutoCloseable atomixCreateStep(BrokerCfg brokerCfg) {
    atomix = AtomixFactory.fromConfiguration(brokerCfg);
    return () -> atomix.stop().join();
  }


  private AutoCloseable commandAPIStep(BrokerCfg brokerCfg, NetworkCfg networkCfg,
    BrokerInfo localBroker) {
    final CommandApiMessageHandler commandApiMessageHandler = new CommandApiMessageHandler();

    final BackpressureCfg backpressure = brokerCfg.getBackpressure();
    PartitionAwareRequestLimiter limiter = PartitionAwareRequestLimiter.newNoopLimiter();
    if (backpressure.isEnabled()) {
      limiter =
        PartitionAwareRequestLimiter.newLimiter(
          backpressure.getAlgorithm(), backpressure.useWindowed());
    }

    final SocketAddress bindAddr = networkCfg.getCommandApi().getAddress();
    final ByteValue transportBufferSize =
      ByteValue.ofBytes(networkCfg.getMaxMessageSize().toBytes() * TRANSPORT_BUFFER_FACTOR);

    final InetSocketAddress bindAddress = bindAddr.toInetSocketAddress();
    final var serverTransport =
      Transports.newServerTransport()
        .name(actorNamePattern(localBroker, "commandApi"))
        .bindAddress(bindAddr.toInetSocketAddress())
        .scheduler(scheduler)
        .messageMemoryPool(new NonBlockingMemoryPool(transportBufferSize))
        .messageMaxLength(networkCfg.getMaxMessageSize())
        .build(commandApiMessageHandler, commandApiMessageHandler);

    commandHandler =
      new CommandApiService(serverTransport.getOutput(), commandApiMessageHandler, limiter);
    partitionListeners.add(commandHandler);
    LOG.info("Command api bound to {}.", bindAddress);
    return serverTransport;
  }

  public static String actorNamePattern(BrokerInfo local, String name) {
    return String.format("Broker-%d-%s", local.getNodeId(), name);
  }

  private AutoCloseable subscriptionAPIStep(BrokerInfo localBroker) {
    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
      new SubscriptionApiCommandMessageHandlerService(localBroker, atomix);
    partitionListeners.add(messageHandlerService);
    scheduleActor(messageHandlerService);
    return messageHandlerService;
  }

  private void scheduleActor(Actor actor) {
    brokerContext.getScheduler().submitActor(actor).join(30, TimeUnit.SECONDS);
  }

  private AutoCloseable topologyManagerStep(ClusterCfg clusterCfg, BrokerInfo localBroker) {
    topologyManager = new TopologyManagerImpl(atomix, localBroker, clusterCfg);
    partitionListeners.add(topologyManager);
    scheduleActor(topologyManager);
    return topologyManager;
  }

  private AutoCloseable metricsServerStep(NetworkCfg networkCfg, BrokerInfo localBroker) {
    final BrokerHealthCheckService healthCheckService =
      new BrokerHealthCheckService(localBroker, atomix);
    partitionListeners.add(healthCheckService);
    scheduleActor(healthCheckService);

    final SocketBindingCfg monitoringApi = networkCfg.getMonitoringApi();
    final BrokerHttpServer httpServer =
      new BrokerHttpServer(
        monitoringApi.getHost(),
        monitoringApi.getPort(),
        METRICS_REGISTRY,
        healthCheckService);
    return () ->
    {
      httpServer.close();
      healthCheckService.close();
    };
  }

  private AutoCloseable managementRequestStep(BrokerInfo localBroker) {
    managementRequestHandler = new LeaderManagementRequestHandler(localBroker, atomix);
    scheduleActor(managementRequestHandler);
    partitionListeners.add(managementRequestHandler);
    return managementRequestHandler;
  }

  private AutoCloseable partitionsStep(BrokerCfg brokerCfg, ClusterCfg clusterCfg,
    BrokerInfo localBroker) {
    final RaftPartitionGroup partitionGroup =
      (RaftPartitionGroup)
        atomix.getPartitionService().getPartitionGroup(AtomixFactory.GROUP_NAME);

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<RaftPartition> owningPartitions =
      partitionGroup.getPartitions().stream()
        .filter(partition -> partition.members().contains(nodeId))
        .map(RaftPartition.class::cast)
        .collect(Collectors.toList());

    LOG.info("Broker {} Partitions {}", localBroker.getNodeId(), owningPartitions);

    final List<ZeebePartition> partitions = new ArrayList<>();
    for (final RaftPartition owningPartition : owningPartitions) {
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
      partitions.add(zeebePartition);
      LOG.info(
        "Bootstrap {}: Partition {} successfully installed.",
        localBroker.getNodeId(),
        owningPartition.id().id());
    }
    return () -> partitions.forEach(ZeebePartition::close);
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
        if (!isClosed) {

          if (startFuture != null) {
            startFuture
              .thenAccept(
                b -> {
                  closeHelper.closeReverse();
                  isClosed = true;
                  LOG.info("Broker shut down.");
                })
              .join();
          }
        }
      });
  }

  public Atomix getAtomix() {
    return atomix;
  }

  public SystemContext getBrokerContext() {
    return brokerContext;
  }

}
