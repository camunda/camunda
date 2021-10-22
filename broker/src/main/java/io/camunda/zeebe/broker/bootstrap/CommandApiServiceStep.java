/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import org.slf4j.Logger;

final class CommandApiServiceStep extends AbstractBrokerStartupStep {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "Command API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    final var socketCfg = brokerCfg.getNetwork().getCommandApi();
    final var securityCfg = brokerCfg.getNetwork().getSecurity();

    final var messagingConfig = new MessagingConfig();
    messagingConfig.setInterfaces(List.of(socketCfg.getHost()));
    messagingConfig.setPort(socketCfg.getPort());

    if (securityCfg.isEnabled()) {
      messagingConfig
          .setTlsEnabled(true)
          .setCertificateChain(securityCfg.getCertificateChainPath())
          .setPrivateKey(securityCfg.getPrivateKeyPath());
    }

    final var messagingService =
        new NettyMessagingService(
            brokerCfg.getCluster().getClusterName(),
            Address.from(socketCfg.getAdvertisedHost(), socketCfg.getAdvertisedPort()),
            messagingConfig);

    messagingService
        .start()
        .whenComplete(
            (createdMessagingService, error) -> {
              /* the next block doesn't use "createdMessagingService" because it is only a
               * MessagingService, but we need a ManagedMessagingService. At the time of this
               * writing createdMessagingService == messagingService, so we use this instead.
               */
              forwardExceptions(
                  () ->
                      concurrencyControl.run(
                          () ->
                              forwardExceptions(
                                  () ->
                                      completeStartup(
                                          brokerStartupContext,
                                          startupFuture,
                                          messagingService,
                                          error),
                                  startupFuture)),
                  startupFuture);
            });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var commandApiServiceActor = brokerShutdownContext.getCommandApiService();
    if (commandApiServiceActor == null) {
      closeServerTransport(brokerShutdownContext, concurrencyControl, shutdownFuture);
      return;
    }
    brokerShutdownContext.removePartitionListener(commandApiServiceActor);
    brokerShutdownContext.removeDiskSpaceUsageListener(commandApiServiceActor);

    concurrencyControl.runOnCompletion(
        commandApiServiceActor.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setCommandApiService(null);
              closeServerTransport(brokerShutdownContext, concurrencyControl, shutdownFuture);
            },
            shutdownFuture));
  }

  private void completeStartup(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture,
      final NettyMessagingService messagingService,
      final Throwable error) {
    if (error != null) {
      startupFuture.completeExceptionally(error);
    } else {
      brokerStartupContext.setCommandApiMessagingService(messagingService);
      LOG.debug(
          "Bound command API to {}, using advertised address {} ",
          messagingService.bindingAddresses(),
          messagingService.address());

      startServerTransport(brokerStartupContext, startupFuture);
    }
  }

  private void startServerTransport(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
    final var brokerInfo = brokerStartupContext.getBrokerInfo();
    final var schedulingService = brokerStartupContext.getActorSchedulingService();
    final var messagingService = brokerStartupContext.getCommandApiMessagingService();

    final var atomixServerTransport =
        new AtomixServerTransport(brokerInfo.getNodeId(), messagingService);

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(atomixServerTransport),
        proceed(
            () -> {
              brokerStartupContext.setCommandApiServerTransport(atomixServerTransport);
              startCommandApiService(brokerStartupContext, atomixServerTransport, startupFuture);
            },
            startupFuture));
  }

  private void startCommandApiService(
      final BrokerStartupContext brokerStartupContext,
      final ServerTransport serverTransport,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
    final var brokerInfo = brokerStartupContext.getBrokerInfo();
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    final var schedulingService = brokerStartupContext.getActorSchedulingService();

    final var backpressureCfg = brokerCfg.getBackpressure();
    var limiter = PartitionAwareRequestLimiter.newNoopLimiter();
    if (backpressureCfg.isEnabled()) {
      limiter = PartitionAwareRequestLimiter.newLimiter(backpressureCfg);
    }

    final var commandApiService =
        new CommandApiServiceImpl(
            serverTransport,
            brokerInfo,
            limiter,
            schedulingService,
            brokerCfg.getExperimental().getQueryApi());

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(commandApiService),
        proceed(
            () -> {
              brokerStartupContext.setCommandApiService(commandApiService);
              brokerStartupContext.addPartitionListener(commandApiService);
              brokerStartupContext.addDiskSpaceUsageListener(commandApiService);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }

  private void closeServerTransport(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var serverTransport = brokerShutdownContext.getCommandApiServerTransport();

    if (serverTransport == null) {
      stopMessagingService(brokerShutdownContext, concurrencyControl, shutdownFuture);
      return;
    }

    concurrencyControl.runOnCompletion(
        serverTransport.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setCommandApiServerTransport(null);
              stopMessagingService(brokerShutdownContext, concurrencyControl, shutdownFuture);
            },
            shutdownFuture));
  }

  private void stopMessagingService(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var messagingService = brokerShutdownContext.getCommandApiMessagingService();

    if (messagingService == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    messagingService
        .stop()
        .whenComplete(
            (of, error) -> {
              if (error != null) {
                shutdownFuture.completeExceptionally(error);
              } else {
                concurrencyControl.run(
                    () -> {
                      brokerShutdownContext.setCommandApiMessagingService(null);
                      shutdownFuture.complete(brokerShutdownContext);
                    });
              }
            });
  }
}
