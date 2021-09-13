/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.shared.EnvironmentHelper;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;

@SpringBootApplication(exclude = ElasticsearchRestClientAutoConfiguration.class)
@ComponentScan({"io.camunda.zeebe.broker", "io.camunda.zeebe.shared"})
public class StandaloneBroker
    implements CommandLineRunner, ApplicationListener<ContextClosedEvent> {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final BrokerCfg configuration;
  private final Environment springEnvironment;
  private final SpringBrokerBridge springBrokerBridge;

  private String tempFolder;
  private SystemContext systemContext;
  private Broker broker;

  @Autowired
  public StandaloneBroker(
      final BrokerCfg configuration,
      final Environment springEnvironment,
      final SpringBrokerBridge springBrokerBridge) {
    this.configuration = configuration;
    this.springEnvironment = springEnvironment;
    this.springBrokerBridge = springBrokerBridge;
  }

  public static void main(final String[] args) {
    System.setProperty("spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");
    EnvironmentHelper.disableGatewayHealthIndicatorsAndProbes();
    SpringApplication.run(StandaloneBroker.class, args);
  }

  @Override
  public void run(final String... args) {
    if (EnvironmentHelper.isProductionEnvironment(springEnvironment)) {
      systemContext = createSystemContextInBaseDirectory();
    } else {
      Loggers.SYSTEM_LOGGER.info("Launching broker in temporary folder.");
      systemContext = createSystemContextInTempDirectory();
    }

    systemContext.getScheduler().start();

    broker = new Broker(systemContext, springBrokerBridge);

    broker.start();
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    try {
      broker.close();
      systemContext.getScheduler().stop().get();
    } catch (final ExecutionException | InterruptedException e) {
      LOG.error(e.getMessage(), e);
    } finally {
      deleteTempDirectory();
      LogManager.shutdown();
    }
  }

  private SystemContext createSystemContextInBaseDirectory() {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }
    return new SystemContext(configuration, basePath, null);
  }

  private SystemContext createSystemContextInTempDirectory() {
    try {
      tempFolder = Files.createTempDirectory("zeebe").toAbsolutePath().normalize().toString();
      return new SystemContext(configuration, tempFolder, null);
    } catch (final IOException e) {
      throw new UncheckedIOException("Could not create system context", e);
    }
  }

  private void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolder(tempFolder);
      } catch (final IOException e) {
        LOG.error("Failed to delete temporary folder {}", tempFolder, e);
      }
    }
  }
}
