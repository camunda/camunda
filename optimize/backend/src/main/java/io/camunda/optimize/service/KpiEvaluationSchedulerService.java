/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import io.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Component
public class KpiEvaluationSchedulerService extends AbstractScheduledService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(KpiEvaluationSchedulerService.class);
  private final ProcessOverviewWriter processOverviewWriter;
  private final DefinitionService definitionService;
  private final ConfigurationService configurationService;
  private final KpiService kpiService;

  public KpiEvaluationSchedulerService(
      final ProcessOverviewWriter processOverviewWriter,
      final DefinitionService definitionService,
      final ConfigurationService configurationService,
      final KpiService kpiService) {
    this.processOverviewWriter = processOverviewWriter;
    this.definitionService = definitionService;
    this.configurationService = configurationService;
    this.kpiService = kpiService;
  }

  @PostConstruct
  public void init() {
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopCleanupScheduling() {
    log.info("Stopping KPI evaluation scheduler");
    stopScheduling();
  }

  public void runKpiImportTask() {
    run();
  }

  @Override
  protected void run() {
    log.debug("Scheduling KPI evaluation tasks for all existing processes.");
    final List<String> processDefinitionKeys =
        definitionService.getAllDefinitionsWithTenants(PROCESS).stream()
            .map(SimpleDefinitionDto::getKey)
            .collect(Collectors.toList());

    final Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis = new HashMap<>();
    for (final String processDefinitionKey : processDefinitionKeys) {
      final Map<String, String> reportIdToKpiValue = new HashMap<>();
      final List<KpiResultDto> kpiResultDtos = kpiService.evaluateKpiReports(processDefinitionKey);
      for (final KpiResultDto kpi : kpiResultDtos) {
        reportIdToKpiValue.put(kpi.getReportId(), kpi.getValue());
      }
      final LastKpiEvaluationResultsDto lastKpiEvaluationResultsDto =
          new LastKpiEvaluationResultsDto(reportIdToKpiValue);
      definitionKeyToKpis.put(processDefinitionKey, lastKpiEvaluationResultsDto);
    }
    processOverviewWriter.updateKpisForProcessDefinitions(definitionKeyToKpis);
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofSeconds(configurationService.getEntityConfiguration().getKpiRefreshInterval()));
  }

  @Override
  public synchronized boolean startScheduling() {
    log.info("Scheduling KPI evaluation scheduler.");
    return super.startScheduling();
  }
}
