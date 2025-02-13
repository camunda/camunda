/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.MainSupport;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Entry point for the standalone broker application. By default, it enables the {@link
 * Profile#BROKER} profile, loading the appropriate application properties overrides.
 *
 * <p>See {@link #main(String[])} for more.
 */
@SpringBootApplication(
    proxyBeanMethods = false,
    scanBasePackages = {
      "io.camunda.zeebe.broker",
      "io.camunda.zeebe.shared",
      "io.camunda.zeebe.gateway.rest"
    })
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.broker", "io.camunda.zeebe.shared"})
public class StandaloneBroker
    implements CommandLineRunner, ApplicationListener<ContextClosedEvent> {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;
  private final BrokerConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final MeterRegistry meterRegistry;

  @Autowired private BrokerShutdownHelper shutdownHelper;
  private Broker broker;

  @Autowired
  public StandaloneBroker(
      final BrokerConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.meterRegistry = meterRegistry;
  }

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .sources(StandaloneBroker.class)
            .profiles(Profile.BROKER.getId())
            .build(args);

    application.run();
  }

  @Override
  public void run(final String... args) throws IOException {
    final SystemContext systemContext =
        new SystemContext(
            configuration.shutdownTimeout(),
            configuration.config(),
            identityConfiguration,
            actorScheduler,
            cluster,
            brokerClient,
            meterRegistry);
    springBrokerBridge.registerShutdownHelper(
        errorCode -> shutdownHelper.initiateShutdown(errorCode));

    broker = new Broker(systemContext, springBrokerBridge);
    broker.start();
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    try {
      broker.close();
    } finally {
      cleanupWorkingDirectory();
    }
  }

  private void cleanupWorkingDirectory() {
    final var workingDirectory = configuration.workingDirectory();
    if (!workingDirectory.isTemporary()) {
      return;
    }

    LOGGER.debug("Deleting broker temporary working directory {}", workingDirectory.path());
    try {
      FileUtil.deleteFolderIfExists(workingDirectory.path());
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete temporary directory {}", workingDirectory.path());
    }
  }
}
