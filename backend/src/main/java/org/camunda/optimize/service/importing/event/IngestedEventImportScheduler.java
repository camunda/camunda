/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.IngestedEventImportConfiguration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Slf4j
@Component
public class IngestedEventImportScheduler extends AbstractScheduledService {
  private final ConfigurationService configurationService;

  @PostConstruct
  public void init() {
    if (getEventBasedImportConfiguration().isEnabled()) {
      startImportScheduling();
    }
  }

  @PreDestroy
  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling ingested event import.");
    stopScheduling();
  }

  public synchronized void startImportScheduling() {
    log.info("Scheduling ingested event import.");
    startScheduling();
  }

  @Override
  protected void run() {
    // TODO call mediators
  }

  @Override
  protected Trigger getScheduleTrigger() {
    return new PeriodicTrigger(getEventBasedImportConfiguration().getImportIntervalInSec(), TimeUnit.SECONDS);
  }

  private IngestedEventImportConfiguration getEventBasedImportConfiguration() {
    return configurationService.getIngestedEventImportConfiguration();
  }
}
