/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class CleanupScheduler extends AbstractScheduledService implements ConfigurationReloadable {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(CleanupScheduler.class);
  private final ConfigurationService configurationService;
  private final List<CleanupService> cleanupServices;

  public CleanupScheduler(
      final ConfigurationService configurationService, final List<CleanupService> cleanupServices) {
    this.configurationService = configurationService;
    this.cleanupServices = cleanupServices;
  }

  @PostConstruct
  public void init() {
    log.info("Initializing OptimizeCleanupScheduler");
    getCleanupConfiguration().validate();
    if (getCleanupConfiguration().isEnabled()) {
      startCleanupScheduling();
    } else {
      stopCleanupScheduling();
    }
  }

  public synchronized void startCleanupScheduling() {
    log.info("Starting cleanup scheduling");
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopCleanupScheduling() {
    log.info("Stopping cleanup scheduling");
    stopScheduling();
  }

  @Override
  public void run() {
    runCleanup();
  }

  @Override
  protected CronTrigger createScheduleTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }

  public void runCleanup() {
    log.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = LocalDateUtil.getCurrentDateTime();

    cleanupServices.stream()
        .filter(CleanupService::isEnabled)
        .forEach(
            optimizeCleanupService -> {
              log.info(
                  "Running CleanupService {}", optimizeCleanupService.getClass().getSimpleName());
              try {
                optimizeCleanupService.doCleanup(startTime);
              } catch (final Exception e) {
                log.error(
                    "Execution of cleanupService {} failed",
                    optimizeCleanupService.getClass().getSimpleName(),
                    e);
              }
            });

    final long durationSeconds =
        OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
    log.info("Finished optimize history cleanup in {}s", durationSeconds);
  }

  public List<CleanupService> getCleanupServices() {
    return cleanupServices;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  protected CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }
}
