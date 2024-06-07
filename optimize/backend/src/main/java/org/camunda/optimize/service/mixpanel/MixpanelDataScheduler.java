/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.mixpanel;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class MixpanelDataScheduler extends AbstractScheduledService
    implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final MixpanelReportingService mixpanelReportingService;

  @PostConstruct
  public void init() {
    log.info("Initializing MixpanelDataScheduler");
    getTelemetryConfiguration().validate();
    startMixpanelTelemetryScheduling();
  }

  @Override
  protected void run() {
    log.info("Checking whether Mixpanel telemetry data can be sent.");
    if (configurationService.getTelemetryConfiguration().isInitializeTelemetry()) {
      try {
        mixpanelReportingService.sendHeartbeatData();
        log.info("Mixpanel telemetry data was sent.");
      } catch (OptimizeRuntimeException e) {
        log.error("Failed to send Mixpanel telemetry.", e);
      }
    } else {
      log.info("Mixpanel telemetry disabled.");
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofHours(getTelemetryConfiguration().getReportingIntervalInHours()));
  }

  public synchronized boolean startMixpanelTelemetryScheduling() {
    log.info("Starting mixpanel scheduling");
    return startScheduling();
  }

  @PreDestroy
  public synchronized void stopMixpanelTelemetryScheduling() {
    log.info("Stopping mixpanel scheduling");
    stopScheduling();
  }

  protected TelemetryConfiguration getTelemetryConfiguration() {
    return this.configurationService.getTelemetryConfiguration();
  }
}
