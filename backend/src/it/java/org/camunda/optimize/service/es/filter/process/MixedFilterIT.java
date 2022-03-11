/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.Disabled;
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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.VARIABLE_AGGREGATION_GROUP_BY_NONE;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class MixedFilterIT extends AbstractFilterIT {

  private static final String CANDIDATE_GROUP_1 = "werewolves";

  @Test
  public void applyCombinationOfFiltersForFinishedInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
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
      .fixedInstanceStartDate()
      .start(null)
      .end(start.plusDays(1))
      .add()
      .fixedInstanceEndDate()
      .start(null)
      .end(end.plusDays(1))
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataReportResultDto =
      createAndEvaluateReportWithFilter(
        processDefinition, filterList
      );

    // then
    assertThat(rawDataReportResultDto.getData()).hasSize(1);
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId()).isEqualTo(expectedInstanceId);
  }

  @Test
  public void applyCombinationOfFiltersForInProgressInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
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
      .fixedInstanceStartDate()
      .start(null)
      .end(start.plusDays(1L))
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataReportResultDto = createAndEvaluateReportWithFilter(
      processDefinition, filterList
    );

    // then
    assertThat(rawDataReportResultDto.getData()).hasSize(1);
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId()).isEqualTo(expectedInstanceId);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelFlowNodeStatusFilters(final ProcessReportDataType reportType) {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .completedFlowNodesOnly()
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .runningFlowNodesOnly()
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @Disabled("Disabled as there currently are no incompatible duration viewLevel filters. To be adjusted with OPT-5349" +
    " and OPT-5350")
  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelFlowNodeDurationFilters(final ProcessReportDataType reportType) {
    // given
    OffsetDateTime firstActivityStart = LocalDateUtil.getCurrentDateTime();
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        BpmnModels.getLoopingProcess(), ImmutableMap.of("anotherRound", true));
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(SERVICE_TASK_ID_1, firstActivityStart);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(SERVICE_TASK_ID_1, firstActivityStart.plusHours(1));
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .flowNodeDuration()
      .flowNode(
        SERVICE_TASK_ID_1,
        DurationFilterDataDto.builder().unit(DurationFilterUnit.HOURS).value(1L).operator(GREATER_THAN_EQUALS).build()
      )
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .flowNodeDuration()
      .flowNode(
        SERVICE_TASK_ID_1,
        DurationFilterDataDto.builder().unit(DurationFilterUnit.HOURS).value(1L).operator(LESS_THAN).build()
      )
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<?> result = evaluateReport(
      reportType, instance.getProcessDefinitionKey(), instance.getProcessDefinitionVersion(), filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelAssigneeFilters(final ProcessReportDataType reportType) {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, instance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .assignee()
      .operator(MembershipFilterOperator.IN)
      .id(DEFAULT_USERNAME)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .assignee()
      .operator(NOT_IN)
      .id(DEFAULT_USERNAME)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelCandidateGroupFilters(final ProcessReportDataType reportType) {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_1);

    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP_1);
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .operator(MembershipFilterOperator.IN)
      .id(CANDIDATE_GROUP_1)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .candidateGroups()
      .id(CANDIDATE_GROUP_1)
      .operator(NOT_IN)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelIncidentFilters(final ProcessReportDataType reportType) {
    // given
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(TWO_SEQUENTIAL_TASKS)
      .startProcessInstance()
      .withResolvedAndOpenIncident()
      .executeDeployment();
    importAllEngineEntitiesFromScratch();
    final ProcessDefinitionOptimizeDto def = elasticSearchIntegrationTestExtension.getAllProcessDefinitions().get(0);

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .withOpenIncident()
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .withResolvedIncident()
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<?> result = evaluateReport(reportType, def.getKey(), def.getVersion(), filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelFlowNodeDateFilters(final ProcessReportDataType reportType) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(now)
      .end(now)
      .add()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(now.minusDays(1))
      .end(now.minusDays(1))
      .add()
      .buildList();
    ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(1L, DateUnit.YEARS)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(1L, DateUnit.WEEKS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfInstanceLevelFlowNodeDateFilters(final ProcessReportDataType reportType) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
    ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(now)
      .end(now)
      .add()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(now.minusDays(1))
      .end(now.minusDays(1))
      .add()
      .buildList();
    ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isZero();

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(1L, DateUnit.YEARS)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(1L, DateUnit.WEEKS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isZero();
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void viewLevelFlowNodeDateFilterCombinations(final ProcessReportDataType reportType) {
    // given
    final OffsetDateTime now = OffsetDateTime.parse("2021-05-05T00:00:00+02:00");
    dateFreezer().dateToFreeze(now).freezeDateAndReturn();
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance1.getId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(instance1.getId(), now.minusDays(1));

    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance2.getId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(instance2.getId(), now.minusDays(6));
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(now.minusDays(2))
      .end(now)
      .add()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(1L, DateUnit.DAYS)
      .add()
      .buildList();
    ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(1L, DateUnit.WEEKS)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(1L, DateUnit.WEEKS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(now.minusDays(2))
      .end(now)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(3L, DateUnit.DAYS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void instanceLevelFlowNodeDateFilterCombinations(final ProcessReportDataType reportType) {
    // given
    final OffsetDateTime now = OffsetDateTime.parse("2021-05-05T00:00:00+02:00");
    dateFreezer().dateToFreeze(now).freezeDateAndReturn();
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance1.getId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(instance1.getId(), now.minusDays(1));

    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance2.getId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(instance2.getId(), now.minusDays(6));
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(now.minusDays(2))
      .end(now)
      .add()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(USER_TASK_1))
      .start(1L, DateUnit.DAYS)
      .add()
      .buildList();
    ReportResultResponseDto<?> result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(1L, DateUnit.WEEKS)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(USER_TASK_1))
      .start(1L, DateUnit.WEEKS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);

    // when
    filterList = ProcessFilterBuilder
      .filter()
      .fixedFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(START_EVENT))
      .start(now.minusDays(2))
      .end(now)
      .add()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(Collections.singletonList(USER_TASK_1))
      .start(3L, DateUnit.DAYS)
      .add()
      .buildList();
    result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
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
      .setReportDataType(ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE)
      .build();
    ReportResultResponseDto<Double> numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(numberResult.getInstanceCount()).isEqualTo(3L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(4.);

    // when I add a resolved + open incident filter
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .withOpenIncident()
        .add()
        .withResolvedIncident()
        .add()
        .buildList());
    numberResult = reportClient.evaluateNumberReport(reportData).getResult();

    // then I get only the process instance with the resolved and the open incident pending
    assertThat(numberResult.getInstanceCount()).isEqualTo(1L);
    assertThat(numberResult.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(numberResult.getFirstMeasureData()).isEqualTo(2.);
  }

  @ParameterizedTest
  @MethodSource("invalidFilters")
  public void reportCreationWithInvalidFilters(List<ProcessFilterDto<?>> filters) {
    // given
    ProcessReportDataDto reportData = createReportWithDefinition(
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram()));
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
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram()));
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
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram()));
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
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram()));
    reportData.setFilter(filters);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  protected ReportResultResponseDto<List<RawDataProcessInstanceDto>> createAndEvaluateReportWithFilter(
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

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<List<ProcessFilterDto<?>>> invalidFilters() {
    return Stream.concat(
      buildFilters(FilterApplicationLevel.INSTANCE)
        .filter(it -> !it.get(0).validApplicationLevels().contains(FilterApplicationLevel.INSTANCE)),
      buildFilters(FilterApplicationLevel.VIEW)
        .filter(it -> !it.get(0).validApplicationLevels().contains(FilterApplicationLevel.VIEW))
    );
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
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
      ProcessFilterBuilder.filter().withOpenIncident().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .rollingInstanceStartDate().start(10L, DateUnit.HOURS).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .relativeInstanceStartDate().start(10L, DateUnit.HOURS).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .fixedInstanceStartDate()
        .start(LocalDateUtil.getCurrentDateTime().minusMinutes(60)).end(LocalDateUtil.getCurrentDateTime())
        .filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .fixedInstanceEndDate()
        .start(LocalDateUtil.getCurrentDateTime().minusMinutes(60)).end(LocalDateUtil.getCurrentDateTime())
        .filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter()
        .duration()
        .operator(ComparisonOperator.GREATER_THAN)
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
          DurationFilterDataDto.builder()
            .unit(DurationFilterUnit.SECONDS)
            .value(0L)
            .operator(ComparisonOperator.GREATER_THAN)
            .build()
        ).filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().candidateGroups().id("someId").filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().assignee().id("someId").filterLevel(levelToApply).add().buildList()
    );
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<ProcessReportDataType> reportTypesToEvaluate() {
    return Stream.of(
      FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE,
      USER_TASK_FREQ_GROUP_BY_USER_TASK,
      RAW_DATA,
      PROC_INST_FREQ_GROUP_BY_NONE,
      VARIABLE_AGGREGATION_GROUP_BY_NONE,
      INCIDENT_FREQ_GROUP_BY_FLOW_NODE
    );
  }

  private ReportResultResponseDto<? extends Object> evaluateReport(final ProcessReportDataType reportType,
                                                                   final String definitionKey,
                                                                   final String definitionVersion,
                                                                   final List<ProcessFilterDto<?>> filter) {
    final TemplatedProcessReportDataBuilder dataBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(reportType)
      .setFilter(filter);
    switch (reportType) {
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
      case USER_TASK_FREQ_GROUP_BY_USER_TASK:
      case INCIDENT_FREQ_GROUP_BY_FLOW_NODE:
        return reportClient.evaluateMapReport(dataBuilder.build()).getResult();
      case RAW_DATA:
        return reportClient.evaluateRawReport(dataBuilder.build()).getResult();
      case PROC_INST_FREQ_GROUP_BY_NONE:
      case VARIABLE_AGGREGATION_GROUP_BY_NONE:
        dataBuilder.setVariableName("someName").setVariableType(VariableType.LONG);
        return reportClient.evaluateNumberReport(dataBuilder.build()).getResult();
      default:
        throw new OptimizeIntegrationTestException(
          String.format("Cannot evaluate report of type %S in this test", reportType));
    }
  }

  private void assertReportWithIncompatibleFilters(final ProcessReportDataType reportType,
                                                   final ReportResultResponseDto<?> reportResult) {
    // then the instance count is zero despite instance matching filters inclusively
    assertThat(reportResult.getInstanceCount()).isZero();
    assertThat(reportResult.getInstanceCountWithoutFilters()).isEqualTo(1L);
    // and the incompatible filters results in no data
    switch (reportType) {
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
      case USER_TASK_FREQ_GROUP_BY_USER_TASK:
      case INCIDENT_FREQ_GROUP_BY_FLOW_NODE:
      case RAW_DATA:
        assertThat(((ReportResultResponseDto<List<?>>) reportResult).getFirstMeasureData()).isEmpty();
        break;
      case PROC_INST_FREQ_GROUP_BY_NONE:
        assertThat(((ReportResultResponseDto<Double>) reportResult).getFirstMeasureData()).isZero();
        break;
      case VARIABLE_AGGREGATION_GROUP_BY_NONE:
        assertThat(((ReportResultResponseDto<Double>) reportResult).getFirstMeasureData()).isNull();
        break;
      default:
        throw new OptimizeIntegrationTestException(
          String.format("Cannot assert results of report of type %s in this test", reportType));
    }
  }

  private ReportResultResponseDto<?> evaluateReport(final ProcessReportDataType reportType,
                                                    final ProcessDefinitionEngineDto definitionEngineDto,
                                                    final List<ProcessFilterDto<?>> filter) {
    return evaluateReport(reportType, definitionEngineDto.getKey(), definitionEngineDto.getVersionAsString(), filter);
  }

}
