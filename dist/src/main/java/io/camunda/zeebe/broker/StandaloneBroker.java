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
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final BrokerCfg configuration;
  private final Environment springEnvironment;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;

  private Path tempFolder;
  private Broker broker;

  @Autowired
  public StandaloneBroker(
      final BrokerCfg configuration,
      final Environment springEnvironment,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler) {
    this.configuration = configuration;
    this.springEnvironment = springEnvironment;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
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
    final Path workingDirectory = resolveWorkingDirectory();
    final SystemContext systemContext =
        new SystemContext(configuration, workingDirectory.toString(), actorScheduler);

    actorScheduler.start();
    broker = new Broker(systemContext, springBrokerBridge);
    broker.start();
  }

  private Path resolveWorkingDirectory() throws IOException {
    final Path workingDirectory;
    if (shouldUseTemporaryFolder()) {
      LOG.debug(
          "Starting broker with a temporary directory; it will be deleted on graceful shutdown");
      tempFolder = Files.createTempDirectory("zeebe").toAbsolutePath().normalize();
      workingDirectory = tempFolder;
    } else {
      workingDirectory = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    }

    return workingDirectory;
  }

  @Override
  public void onApplicationEvent(final ContextClosedEvent event) {
    try {
      broker.close();
      actorScheduler.stop().get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Shutdown interrupted, most likely harmless", e);
    } catch (final ExecutionException e) {
      LOG.error("Failed to shutdown broker gracefully", e);
    } finally {
      deleteTempDirectory();
      LogManager.shutdown();
    }
  }

  private boolean shouldUseTemporaryFolder() {
    return springEnvironment.acceptsProfiles(
        Profiles.of(Profile.DEVELOPMENT.getId(), Profile.TEST.getId()));
  }

  private void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolderIfExists(tempFolder);
      } catch (final IOException e) {
        LOG.error("Failed to delete temporary folder {}", tempFolder, e);
      }
    }
  }
}
