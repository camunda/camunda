/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class TelemetryScheduler extends AbstractScheduledService implements ConfigurationReloadable {

  private final TelemetrySendingService telemetrySendingService;
  private final TelemetryDataService telemetryDataService;
  private final ConfigurationService configurationService;
  private final SettingsService settingsService;

  @PostConstruct
  public void init() {
    log.info("Initializing TelemetryScheduler");
    getTelemetryConfiguration().validate();
    startTelemetryScheduling();
  }

  @Override
  protected void run() {
    log.info("Checking whether telemetry data can be sent.");
    if (settingsService.getSettings().isMetadataTelemetryEnabled()) {
      try {
        telemetrySendingService.sendTelemetryData(
          telemetryDataService.getTelemetryData(),
          getTelemetryConfiguration().getTelemetryEndpoint()
        );
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

  public synchronized void startTelemetryScheduling() {
    log.info("Starting telemetry scheduling");
    startScheduling();
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
