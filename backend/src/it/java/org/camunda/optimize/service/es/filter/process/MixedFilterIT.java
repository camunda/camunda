/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;

public class MixedFilterIT extends AbstractFilterIT {

  @Test
  public void applyCombinationOfFiltersForFinishedInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      BpmnModels.getSingleUserTaskDiagram());
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

    // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    );
    final String expectedInstanceId = instanceEngineDto.getId();
    engineIntegrationExtension.finishAllRunningUserTasks(expectedInstanceId);

    // wrong not executed flow node
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId())
      .getStartTime();
    OffsetDateTime end = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId()).getEndTime();
    engineDatabaseExtension.changeProcessInstanceStartDate(instanceEngineDto.getId(), start.plusDays(2));
    engineDatabaseExtension.changeProcessInstanceEndDate(instanceEngineDto.getId(), end.plusDays(2));
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(USER_TASK_1)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.plusDays(1))
      .add()
      .fixedEndDate()
      .start(null)
      .end(end.plusDays(1))
      .add()
      .buildList();

    final RawDataProcessReportResultDto rawDataReportResultDto = createAndEvaluateReportWithFilter(
      processDefinition, filterList
    );

    // then
    assertThat(rawDataReportResultDto.getData()).hasSize(1);
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId()).isEqualTo(expectedInstanceId);
  }

  @Test
  public void applyCombinationOfFiltersForInProgressInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      BpmnModels.getSingleUserTaskDiagram());
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

    // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    );
    final String expectedInstanceId = instanceEngineDto.getId();

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId())
      .getStartTime();
    engineDatabaseExtension.changeProcessInstanceStartDate(instanceEngineDto.getId(), start.plusDays(2));
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .executingFlowNodes()
      .id(USER_TASK_1)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.plusDays(1L))
      .add()
      .buildList();

    RawDataProcessReportResultDto rawDataReportResultDto = createAndEvaluateReportWithFilter(
      processDefinition,
      filterList
    );

    // then
    assertThat(rawDataReportResultDto.getData()).hasSize(1);
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId()).isEqualTo(expectedInstanceId);
  }

  @Test
  public void incidentFilterCombination() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
        .withResolvedIncident()
      .startProcessInstance()
        .withResolvedAndOpenIncident()
      .startProcessInstance()
        .withOpenIncident()
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when I evaluate the report without filters
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(IncidentDataDeployer.PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .build();
    NumberResultDto numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(3L);
    assertThat(numberResult.getData()).isEqualTo(4.);

    // when I add a resolved + open incident filter
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withOpenIncidentsOnly()
        .add()
        .withResolvedIncidentsOnly()
        .add()
        .buildList());
    numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then I get only the process instance with the resolved and the open incident pending
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(numberResult.getData()).isEqualTo(2.);
  }

  @ParameterizedTest
  @MethodSource("invalidFilters")
  public void reportCreationWithInvalidFilters(List<ProcessFilterDto<?>> filters) {
    // given
    ProcessReportDataDto reportData = createReportWithDefinition(
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram()));
    reportData.setFilter(filters);
    SingleProcessReportDefinitionRequestDto definition = new SingleProcessReportDefinitionRequestDto();
    definition.setData(reportData);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(definition)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidFilters")
  public void reportUpdateWithInvalidFilters(List<ProcessFilterDto<?>> filters) {
    // given
    final String reportId = reportClient.createEmptySingleProcessReport();
    ProcessReportDataDto reportData = createReportWithDefinition(
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram()));
    reportData.setFilter(filters);
    SingleProcessReportDefinitionRequestDto definition = new SingleProcessReportDefinitionRequestDto();
    definition.setData(reportData);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, definition, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validFilters")
  public void reportEvaluationWithValidApplicationLevelsForFilters(List<ProcessFilterDto<?>> filters) {
    // given
    ProcessReportDataDto reportData = createReportWithDefinition(
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram()));
    reportData.setFilter(filters);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidFilters")
  public void reportEvaluationWithInvalidApplicationLevelsForFilters(List<ProcessFilterDto<?>> filters) {
    // given
    ProcessReportDataDto reportData = createReportWithDefinition(
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram()));
    reportData.setFilter(filters);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  protected RawDataProcessReportResultDto createAndEvaluateReportWithFilter(
    final ProcessDefinitionEngineDto processDefinition,
    final List<ProcessFilterDto<?>> filter) {
    ProcessReportDataDto reportData =
      createReportWithCompletedInstancesFilter(processDefinition);
    reportData.setFilter(filter);
    final String reportId = reportClient.createSingleProcessReport(reportData);
    return reportClient.evaluateRawReportById(reportId).getResult();
  }

  private ProcessReportDataDto createReportWithCompletedInstancesFilter(ProcessDefinitionEngineDto processDefinition) {
    ProcessReportDataDto processReportDataDto = createReportWithDefinition(processDefinition);
    processReportDataDto.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    return processReportDataDto;
  }

  private static Stream<List<ProcessFilterDto<?>>> invalidFilters() {
    return Stream.concat(
      buildFilters(FilterApplicationLevel.INSTANCE)
        .filter(it -> !it.get(0).validApplicationLevels().contains(FilterApplicationLevel.INSTANCE)),
      buildFilters(FilterApplicationLevel.VIEW)
        .filter(it -> !it.get(0).validApplicationLevels().contains(FilterApplicationLevel.VIEW))
    );
  }

  private static Stream<List<ProcessFilterDto<?>>> validFilters() {
    return Stream.concat(
      buildFilters(FilterApplicationLevel.INSTANCE)
        .filter(it -> it.get(0).validApplicationLevels().contains(FilterApplicationLevel.INSTANCE)),
      buildFilters(FilterApplicationLevel.VIEW)
        .filter(it -> it.get(0).validApplicationLevels().contains(FilterApplicationLevel.VIEW))
    );
  }

  private static Stream<List<ProcessFilterDto<?>>> buildFilters(final FilterApplicationLevel levelToApply) {
    return Stream.of(
      ProcessFilterBuilder.filter().executedFlowNodes().id("someId").filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().executingFlowNodes().id("someId").filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().canceledFlowNodes().id("someId").filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().canceledInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().nonCanceledInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().suspendedInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().nonSuspendedInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().completedInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().runningInstancesOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().withOpenIncidentsOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().withResolvedIncidentsOnly().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .rollingStartDate().start(10L, DateFilterUnit.HOURS).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .relativeStartDate().start(10L, DateFilterUnit.HOURS).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(LocalDateUtil.getCurrentDateTime().minusMinutes(60)).end(LocalDateUtil.getCurrentDateTime())
        .filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .fixedEndDate()
        .start(LocalDateUtil.getCurrentDateTime().minusMinutes(60)).end(LocalDateUtil.getCurrentDateTime())
        .filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .duration()
        .operator(GREATER_THAN)
        .unit(DurationFilterUnit.HOURS)
        .value(10L)
        .filterLevel(levelToApply)
        .add()
        .buildList(),
      ProcessFilterBuilder.filter()
        .variable().stringType().name("name").operator(CONTAINS).values(Collections.singletonList("someString"))
        .filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(
          "someId",
          DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L).operator(GREATER_THAN).build()
        ).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().candidateGroups().id("someId").filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().assignee().id("someId").filterLevel(levelToApply).add().buildList()
    );
  }

}
