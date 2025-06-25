/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import io.camunda.application.commons.console.ping.PingConsoleConfiguration.ConsolePingConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ConsolePingConfiguration.class})
public class PingConsoleConfiguration implements ApplicationRunner {
  public static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleConfiguration.class);

  private final ConsolePingConfiguration pingConfiguration;
  private final ManagementServices managementServices;

  @Autowired
  public PingConsoleConfiguration(
      final ConsolePingConfiguration pingConfigurationProperties,
      final ManagementServices managementServices) {
    pingConfiguration = pingConfigurationProperties;
    this.managementServices = managementServices;
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    if (pingConfiguration.enabled) {
      if (pingConfiguration.endpoint() == null || pingConfiguration.endpoint().isBlank()) {
        throw new IllegalArgumentException("Ping endpoint must not be null or empty.");
      }
      if (pingConfiguration.clusterId() == null || pingConfiguration.clusterId().isBlank()) {
        throw new IllegalArgumentException("Cluster ID must not be null or empty.");
      }
      if (pingConfiguration.clusterName() == null || pingConfiguration.clusterName().isBlank()) {
        throw new IllegalArgumentException("Cluster name must not be null or empty.");
      }
      if (pingConfiguration.pingPeriod() <= 0) {
        throw new IllegalArgumentException("Ping period must be greater than zero.");
      }

      LOGGER.info(
          "Console ping is enabled with endpoint: {}, and delay: {} " + "minutes",
          pingConfiguration.endpoint(),
          pingConfiguration.pingPeriod());
      final var executor = createTaskExecutor();
      executor.schedule(
          new SelfSchedulingTask(
              executor,
              new PingConsoleTask(managementServices, pingConfiguration),
              // pingPeriod is given in minutes.
              pingConfiguration.pingPeriod * 60 * 1000L),
          1000,
          TimeUnit.MILLISECONDS);
    }
  }

  public ScheduledThreadPoolExecutor createTaskExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("console-license-ping-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(LOGGER))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(1, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  @ConfigurationProperties("camunda.console.ping")
  public record ConsolePingConfiguration(
      boolean enabled,
      String endpoint,
      String clusterId,
      String clusterName,
      int pingPeriod,
      Map<String, String> properties) {}

  static final class SelfSchedulingTask implements Runnable {

    private final ScheduledThreadPoolExecutor executor;
    private final Runnable task;
    private final long delay;

    SelfSchedulingTask(
        final ScheduledThreadPoolExecutor executor, final Runnable task, final long delay) {
      this.executor = executor;
      this.task = task;
      this.delay = delay;
    }

    @Override
    public void run() {
      task.run();
      executor.schedule(this, delay, TimeUnit.MILLISECONDS);
    }
  }
}
