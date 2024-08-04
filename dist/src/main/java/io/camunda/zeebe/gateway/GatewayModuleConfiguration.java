/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.GatewayBasedConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the gateway modules by using the the {@link
 * io.camunda.application.Profile#GATEWAY} profile, so that the appropriate gateway application
 * properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "io.camunda.zeebe.gateway",
      "io.camunda.zeebe.shared",
      "io.camunda.zeebe.util.liveness"
    },
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.camunda\\.zeebe\\.gateway\\.rest\\..*")
    })
@Profile("gateway")
public class GatewayModuleConfiguration implements CloseableSilently {

  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final GatewayBasedConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringGatewayBridge springGatewayBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster atomixCluster;
  private final BrokerClient brokerClient;
  private final JobStreamClient jobStreamClient;

  private Gateway gateway;

  @Autowired
  public GatewayModuleConfiguration(
      final GatewayBasedConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringGatewayBridge springGatewayBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster atomixCluster,
      final BrokerClient brokerClient,
      final JobStreamClient jobStreamClient) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springGatewayBridge = springGatewayBridge;
    this.actorScheduler = actorScheduler;
    this.atomixCluster = atomixCluster;
    this.brokerClient = brokerClient;
    this.jobStreamClient = jobStreamClient;
  }

  @Bean(destroyMethod = "close")
  public Gateway gateway() {
    LOGGER.info(
        "Starting standalone gateway {} with version {}",
        configuration.config().getCluster().getMemberId(),
        VersionUtil.getVersion());

    atomixCluster.start();
    jobStreamClient.start().join();

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);

    gateway =
        new Gateway(
            configuration.shutdownTimeout(),
            configuration.config(),
            identityConfiguration,
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer());
    springGatewayBridge.registerGatewayStatusSupplier(gateway::getStatus);
    springGatewayBridge.registerClusterStateSupplier(
        () ->
            Optional.ofNullable(gateway.getBrokerClient())
                .map(BrokerClient::getTopologyManager)
                .map(BrokerTopologyManager::getTopology));
    springGatewayBridge.registerJobStreamClient(() -> jobStreamClient);

    gateway.start().join(30, TimeUnit.SECONDS);
    LOGGER.info("Standalone gateway is started!");

    return gateway;
  }

  @Override
  public void close() {
    if (gateway != null) {
      try {
        gateway.close();
      } catch (final Exception e) {
        LOGGER.warn("Failed to gracefully shutdown gRPC gateway", e);
      }
    }
  }
}
