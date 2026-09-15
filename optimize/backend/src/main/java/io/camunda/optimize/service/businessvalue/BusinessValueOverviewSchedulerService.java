/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

/**
 * Periodic driver for the business-value overview compute path. Each tick recomputes every {@code
 * (tenantId, processDefinitionKey, metricRange)} row across all four range presets. The interval is
 * a single value on {@link BusinessValueConfiguration#getOverviewRefreshInterval} so a team can
 * tighten or relax the cadence without a code change.
 *
 * <p>Startup is bound to {@link ApplicationReadyEvent} at {@link Ordered#LOWEST_PRECEDENCE} so the
 * scheduler only starts after {@link
 * io.camunda.optimize.service.dashboard.BusinessValueDashboardService#init()} (annotated {@link
 * Ordered#HIGHEST_PRECEDENCE}) has seeded the per-process reports the compute path evaluates.
 * Without this ordering the first tick can run before the reports exist, fail, and then wait the
 * full refresh interval (1h by default) before retrying.
 */
@Component
public class BusinessValueOverviewSchedulerService extends AbstractScheduledService {

  static final List<MetricRange> ALL_RANGES =
      List.of(
          MetricRange.SEVEN_DAYS,
          MetricRange.THIRTY_DAYS,
          MetricRange.THREE_MONTHS,
          MetricRange.SIX_MONTHS);

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewSchedulerService.class);

  private final BusinessValueOverviewComputeService computeService;
  private final ConfigurationService configurationService;

  public BusinessValueOverviewSchedulerService(
      final BusinessValueOverviewComputeService computeService,
      final ConfigurationService configurationService) {
    this.computeService = computeService;
    this.configurationService = configurationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(Ordered.LOWEST_PRECEDENCE)
  public void init() {
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopOverviewScheduling() {
    LOG.info("Stopping business-value overview scheduler");
    stopScheduling();
  }

  public void runOverviewComputeTask() {
    run();
  }

  @Override
  protected void run() {
    LOG.debug("Running business-value overview compute across all definitions and range presets");
    computeService.computeOverviewRows(ALL_RANGES);
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofSeconds(
            configurationService.getBusinessValueConfiguration().getOverviewRefreshInterval()));
  }

  @Override
  public synchronized boolean startScheduling() {
    LOG.info("Scheduling business-value overview scheduler");
    return super.startScheduling();
  }
}
