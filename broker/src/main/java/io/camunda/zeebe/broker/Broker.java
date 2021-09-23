/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContextImpl;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupProcess;
import io.camunda.zeebe.broker.clustering.AtomixClusterFactory;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.LogUtil;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.netty.util.NetUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public final class Broker implements AutoCloseable {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemContext systemContext;
  private boolean isClosed = false;

  private ClusterServicesImpl clusterServices;
  private CompletableFuture<Broker> startFuture;
  private final ActorScheduler scheduler;
  private BrokerHealthCheckService healthCheckService;
  private BrokerAdminService brokerAdminService;

  private final TestCompanionClass testCompanionObject = new TestCompanionClass();
  // TODO make Broker class itself the actor
  private final BrokerStartupActor brokerStartupActor;
  private final BrokerInfo localBroker;
  private BrokerContext brokerContext;
  private final SpringBrokerBridge springBrokerBridge;
  // TODO make Broker class itself the actor

  public Broker(final SystemContext systemContext, final SpringBrokerBridge springBrokerBridge) {
    this(systemContext, springBrokerBridge, Collections.emptyList());
  }

  public Broker(
      final SystemContext systemContext,
      final SpringBrokerBridge springBrokerBridge,
      final List<PartitionListener> additionalPartitionListeners) {
    this.systemContext = systemContext;
    scheduler = this.systemContext.getScheduler();

    this.springBrokerBridge = springBrokerBridge;
    localBroker = createBrokerInfo(getConfig());

    healthCheckService = new BrokerHealthCheckService(localBroker);

    final BrokerStartupContextImpl startupContext =
        new BrokerStartupContextImpl(
            localBroker,
            systemContext.getBrokerConfiguration(),
            springBrokerBridge,
            scheduler,
            healthCheckService,
            buildExporterRepository(getConfig()),
            additionalPartitionListeners);

    brokerStartupActor = new BrokerStartupActor(startupContext);
    scheduler.submitActor(brokerStartupActor);
  }

  public synchronized CompletableFuture<Broker> start() {
    if (startFuture == null) {
      logBrokerStart();

      startFuture = new CompletableFuture<>();
      LogUtil.doWithMDC(systemContext.getDiagnosticContext(), this::internalStart);
    }
    return startFuture;
  }

  private void logBrokerStart() {
    if (LOG.isInfoEnabled()) {
      final BrokerCfg brokerCfg = getConfig();
      LOG.info("Version: {}", VersionUtil.getVersion());
      LOG.info(
          "Starting broker {} with configuration {}",
          brokerCfg.getCluster().getNodeId(),
          brokerCfg.toJson());
    }
  }

  private void internalStart() {
    final List<PartitionListener> partitionListeners = new ArrayList<>();

    DiskSpaceUsageMonitor diskSpaceUsageMonitor = null;
    try {

      if (getConfig().getData().isDiskUsageMonitoringEnabled()) {
        // must be executed before any disk space usage listeners are registered
        final var data = getConfig().getData();
        try {
          FileUtil.ensureDirectoryExists(new File(data.getDirectory()).toPath());
        } catch (final IOException e) {
          LangUtil.rethrowUnchecked(e);
          return;
        }

        diskSpaceUsageMonitor = new DiskSpaceUsageMonitor(data);
        systemContext.getScheduler().submitActor(diskSpaceUsageMonitor).join();
      }

      systemContext.getScheduler().submitActor(healthCheckService).join();

      springBrokerBridge.registerBrokerHealthCheckServiceSupplier(() -> healthCheckService);
      partitionListeners.add(healthCheckService);

      final var adminService = new BrokerAdminServiceImpl();
      systemContext.getScheduler().submitActor(adminService).join();
      springBrokerBridge.registerBrokerAdminServiceSupplier(() -> adminService);

      final var atomix = AtomixClusterFactory.fromConfiguration(getConfig());

      final var clusterServices = new ClusterServicesImpl(atomix);

      final var brokerCfg = getConfig();

      final var socketCfg = brokerCfg.getNetwork().getCommandApi();
      final var messagingConfig = new MessagingConfig();
      messagingConfig.setInterfaces(List.of(socketCfg.getHost()));
      messagingConfig.setPort(socketCfg.getPort());
      final var messagingService =
          new NettyMessagingService(
              brokerCfg.getCluster().getClusterName(),
              Address.from(socketCfg.getAdvertisedHost(), socketCfg.getAdvertisedPort()),
              messagingConfig);

      messagingService.start().join();

      final var brokerInfo = localBroker;

      final var atomixServerTransport =
          new AtomixServerTransport(brokerInfo.getNodeId(), messagingService);

      systemContext.getScheduler().submitActor(atomixServerTransport).join();

      final var backpressureCfg = brokerCfg.getBackpressure();
      var limiter = PartitionAwareRequestLimiter.newNoopLimiter();
      if (backpressureCfg.isEnabled()) {
        limiter = PartitionAwareRequestLimiter.newLimiter(backpressureCfg);
      }

      final var commandApiService =
          new CommandApiServiceImpl(
              atomixServerTransport,
              brokerInfo,
              limiter,
              systemContext.getScheduler(),
              brokerCfg.getExperimental().getQueryApi());

      partitionListeners.add(commandApiService);
      if (diskSpaceUsageMonitor != null) {
        diskSpaceUsageMonitor.addDiskUsageListener(commandApiService);
      }

      final SubscriptionApiCommandMessageHandlerService subscriptionApiService =
          new SubscriptionApiCommandMessageHandlerService(
              brokerInfo, clusterServices.getCommunicationService());

      systemContext.getScheduler().submitActor(subscriptionApiService);
      partitionListeners.add(subscriptionApiService);

      if (diskSpaceUsageMonitor != null) {
        diskSpaceUsageMonitor.addDiskUsageListener(subscriptionApiService);
      }

      clusterServices.start().join();

      if (brokerCfg.getGateway().isEnable()) {
        final var embeddedGatewayService =
            new EmbeddedGatewayService(
                brokerCfg,
                systemContext.getScheduler(),
                clusterServices.getMessagingService(),
                clusterServices.getMembershipService(),
                clusterServices.getEventService());
        testCompanionObject.embeddedGatewayService = embeddedGatewayService;
      }

      final var managementRequestHandler =
          new LeaderManagementRequestHandler(
              brokerInfo,
              clusterServices.getCommunicationService(),
              clusterServices.getEventService());

      systemContext.getScheduler().submitActor(managementRequestHandler).join();

      partitionListeners.add(managementRequestHandler);
      if (diskSpaceUsageMonitor != null) {
        diskSpaceUsageMonitor.addDiskUsageListener(managementRequestHandler);
      }

      final var partitionManager =
          new PartitionManagerImpl(
              systemContext.getScheduler(),
              brokerCfg,
              localBroker,
              clusterServices,
              healthCheckService,
              managementRequestHandler.getPushDeploymentRequestHandler(),
              diskSpaceUsageMonitor::addDiskUsageListener,
              partitionListeners,
              commandApiService,
              buildExporterRepository(brokerCfg));

      partitionManager.start().join();
      adminService.injectAdminAccess(partitionManager.createAdminAccess(adminService));
      adminService.injectPartitionInfoSource(partitionManager.getPartitions());

      testCompanionObject.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
      testCompanionObject.atomix = clusterServices.getAtomixCluster();

      startFuture.complete(this);
      healthCheckService.setBrokerStarted();
    } catch (final Exception bootStrapException) {
      final BrokerCfg brokerCfg = getConfig();
      LOG.error(
          "Failed to start broker {}!", brokerCfg.getCluster().getNodeId(), bootStrapException);

      final UncheckedExecutionException exception =
          new UncheckedExecutionException("Failed to start broker", bootStrapException);
      startFuture.completeExceptionally(exception);
      throw exception;
    }
  }

  private BrokerInfo createBrokerInfo(final BrokerCfg brokerCfg) {
    final var clusterCfg = brokerCfg.getCluster();

    final BrokerInfo result =
        new BrokerInfo(
            clusterCfg.getNodeId(),
            NetUtil.toSocketAddressString(
                brokerCfg.getNetwork().getCommandApi().getAdvertisedAddress()));

    result
        .setClusterSize(clusterCfg.getClusterSize())
        .setPartitionsCount(clusterCfg.getPartitionsCount())
        .setReplicationFactor(clusterCfg.getReplicationFactor());

    final String version = VersionUtil.getVersion();
    if (version != null && !version.isBlank()) {
      result.setVersion(version);
    }
    return result;
  }

  private ExporterRepository buildExporterRepository(final BrokerCfg cfg) {
    final ExporterRepository exporterRepository = new ExporterRepository();
    final var exporterEntries = cfg.getExporters().entrySet();

    // load and validate exporters
    for (final var exporterEntry : exporterEntries) {
      final var id = exporterEntry.getKey();
      final var exporterCfg = exporterEntry.getValue();
      try {
        exporterRepository.load(id, exporterCfg);
      } catch (final ExporterLoadException | ExternalJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    return exporterRepository;
  }

  public BrokerCfg getConfig() {
    return systemContext.getBrokerConfiguration();
  }

  @Override
  public void close() {
    LogUtil.doWithMDC(
        systemContext.getDiagnosticContext(),
        () -> {
          if (!isClosed && startFuture != null) {
            startFuture
                .thenAccept(
                    b -> {
                      brokerStartupActor.stop().join();
                      healthCheckService = null;
                      isClosed = true;
                      testCompanionObject.atomix = null;
                      LOG.info("Broker shut down.");
                    })
                .join();
          }
        });
  }

  @Deprecated
  public EmbeddedGatewayService getEmbeddedGatewayService() {
    return testCompanionObject.embeddedGatewayService;
  }

  // only used for tests
  @Deprecated
  public AtomixCluster getAtomixCluster() {
    return testCompanionObject.atomix;
  }

  public ClusterServices getClusterServices() {
    return clusterServices;
  }

  public DiskSpaceUsageMonitor getDiskSpaceUsageMonitor() {
    return testCompanionObject.diskSpaceUsageMonitor;
  }

  public BrokerAdminService getBrokerAdminService() {
    return brokerAdminService;
  }

  public SystemContext getSystemContext() {
    return systemContext;
  }

  public PartitionManager getPartitionManager() {
    return brokerContext.getPartitionManager();
  }

  @Deprecated // only used for test; temporary work around
  private static final class TestCompanionClass {
    private AtomixCluster atomix;
    private EmbeddedGatewayService embeddedGatewayService;
    private DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  }

  /**
   * Temporary helper object. This object is needed during the transition of broker startup/shutdown
   * steps to the new concept. Afterwards, the expectation is that this object will merge with the
   * Broker and that then the Broker becomes the actor
   */
  private static final class BrokerStartupActor extends Actor {

    private final BrokerStartupProcess brokerStartupProcess;

    private final int nodeId;

    private BrokerStartupActor(final BrokerStartupContextImpl startupContext) {
      nodeId = startupContext.getBrokerInfo().getNodeId();
      startupContext.setConcurrencyControl(actor);
      brokerStartupProcess = new BrokerStartupProcess(startupContext);
    }

    @Override
    public String getName() {
      return buildActorName(nodeId, "Startup");
    }

    private ActorFuture<BrokerContext> start() {
      final ActorFuture<BrokerContext> result = createFuture();
      actor.run(() -> actor.runOnCompletion(brokerStartupProcess.start(), result));
      return result;
    }

    private ActorFuture<Void> stop() {
      final ActorFuture<Void> result = createFuture();
      actor.run(() -> actor.runOnCompletion(brokerStartupProcess.stop(), result));
      return result;
    }
  }
}
