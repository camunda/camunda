/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContextImpl;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupProcess;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.LogUtil;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.netty.util.NetUtil;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class Broker implements AutoCloseable {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemContext systemContext;
  private boolean isClosed = false;

  private CompletableFuture<Broker> startFuture;
  private BrokerHealthCheckService healthCheckService;

  // TODO make Broker class itself the actor
  private final BrokerStartupActor brokerStartupActor;
  private BrokerContext brokerContext;

  // TODO make Broker class itself the actor
  public Broker(final SystemContext systemContext, final SpringBrokerBridge springBrokerBridge) {
    this(systemContext, springBrokerBridge, Collections.emptyList());
  }

  public Broker(
      final SystemContext systemContext,
      final SpringBrokerBridge springBrokerBridge,
      final List<PartitionListener> additionalPartitionListeners) {
    this.systemContext = systemContext;

    final ActorScheduler scheduler = this.systemContext.getScheduler();
    final BrokerInfo localBroker = createBrokerInfo(getConfig());
    final var nodeId = MemberId.from(String.valueOf(getConfig().getCluster().getNodeId()));

    healthCheckService = new BrokerHealthCheckService(nodeId);

    final var startupContext =
        new BrokerStartupContextImpl(
            localBroker,
            systemContext.getBrokerConfiguration(),
            systemContext.getIdentityConfiguration(),
            springBrokerBridge,
            scheduler,
            healthCheckService,
            buildExporterRepository(getConfig()),
            new ClusterServicesImpl(systemContext.getCluster()),
            systemContext.getBrokerClient(),
            additionalPartitionListeners,
            systemContext.getShutdownTimeout());

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
      LOG.info(
          "Starting broker {} version {}",
          brokerCfg.getCluster().getNodeId(),
          VersionUtil.getVersion());
    }
  }

  private void internalStart() {
    try {
      brokerContext = brokerStartupActor.start().join();

      healthCheckService.setBrokerStarted();
      startFuture.complete(this);
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

  /**
   * Create an instance of BrokerInfo with the fields that can be initialized from the
   * configuration. It does not initialize fields that are part of the ClusterConfiguration.
   */
  private BrokerInfo createBrokerInfo(final BrokerCfg brokerCfg) {
    final var clusterCfg = brokerCfg.getCluster();

    final BrokerInfo result =
        new BrokerInfo(
            clusterCfg.getNodeId(),
            NetUtil.toSocketAddressString(
                brokerCfg.getNetwork().getCommandApi().getAdvertisedAddress()));

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
                      LOG.info("Broker shut down.");
                    })
                .join();
          }
        });
  }

  // only used for tests
  public BrokerContext getBrokerContext() {
    return brokerContext;
  }

  // only used for tests
  public SystemContext getSystemContext() {
    return systemContext;
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
      return "Startup";
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
