/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContextImpl;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupProcess;
import io.camunda.zeebe.broker.bootstrap.CloseProcess;
import io.camunda.zeebe.broker.bootstrap.StartProcess;
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
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.BackpressureCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.transport.TransportFactory;
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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class Broker implements AutoCloseable {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemContext systemContext;
  private final List<PartitionListener> partitionListeners;
  private boolean isClosed = false;

  private ClusterServicesImpl clusterServices;
  private CompletableFuture<Broker> startFuture;
  private LeaderManagementRequestHandler managementRequestHandler;
  private CommandApiServiceImpl commandApiService;
  private final ActorScheduler scheduler;
  private CloseProcess closeProcess;
  private EmbeddedGatewayService embeddedGatewayService;
  private BrokerHealthCheckService healthCheckService;
  private final List<DiskSpaceUsageListener> diskSpaceUsageListeners = new ArrayList<>();
  private final SpringBrokerBridge springBrokerBridge;
  private DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private BrokerAdminService brokerAdminService;
  private PartitionManagerImpl partitionManager;

  private final TestCompanionClass testCompanionObject = new TestCompanionClass();
  // TODO make Broker class itself the actor
  private final BrokerStartupActor brokerStartupActor;
  private final BrokerInfo localBroker;
  private BrokerContext brokerContext;
  // TODO make Broker class itself the actor

  public Broker(final SystemContext systemContext, final SpringBrokerBridge springBrokerBridge) {
    this.systemContext = systemContext;
    partitionListeners = new ArrayList<>();
    this.springBrokerBridge = springBrokerBridge;
    scheduler = this.systemContext.getScheduler();

    localBroker = createBrokerInfo(getConfig());

    healthCheckService = new BrokerHealthCheckService(localBroker);

    final BrokerStartupContextImpl startupContext =
        new BrokerStartupContextImpl(
            localBroker,
            systemContext.getBrokerConfiguration(),
            springBrokerBridge,
            scheduler,
            healthCheckService);

    brokerStartupActor = new BrokerStartupActor(startupContext);
    scheduler.submitActor(brokerStartupActor);
  }

  public void addPartitionListener(final PartitionListener listener) {
    partitionListeners.add(listener);
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
    final StartProcess startProcess = initStart();

    try {
      closeProcess = startProcess.start();
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

  private StartProcess initStart() {
    final BrokerCfg brokerCfg = getConfig();

    final StartProcess startContext = new StartProcess("Broker-" + localBroker.getNodeId());

    startContext.addStep("Migrated Startup Steps", this::migratedStartupSteps);
    startContext.addStep(
        "command api transport and handler",
        () -> commandApiTransportAndHandlerStep(brokerCfg, localBroker));
    startContext.addStep("subscription api", () -> subscriptionAPIStep(localBroker));

    startContext.addStep("cluster services", () -> clusterServices.start().join());
    if (brokerCfg.getGateway().isEnable()) {
      startContext.addStep(
          "embedded gateway",
          () -> {
            embeddedGatewayService =
                new EmbeddedGatewayService(
                    brokerCfg,
                    scheduler,
                    clusterServices.getMessagingService(),
                    clusterServices.getMembershipService(),
                    clusterServices.getEventService());
            return embeddedGatewayService;
          });
    }

    startContext.addStep("disk space monitor", () -> diskSpaceMonitorStep(brokerCfg.getData()));
    startContext.addStep(
        "leader management request handler", () -> managementRequestStep(localBroker));
    startContext.addStep("zeebe partitions", () -> partitionsStep(brokerCfg, localBroker));
    startContext.addStep("register diskspace usage listeners", this::addDiskSpaceUsageListeners);
    startContext.addStep("upgrade manager", this::addBrokerAdminService);

    return startContext;
  }

  private AutoCloseable migratedStartupSteps() {
    brokerContext = brokerStartupActor.start().join();

    partitionListeners.addAll(brokerContext.getPartitionListeners());

    clusterServices = brokerContext.getClusterServices();
    testCompanionObject.atomix = clusterServices.getAtomixCluster();

    return () -> {
      brokerStartupActor.stop().join();
      healthCheckService = null;
    };
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

  private AutoCloseable addBrokerAdminService() {
    final var adminService = new BrokerAdminServiceImpl();
    scheduleActor(adminService);

    adminService.injectAdminAccess(partitionManager.createAdminAccess(adminService));
    adminService.injectPartitionInfoSource(partitionManager.getPartitions());

    brokerAdminService = adminService;
    springBrokerBridge.registerBrokerAdminServiceSupplier(() -> brokerAdminService);
    return adminService;
  }

  private AutoCloseable commandApiTransportAndHandlerStep(
      final BrokerCfg brokerCfg, final BrokerInfo localBroker) {
    final var commandApiCfg = brokerCfg.getNetwork().getCommandApi();
    final var messagingService = createMessagingService(brokerCfg.getCluster(), commandApiCfg);
    messagingService.start().join();
    LOG.debug(
        "Bound command API to {}, using advertised address {} ",
        messagingService.bindingAddresses(),
        messagingService.address());

    final var transportFactory = new TransportFactory(scheduler);
    final var serverTransport =
        transportFactory.createServerTransport(localBroker.getNodeId(), messagingService);

    final BackpressureCfg backpressureCfg = brokerCfg.getBackpressure();
    PartitionAwareRequestLimiter limiter = PartitionAwareRequestLimiter.newNoopLimiter();
    if (backpressureCfg.isEnabled()) {
      limiter = PartitionAwareRequestLimiter.newLimiter(backpressureCfg);
    }

    commandApiService =
        new CommandApiServiceImpl(
            serverTransport,
            localBroker,
            limiter,
            scheduler,
            brokerCfg.getExperimental().getQueryApi());
    partitionListeners.add(commandApiService);
    scheduleActor(commandApiService);
    diskSpaceUsageListeners.add(commandApiService);

    return () -> {
      commandApiService.close();
      serverTransport.close();
      messagingService.stop().join();
    };
  }

  private ManagedMessagingService createMessagingService(
      final ClusterCfg clusterCfg, final SocketBindingCfg socketCfg) {
    final var messagingConfig = new MessagingConfig();
    messagingConfig.setInterfaces(List.of(socketCfg.getHost()));
    messagingConfig.setPort(socketCfg.getPort());
    return new NettyMessagingService(
        clusterCfg.getClusterName(),
        Address.from(socketCfg.getAdvertisedHost(), socketCfg.getAdvertisedPort()),
        messagingConfig);
  }

  private AutoCloseable subscriptionAPIStep(final BrokerInfo localBroker) {
    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
        new SubscriptionApiCommandMessageHandlerService(
            localBroker, clusterServices.getCommunicationService());
    partitionListeners.add(messageHandlerService);
    scheduleActor(messageHandlerService);
    diskSpaceUsageListeners.add(messageHandlerService);
    return messageHandlerService;
  }

  private void addDiskSpaceUsageListeners() {
    diskSpaceUsageListeners.forEach(diskSpaceUsageMonitor::addDiskUsageListener);
  }

  private void scheduleActor(final Actor actor) {
    systemContext.getScheduler().submitActor(actor).join();
  }

  private AutoCloseable diskSpaceMonitorStep(final DataCfg data) {
    /* the folder needs to be created at this point. If it doesn't exist, then the DiskSpaceUsageMonitor
     * will calculate the wrong watermark, as a non-existing folder has a total size of 0
     */
    try {
      FileUtil.ensureDirectoryExists(new File(data.getDirectory()).toPath());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    diskSpaceUsageMonitor = new DiskSpaceUsageMonitor(data);
    if (data.isDiskUsageMonitoringEnabled()) {
      scheduleActor(diskSpaceUsageMonitor);
      diskSpaceUsageListeners.forEach(l -> diskSpaceUsageMonitor.addDiskUsageListener(l));
      return () -> diskSpaceUsageMonitor.close();
    } else {
      LOG.info("Skipping start of disk space usage monitor, as it is disabled by configuration");
      return () -> {};
    }
  }

  private AutoCloseable managementRequestStep(final BrokerInfo localBroker) {
    managementRequestHandler =
        new LeaderManagementRequestHandler(
            localBroker,
            clusterServices.getCommunicationService(),
            clusterServices.getEventService());
    scheduleActor(managementRequestHandler);
    partitionListeners.add(managementRequestHandler);
    diskSpaceUsageListeners.add(managementRequestHandler);
    return managementRequestHandler;
  }

  private AutoCloseable partitionsStep(final BrokerCfg brokerCfg, final BrokerInfo localBroker) {
    partitionManager =
        new PartitionManagerImpl(
            scheduler,
            brokerCfg,
            localBroker,
            clusterServices,
            healthCheckService,
            managementRequestHandler.getPushDeploymentRequestHandler(),
            diskSpaceUsageListeners::add,
            partitionListeners,
            commandApiService,
            buildExporterRepository(brokerCfg));

    partitionManager.start().join();

    return () -> {
      partitionManager.stop().join();
      partitionManager = null;
      // TODO shutdown snapshot store
    };
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

  // only used for tests
  @Deprecated
  public AtomixCluster getAtomixCluster() {
    return testCompanionObject.atomix;
  }

  public ClusterServices getClusterServices() {
    return clusterServices;
  }

  public DiskSpaceUsageMonitor getDiskSpaceUsageMonitor() {
    return diskSpaceUsageMonitor;
  }

  public BrokerAdminService getBrokerAdminService() {
    return brokerAdminService;
  }

  public SystemContext getSystemContext() {
    return systemContext;
  }

  public PartitionManager getPartitionManager() {
    return partitionManager;
  }

  @Deprecated // only used for test; temporary work around
  private static final class TestCompanionClass {
    private AtomixCluster atomix;
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
