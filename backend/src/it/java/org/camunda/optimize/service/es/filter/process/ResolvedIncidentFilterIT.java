/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;

public class ResolvedIncidentFilterIT extends AbstractFilterIT {

  @Test
  public void instanceLevelFilterByResolvedIncident() {
    // given
    // @formatter:off
    final List<ProcessInstanceEngineDto> deployedInstances = IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withDeletedIncident()
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();
    final List<ProcessFilterDto<?>> filter = resolvedIncidentFilter(FilterApplicationLevel.INSTANCE);

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filter);
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
    reportData.setFilter(filter);
    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(1.);
  }

  @Test
  public void viewLevelFilterByResolvedIncident() {
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

    final List<ProcessFilterDto<?>> filter = resolvedIncidentFilter(FilterApplicationLevel.VIEW);
    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setFilter(filter);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(result.getFirstMeasureData()).hasSize(3);
  }

  private static Stream<Arguments> filterLevelAndExpectedResult() {
    return Stream.of(
      Arguments.of(FilterApplicationLevel.INSTANCE, 3., 2.),
      Arguments.of(FilterApplicationLevel.VIEW, 2., 1.)
    );
  }

  @ParameterizedTest
  @MethodSource("filterLevelAndExpectedResult")
  public void canBeMixedWithOtherFilters(final FilterApplicationLevel filterLevel,
                                         final Double firstExpectedResult,
                                         final Double secondExpectedResult) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .executeDeployment();
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
    reportData.setFilter(resolvedIncidentFilter(filterLevel));

    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(2L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(firstExpectedResult);

    // when I add the flow node filter as well
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withResolvedIncident()
        .filterLevel(filterLevel)
        .add()
        .executingFlowNodes()
        .id(SERVICE_TASK_ID_2)
        .add()
        .buildList());
    numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(secondExpectedResult);
  }

  private List<ProcessFilterDto<?>> resolvedIncidentFilter(final FilterApplicationLevel filterLevel) {
    return ProcessFilterBuilder.filter()
      .withResolvedIncident()
      .filterLevel(filterLevel)
      .add()
      .buildList();
  }

}
