/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.standalone;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.FileUtil;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;

@Profile("broker")
@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.zeebe.broker",
      "io.camunda.zeebe.shared",
      "io.camunda.zeebe.gateway.rest"
    })
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.broker", "io.camunda.zeebe.shared"})
public class BrokerModuleConfiguration implements ApplicationListener<ContextClosedEvent> {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private final BrokerConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;

  private Broker broker;

  @Autowired
  public BrokerModuleConfiguration(
      final BrokerConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
  }

  @PostConstruct
  public void logModule() {
    LOGGER.info("Starting Broker");

    final SystemContext systemContext =
        new SystemContext(
            configuration.config(), identityConfiguration, actorScheduler, cluster, brokerClient);

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
