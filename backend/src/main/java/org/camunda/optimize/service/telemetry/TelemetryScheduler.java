/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class TelemetryScheduler extends AbstractScheduledService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final SettingsService settingsService;
  private final TelemetryReportingService telemetryService;

  @PostConstruct
  public void init() {
    log.info("Initializing TelemetryScheduler");
    getTelemetryConfiguration().validate();
    startTelemetryScheduling();
  }

  @Override
  protected void run() {
    log.info("Checking whether telemetry data can be sent.");
    Optional<Boolean> telemetryMetadata = settingsService.getSettings().getMetadataTelemetryEnabled();
    if (telemetryMetadata.isPresent() && Boolean.TRUE.equals(telemetryMetadata.get())) {
      try {
        telemetryService.sendTelemetryData();
        log.info("Telemetry data was sent.");
      } catch (OptimizeRuntimeException e) {
        log.error("Failed to send telemetry.", e);
      }
    } else {
      log.info("Telemetry disabled.");
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(getTelemetryConfiguration().getReportingIntervalInHours(), TimeUnit.HOURS);
  }

  public synchronized boolean startTelemetryScheduling() {
    log.info("Starting telemetry scheduling");
    return startScheduling();
  }

  @PreDestroy
  public synchronized void stopTelemetryScheduling() {
    log.info("Stopping telemetry scheduling");
    stopScheduling();
  }

  protected TelemetryConfiguration getTelemetryConfiguration() {
    return this.configurationService.getTelemetryConfiguration();
  }

}
