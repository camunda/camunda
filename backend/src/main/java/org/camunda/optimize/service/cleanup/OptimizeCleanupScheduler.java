/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@RequiredArgsConstructor
@Component
@Slf4j
public class OptimizeCleanupScheduler {

  private final ConfigurationService configurationService;
  private final List<OptimizeCleanupService> cleanupServices;

  private ThreadPoolTaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

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
    if (this.taskScheduler == null) {
      this.taskScheduler = new ThreadPoolTaskScheduler();
      this.taskScheduler.initialize();
    }
    if (this.scheduledTrigger == null) {
      this.scheduledTrigger = this.taskScheduler.schedule(this::runCleanup, getCronTrigger());
    }
  }

  public boolean isScheduledToRun() {
    return this.scheduledTrigger != null;
  }

  @PreDestroy
  public synchronized void stopCleanupScheduling() {
    log.info("Stopping cleanup scheduling");
    if (scheduledTrigger != null) {
      this.scheduledTrigger.cancel(true);
      this.scheduledTrigger = null;
    }
    if (this.taskScheduler != null) {
      this.taskScheduler.destroy();
      this.taskScheduler = null;
    }
  }

  public void runCleanup() {
    log.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = OffsetDateTime.now();

    cleanupServices.forEach(optimizeCleanupService -> {
      try {
        optimizeCleanupService.doCleanup(startTime);
      } catch (Exception e) {
        log.error("Exceution of cleanupService {} failed", optimizeCleanupService.getClass().getSimpleName(), e);
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

  private CronTrigger getCronTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }
}
