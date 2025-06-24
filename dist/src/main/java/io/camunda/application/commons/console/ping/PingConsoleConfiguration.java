/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import io.camunda.service.ManagementServices;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// @EnableConfigurationProperties(PingConfiguration.class)
public class PingConsoleConfiguration {
  public static final Logger LOGGER = LoggerFactory.getLogger(PingConsoleConfiguration.class);

  private final PingConfiguration pingConfiguration;

  private final ManagementServices managementServices;

  public PingConsoleConfiguration(final ManagementServices managementServices) {
    this.managementServices = managementServices;
    pingConfiguration = new PingConfiguration();
    // TODO: remove this hardcoded values.
    pingConfiguration.setClusterId("123");
    pingConfiguration.setClusterName("Mycluster");
    pingConfiguration.setEnabled(true);
    pingConfiguration.setPingPeriod(60);
    pingConfiguration.setEndpoint("https://webhook.site/6480438f-a2ca-41c7-b71e-8a6f4c7fc94");
  }

  @PostConstruct
  public void validate() {
    // TODO: validate config here.
    // should only try to validate if ping is enabled

    // if endpoint is not set, should fail
    // endpoint validity should be checked at runtime? throw warnings after
    // several failed attempts?

    // if clusterId is not set, should fail
    // if period is not set, should fail

    if (false) {
      throw new IllegalStateException("Invalid Configuration.");
    }
  }

  @Bean("persistentConsolePingTaskExecutor")
  public ScheduledThreadPoolExecutor persistentConsolePingTaskExecutor() {
    if (!pingConfiguration.isEnabled()) {
      LOGGER.info("Console ping is disabled, skipping task creation.");
      return null;
    }
    LOGGER.info(
        "Console ping is enabled with endpoint: {}, and delay: {} " + "minutes",
        pingConfiguration.getEndpoint(),
        pingConfiguration.getPingPeriod());

    final var executor = createTaskExecutor();
    executor.schedule(
        // TODO: use the config deleay period here.
        new SelfSchedulingTask(
            executor, new PingConsoleTask(managementServices, pingConfiguration), 1000),
        1000,
        TimeUnit.MILLISECONDS);
    return executor;
  }

  public ScheduledThreadPoolExecutor createTaskExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("console-license-ping-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(LOGGER))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(0, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    executor.allowCoreThreadTimeOut(true);
    executor.setKeepAliveTime(1, TimeUnit.MINUTES);
    executor.setCorePoolSize(1);
    return executor;
  }

  // TODO: autowhire the configs.
  //  @ConfigurationProperties("camunda.console.ping")
  //  public record PingProps(
  //      boolean enabled, String endpoint, String clusterId, String clusterName, int pingPeriod) {}

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
