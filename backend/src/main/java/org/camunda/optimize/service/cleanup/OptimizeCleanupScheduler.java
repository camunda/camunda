/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class OptimizeCleanupScheduler extends AbstractScheduledService {

  private final ConfigurationService configurationService;
  private final List<OptimizeCleanupService> cleanupServices;

  @PostConstruct
  public void init() {
    log.info("Initializing OptimizeCleanupScheduler");
    getCleanupConfiguration().validate();
    if (getCleanupConfiguration().getEnabled()) {
      startCleanupScheduling();
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

  public void runCleanup() {
    log.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = OffsetDateTime.now();

    cleanupServices.forEach(optimizeCleanupService -> {
      try {
        optimizeCleanupService.doCleanup(startTime);
      } catch (Exception e) {
        log.error("Execution of cleanupService {} failed", optimizeCleanupService.getClass().getSimpleName(), e);
      }
    });

    final long durationSeconds = OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
    log.info("Finished optimize history cleanup in {}s", durationSeconds);
  }

  public List<OptimizeCleanupService> getCleanupServices() {
    return cleanupServices;
  }

  protected OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  @Override
  protected CronTrigger getScheduleTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }
}
