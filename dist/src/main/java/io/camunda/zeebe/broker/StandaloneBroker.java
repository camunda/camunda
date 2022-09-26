/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
    scanBasePackages = {"io.camunda.zeebe.broker", "io.camunda.zeebe.shared"})
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.broker", "io.camunda.zeebe.shared"})
public class StandaloneBroker
    implements CommandLineRunner, ApplicationListener<ContextClosedEvent> {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private final BrokerCfg configuration;
  private final WorkingDirectory workingDirectory;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;

  private Broker broker;

  @Autowired
  public StandaloneBroker(
      final BrokerCfg configuration,
      final WorkingDirectory workingDirectory,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient) {
    this.configuration = configuration;
    this.workingDirectory = workingDirectory;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
  }

  public static void main(final String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(
        FatalErrorHandler.uncaughtExceptionHandler(Loggers.SYSTEM_LOGGER));

    System.setProperty("spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");
    final var application =
        new SpringApplicationBuilder(StandaloneBroker.class)
            .web(WebApplicationType.SERVLET)
            .logStartupInfo(true)
            .profiles(Profile.BROKER.getId())
            .build(args);

    application.run();
  }

  @Override
  public void run(final String... args) throws IOException {
    final SystemContext systemContext = new SystemContext(configuration, actorScheduler, cluster);

    actorScheduler.start();
    brokerClient.start();
    broker = new Broker(systemContext, springBrokerBridge);
    broker.start();
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    try {
      broker.close();
    } finally {
      cleanupWorkingDirectory();
      LogManager.shutdown();
    }
  }

  private void cleanupWorkingDirectory() {
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
