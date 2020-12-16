/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;

public class OpenIncidentFilterIT extends AbstractFilterIT {

  @Test
  public void filterByOpenIncident() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withDeletedIncident()
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(ProcessFilterBuilder.filter().withOpenIncidentsOnly().add().buildList());
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    // when
    reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .build();
    reportData.setFilter(ProcessFilterBuilder.filter().withOpenIncidentsOnly().add().buildList());
    NumberResultDto numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(numberResult.getData()).isEqualTo(1.);
  }

  @Test
  public void canBeMixedWithOtherFilters() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withOpenIncidentsOnly()
        .add()
        .buildList());
    NumberResultDto numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(2L);
    // process instance (pi) with open incident + pi with resolved and open incident
    assertThat(numberResult.getData()).isEqualTo(3.);

    // when I add the flow node filter as well
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withOpenIncidentsOnly()
        .add()
        .executedFlowNodes()
        .id(SERVICE_TASK_ID_2)
        .add()
        .buildList());
    numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(numberResult.getData()).isEqualTo(2.);
  }

}
