/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import org.slf4j.Logger;

public class ApiMessagingServiceStep extends AbstractBrokerStartupStep {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    final var commandApiCfg = brokerCfg.getNetwork().getCommandApi();
    final var securityCfg = brokerCfg.getNetwork().getSecurity();

    final var messagingConfig = new MessagingConfig();
    messagingConfig.setInterfaces(List.of(commandApiCfg.getHost()));
    messagingConfig.setPort(commandApiCfg.getPort());

    if (securityCfg.isEnabled()) {
      messagingConfig
          .setTlsEnabled(true)
          .configureTls(
              securityCfg.getKeyStore().getFilePath(),
              securityCfg.getKeyStore().getPassword(),
              securityCfg.getPrivateKeyPath(),
              securityCfg.getCertificateChainPath());
    }

    messagingConfig.setCompressionAlgorithm(brokerCfg.getCluster().getMessageCompression());

    final var messagingService =
        new NettyMessagingService(
            brokerCfg.getCluster().getClusterName(),
            Address.from(commandApiCfg.getAdvertisedHost(), commandApiCfg.getAdvertisedPort()),
            messagingConfig,
            "Broker-" + brokerCfg.getCluster().getNodeId());

    messagingService
        .start()
        .whenComplete(
            (createdMessagingService, error) -> {
              /* here we don't use "createdMessagingService" because it is only a
               * MessagingService, but we need a ManagedMessagingService. At the time of this
               * writing createdMessagingService == messagingService, so we use this instead.
               */
              if (error != null) {
                startupFuture.completeExceptionally(error);
              } else {
                concurrencyControl.run(
                    () -> {
                      LOG.debug(
                          "Bound API to {}, using advertised address {} ",
                          messagingService.bindingAddresses(),
                          messagingService.address());
                      brokerStartupContext.setApiMessagingService(messagingService);
                      startupFuture.complete(brokerStartupContext);
                    });
              }
            });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var messagingService = brokerShutdownContext.getApiMessagingService();
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
                      brokerShutdownContext.setApiMessagingService(null);
                      shutdownFuture.complete(brokerShutdownContext);
                    });
              }
            });
  }

  @Override
  public String getName() {
    return "API Messaging Service";
  }
}
