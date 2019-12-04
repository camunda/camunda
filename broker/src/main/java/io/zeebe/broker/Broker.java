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
import io.zeebe.broker.clustering.atomix.AtomixFactory;
import io.zeebe.broker.clustering.base.EmbeddedGatewayService;
import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.partitions.TypedRecordProcessorsFactory;
import io.zeebe.broker.clustering.base.topology.TopologyManagerImpl;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
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
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class Broker implements AutoCloseable {
  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private static final int TRANSPORT_BUFFER_FACTOR = 16;
  private static final String VERSION;
  private static final String COMMAND_API_SERVER_NAME = "commandApi.server";

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
  private boolean isClosed = false;
  private final List<AutoCloseable> closeables;
  private final List<PartitionListener> partitionListeners;
  private Atomix atomix;

  public Broker(final String configFileLocation, final String basePath, final ActorClock clock) {
    this(new SystemContext(configFileLocation, basePath, clock));
  }

  public Broker(final InputStream configStream, final String basePath, final ActorClock clock) {
    this(new SystemContext(configStream, basePath, clock));
  }

  public Broker(final BrokerCfg cfg, final String basePath, final ActorClock clock) {
    this(new SystemContext(cfg, basePath, clock));
  }

  public Broker(final SystemContext systemContext) {
    this.brokerContext = systemContext;
    this.partitionListeners = new ArrayList<>();
    this.closeables = new ArrayList<>();

    LogUtil.doWithMDC(systemContext.getDiagnosticContext(), () -> start());
  }

  protected void start() {
    final BrokerCfg brokerCfg = getConfig();
    final NetworkCfg networkCfg = brokerCfg.getNetwork();

    if (LOG.isInfoEnabled()) {
      LOG.info("Version: {}", VERSION);
      LOG.info("Starting broker with configuration {}", brokerCfg.toJson());
    }

    final ActorScheduler scheduler = brokerContext.getScheduler();
    scheduler.start();
    LOG.info("Broker startup phase: Step 1 Scheduler started.");

    atomix = AtomixFactory.fromConfiguration(brokerCfg);
    LOG.info("Broker startup phase: Step 2 membership and replication protocol created.");

    final ClusterCfg clusterCfg = brokerCfg.getCluster();
    final BrokerInfo localMember =
        new BrokerInfo(
            clusterCfg.getNodeId(), networkCfg.getCommandApi().getAdvertisedAddress().toString());

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// COMMAND API //////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
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
            .name(COMMAND_API_SERVER_NAME)
            .bindAddress(bindAddr.toInetSocketAddress())
            .scheduler(scheduler)
            .messageMemoryPool(new NonBlockingMemoryPool(transportBufferSize))
            .messageMaxLength(networkCfg.getMaxMessageSize())
            .build(commandApiMessageHandler, commandApiMessageHandler);
    closeables.add(serverTransport);

    final CommandApiService commandHandler =
        new CommandApiService(serverTransport.getOutput(), commandApiMessageHandler, limiter);
    partitionListeners.add(commandHandler);
    LOG.info("Broker startup phase: Step 4 command api bound to {}.", bindAddress);

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// SUBSCRIPTION API /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
        new SubscriptionApiCommandMessageHandlerService(atomix);
    partitionListeners.add(messageHandlerService);
    scheduleActor(messageHandlerService);

    LOG.info("Broker startup phase: Step 5 message subscription api started.");

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// GATEWAY /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    if (brokerCfg.getGateway().isEnable()) {
      closeables.add(new EmbeddedGatewayService(brokerCfg, scheduler, atomix));
      LOG.info("Broker startup phase: Step 7 embedded gateway started.");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// ATOMIX JOIN /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    // after topology mgr is created
    atomix.start().join();
    LOG.info("Broker startup phase: Step 8 joined cluster successfully.");

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// TOPOLOGY /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    final TopologyManagerImpl topologyManager =
        new TopologyManagerImpl(atomix, localMember, clusterCfg);
    partitionListeners.add(topologyManager);
    scheduleActor(topologyManager);
    LOG.info("Broker startup phase: Step 6 topology manager started.");

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// METRICS AND READY CHECK ////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    final BrokerHealthCheckService healthCheckService = new BrokerHealthCheckService(atomix);
    partitionListeners.add(healthCheckService);
    scheduleActor(healthCheckService);

    final SocketBindingCfg monitoringApi = networkCfg.getMonitoringApi();
    final BrokerHttpServer httpServer =
        new BrokerHttpServer(
            monitoringApi.getHost(), monitoringApi.getPort(), METRICS_REGISTRY, healthCheckService);
    closeables.add(httpServer);
    LOG.info("Broker startup phase: Step 8 metric's server started.");

    final LeaderManagementRequestHandler requestHandler =
        new LeaderManagementRequestHandler(atomix);
    scheduleActor(requestHandler);
    partitionListeners.add(requestHandler);

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// START PARTITIONS /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup)
            atomix.getPartitionService().getPartitionGroup(AtomixFactory.GROUP_NAME);

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

    for (final RaftPartition owningPartition : owningPartitions) {
      final PartitionInstallService partitionInstallService =
          new PartitionInstallService(
              owningPartition,
              partitionListeners,
              atomix.getEventService(),
              scheduler,
              brokerCfg,
              commandHandler,
              createFactory(topologyManager, clusterCfg, atomix, requestHandler));
      scheduleActor(partitionInstallService);
      LOG.info(
          "Broker startup phase: Step 9 partition {} successfully installed.",
          owningPartition.id().id());
    }

    LOG.info("Broker started successfully!");
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

  private void scheduleActor(Actor actor) {
    brokerContext.getScheduler().submitActor(actor).join(10, TimeUnit.SECONDS);
    closeables.add(actor);
  }

  @Override
  public void close() {
    LogUtil.doWithMDC(
        brokerContext.getDiagnosticContext(),
        () -> {
          if (!isClosed) {
            Collections.reverse(closeables);
            closeables.forEach(
                c -> {
                  try {
                    c.close();
                  } catch (Exception e) {
                    LOG.error("Failed to close {}", c, e);
                  }
                });
            brokerContext.close();
            isClosed = true;
            LOG.info("Broker shut down.");
          }
        });
  }

  public SystemContext getBrokerContext() {
    return brokerContext;
  }

  public BrokerCfg getConfig() {
    return brokerContext.getBrokerConfiguration();
  }

  public Atomix getAtomix() {
    return atomix;
  }
}
