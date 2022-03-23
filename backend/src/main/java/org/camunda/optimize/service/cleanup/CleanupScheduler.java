/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class CleanupScheduler extends AbstractScheduledService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final List<CleanupService> cleanupServices;

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

  public void runCleanup() {
    log.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = LocalDateUtil.getCurrentDateTime();

    cleanupServices.stream()
      .filter(CleanupService::isEnabled)
      .forEach(optimizeCleanupService -> {
        log.info("Running CleanupService {}", optimizeCleanupService.getClass().getSimpleName());
        try {
          optimizeCleanupService.doCleanup(startTime);
        } catch (Exception e) {
          log.error("Execution of cleanupService {} failed", optimizeCleanupService.getClass().getSimpleName(), e);
        }
      });

    final long durationSeconds = OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
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
    return this.configurationService.getCleanupServiceConfiguration();
  }

  @Override
  protected CronTrigger createScheduleTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }
}
