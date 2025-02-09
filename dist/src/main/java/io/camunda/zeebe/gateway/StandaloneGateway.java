/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.MainSupport;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Entry point for the standalone gateway application. By default, it enables the {@link
 * Profile#GATEWAY} profile, loading the appropriate application properties overrides.
 *
 * <p>See {@link #main(String[])} for more.
 */
@SpringBootApplication(
    proxyBeanMethods = false,
    scanBasePackages = {
      "io.camunda.zeebe.gateway",
      "io.camunda.zeebe.shared",
      "io.camunda.zeebe.util.liveness"
    })
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.gateway", "io.camunda.zeebe.shared"})
public class StandaloneGateway
    implements CommandLineRunner, ApplicationListener<ContextClosedEvent>, CloseableSilently {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private final GatewayConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringGatewayBridge springGatewayBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster atomixCluster;
  private final BrokerClient brokerClient;
  private final JobStreamClient jobStreamClient;
  private final MeterRegistry meterRegistry;

  private Gateway gateway;

  @Autowired
  public StandaloneGateway(
      final GatewayConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringGatewayBridge springGatewayBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster atomixCluster,
      final BrokerClient brokerClient,
      final JobStreamClient jobStreamClient,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springGatewayBridge = springGatewayBridge;
    this.actorScheduler = actorScheduler;
    this.atomixCluster = atomixCluster;
    this.brokerClient = brokerClient;
    this.jobStreamClient = jobStreamClient;
    this.meterRegistry = meterRegistry;
  }

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/zeebe_gateway_banner.txt");

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .sources(StandaloneGateway.class)
            .profiles(Profile.GATEWAY.getId())
            .build(args);

    application.run();
  }

  @Override
  public void run(final String... args) throws Exception {
    LOG.info(
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
            jobStreamClient.streamer(),
            meterRegistry);
    springGatewayBridge.registerGatewayStatusSupplier(gateway::getStatus);
    springGatewayBridge.registerClusterStateSupplier(
        () ->
            Optional.ofNullable(gateway.getBrokerClient())
                .map(BrokerClient::getTopologyManager)
                .map(BrokerTopologyManager::getTopology));
    springGatewayBridge.registerJobStreamClient(() -> jobStreamClient);

    gateway.start().join(30, TimeUnit.SECONDS);
    LOG.info("Standalone gateway is started!");
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    close();
  }

  @Override
  public void close() {
    if (gateway != null) {
      try {
        gateway.close();
      } catch (final Exception e) {
        LOG.warn("Failed to gracefully shutdown gRPC gateway", e);
      }
    }
  }
}
