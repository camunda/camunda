/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@AllArgsConstructor
@Component
@Slf4j
public class KpiEvaluationSchedulerService extends AbstractScheduledService {

  private final ProcessOverviewWriter processOverviewWriter;
  private final DefinitionService definitionService;
  private final ConfigurationService configurationService;
  private final KpiService kpiService;

  @PostConstruct
  public void init() {
    startScheduling();
  }

  @Override
  public synchronized boolean startScheduling() {
    log.info("Scheduling KPI evaluation scheduler.");
    return super.startScheduling();
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
    final List<String> processDefinitionKeys = definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def -> !def.getIsEventProcess())
      .map(SimpleDefinitionDto::getKey)
      .collect(Collectors.toList());

    Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis = new HashMap<>();
    for (String processDefinitionKey : processDefinitionKeys) {
      Map<String, String> reportIdToKpiValue = new HashMap<>();
      List<KpiResultDto> kpiResultDtos = kpiService.evaluateKpiReports(processDefinitionKey);
      for (KpiResultDto kpi : kpiResultDtos) {
        reportIdToKpiValue.put(kpi.getReportId(), kpi.getValue());
      }
      LastKpiEvaluationResultsDto lastKpiEvaluationResultsDto = new LastKpiEvaluationResultsDto(reportIdToKpiValue);
      definitionKeyToKpis.put(processDefinitionKey, lastKpiEvaluationResultsDto);
    }
    processOverviewWriter.updateKpisForProcessDefinitions(definitionKeyToKpis);
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(configurationService.getEntityConfiguration().getKpiRefreshInterval(), TimeUnit.SECONDS);
  }

}
