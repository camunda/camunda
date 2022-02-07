/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;

public class DeletedIncidentFilterIT extends AbstractFilterIT {

  @Test
  public void instanceLevelFilterByDeletedIncident() {
    // given
    // @formatter:off
    final List<ProcessInstanceEngineDto> deployedInstances = IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
       .withOpenIncident()
      .startProcessInstance()
       .withResolvedIncident()
      .startProcessInstance()
       .withDeletedIncident()
      .startProcessInstance()
       .withoutIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();
    final List<ProcessFilterDto<?>> filter = deletedIncidentFilter();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filter);
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData)
      .getResult();

    // then the result contains only the instance with a deleted incident
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(result.getData()).hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId).containsExactly(deployedInstances.get(2).getId());

    // when
    reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
    reportData.setFilter(filter);
    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(1.);
  }

  @Test
  public void canBeMixedWithOtherFilters() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withOpenIncident()
      .startProcessInstance()
       .withDeletedIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
    reportData.setFilter(deletedIncidentFilter());
    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(1.);

    // when I add the flow node filter as well
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withDeletedIncident()
        .filterLevel(FilterApplicationLevel.INSTANCE)
        .add()
        .executedFlowNodes()
        .id(SERVICE_TASK_ID_2)
        .add()
        .buildList());
    numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(0L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(0.);
  }

  private List<ProcessFilterDto<?>> deletedIncidentFilter() {
    return ProcessFilterBuilder.filter()
      .withDeletedIncident()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .add()
      .buildList();
  }
}
