/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;

public class NoIncidentFilterIT extends AbstractFilterIT {

  @Test
  public void filterByDoesNotContainIncidentsOnly() {
    // given
    // @formatter:off
    final List<ProcessInstanceEngineDto> deployedInstances = IncidentDataDeployer.dataDeployer(incidentClient)
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
    reportData.setFilter(ProcessFilterBuilder.filter().noIncidents().add().buildList());
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId).containsExactly(deployedInstances.get(0).getId());

    // when
    reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
    reportData.setFilter(ProcessFilterBuilder.filter().noIncidents().add().buildList());
    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(0.);
  }

  @Test
  public void canBeMixedWithOtherFilters() {
    // given
    final ProcessInstanceEngineDto incidentInstance = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    final ProcessInstanceEngineDto longDurationNoIncidentInstance = engineIntegrationExtension.startProcessInstance(
      incidentInstance.getDefinitionId());
    final ProcessInstanceEngineDto secondNoIncidentInstance = engineIntegrationExtension.startProcessInstance(
      incidentInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(ProcessFilterBuilder.filter().noIncidents().add().buildList());
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getData()).extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(longDurationNoIncidentInstance.getId(), secondNoIncidentInstance.getId());

    // when I add a flow node duration filter
    engineDatabaseExtension.changeFlowNodeTotalDuration(longDurationNoIncidentInstance.getId(), START_EVENT, 20000);
    importAllEngineEntitiesFromScratch();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .noIncidents()
        .add()
        .flowNodeDuration()
        .flowNode(
          START_EVENT,
          DurationFilterDataDto.builder().unit(DurationUnit.SECONDS).value(15L).operator(GREATER_THAN).build()
        )
        .add()
        .buildList()
    );
    result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getData()).extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(longDurationNoIncidentInstance.getId());
  }

}
