/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.TWO_SEQUENTIAL_TASKS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.VARIABLE_AGGREGATION_GROUP_BY_NONE;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;

public class MixedFilterIT extends AbstractFilterIT {

  private static final String SECOND_USER = "otherUser";
  private static final String SECOND_USER_PW = "otherPassword";
  private static final String CANDIDATE_GROUP_1 = "werewolves";
  private static final String CANDIDATE_GROUP_2 = "villagers";

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
    final ProcessReportResultDto result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesToEvaluate")
  public void testIncompatibleCombinationOfViewLevelFlowNodeDurationFilters(final ProcessReportDataType reportType) {
    // given
    OffsetDateTime firstActivityStart = LocalDateUtil.getCurrentDateTime();
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        BpmnModels.getLoopingProcess(), ImmutableMap.of("anotherRound", true));
    engineDatabaseExtension.changeFirstActivityInstanceStartDate(SERVICE_TASK_ID_1, firstActivityStart);
    engineDatabaseExtension.changeFirstActivityInstanceEndDate(SERVICE_TASK_ID_1, firstActivityStart.plusHours(1));
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
    final ProcessReportResultDto result = evaluateReport(
      reportType, instance.getProcessDefinitionKey(), instance.getProcessDefinitionVersion(), filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  private static Stream<Arguments> reportTypesWithFilterOperators() {
    return reportTypesToEvaluate()
      .flatMap(reportType -> Stream.of(IN, NOT_IN).map(operator -> Arguments.of(reportType, operator)));
  }

  @ParameterizedTest
  @MethodSource("reportTypesWithFilterOperators")
  public void testIncompatibleCombinationOfViewLevelAssigneeFilters(final ProcessReportDataType reportType,
                                                                    final FilterOperator operator) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_PW);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, instance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USER_PW, instance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .assignee()
      .operator(operator)
      .id(DEFAULT_USERNAME)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .assignee()
      .operator(operator)
      .id(SECOND_USER)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ProcessReportResultDto result = evaluateReport(reportType, processDefinition, filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
  }

  @ParameterizedTest
  @MethodSource("reportTypesWithFilterOperators")
  public void testIncompatibleCombinationOfViewLevelCandidateGroupFilters(final ProcessReportDataType reportType,
                                                                          final FilterOperator operator) {
    // given
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_1);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_2);

    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP_1);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(CANDIDATE_GROUP_2);
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filterList = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .operator(operator)
      .id(CANDIDATE_GROUP_1)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .candidateGroups()
      .id(CANDIDATE_GROUP_2)
      .operator(operator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ProcessReportResultDto result = evaluateReport(reportType, processDefinition, filterList);

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
    final ProcessReportResultDto result = evaluateReport(reportType, def.getKey(), def.getVersion(), filterList);

    // then
    assertReportWithIncompatibleFilters(reportType, result);
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
        .withOpenIncident()
        .add()
        .withResolvedIncident()
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
      ProcessFilterBuilder.filter().withOpenIncident().filterLevel(levelToApply).add().buildList(),
      ProcessFilterBuilder.filter().withResolvedIncident().filterLevel(levelToApply).add().buildList(),
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

  private static Stream<ProcessReportDataType> reportTypesToEvaluate() {
    return Stream.of(
      COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE,
      USER_TASK_FREQUENCY_GROUP_BY_USER_TASK,
      RAW_DATA,
      COUNT_PROC_INST_FREQ_GROUP_BY_NONE,
      VARIABLE_AGGREGATION_GROUP_BY_NONE,
      INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE
    );
  }

  private ProcessReportResultDto evaluateReport(final ProcessReportDataType reportType,
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
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
      case USER_TASK_FREQUENCY_GROUP_BY_USER_TASK:
      case INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE:
        return reportClient.evaluateMapReport(dataBuilder.build()).getResult();
      case RAW_DATA:
        return reportClient.evaluateRawReport(dataBuilder.build()).getResult();
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
      case VARIABLE_AGGREGATION_GROUP_BY_NONE:
        dataBuilder.setVariableName("someName").setVariableType(VariableType.LONG);
        return reportClient.evaluateNumberReport(dataBuilder.build()).getResult();
      default:
        throw new OptimizeIntegrationTestException(
          String.format("Cannot evaluate report of type %S in this test", reportType));
    }
  }

  private void assertReportWithIncompatibleFilters(final ProcessReportDataType reportType,
                                                   final ProcessReportResultDto reportResult) {
    // then the instance count is zero despite instance matching filters inclusively
    assertThat(reportResult.getInstanceCount()).isZero();
    assertThat(reportResult.getInstanceCountWithoutFilters()).isEqualTo(1L);
    // and the incompatible filters results in no data
    switch (reportType) {
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
      case USER_TASK_FREQUENCY_GROUP_BY_USER_TASK:
      case INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE:
        assertThat(((ReportMapResultDto) reportResult).getData()).isEmpty();
        break;
      case RAW_DATA:
        assertThat(((RawDataProcessReportResultDto) reportResult).getData()).isEmpty();
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
        assertThat(((NumberResultDto) reportResult).getData()).isZero();
        break;
      case VARIABLE_AGGREGATION_GROUP_BY_NONE:
        assertThat(((NumberResultDto) reportResult).getData()).isNull();
        break;
      default:
        throw new OptimizeIntegrationTestException(
          String.format("Cannot assert results of report of type %s in this test", reportType));
    }
  }

  private ProcessReportResultDto evaluateReport(final ProcessReportDataType reportType,
                                                final ProcessDefinitionEngineDto definitionEngineDto,
                                                final List<ProcessFilterDto<?>> filter) {
    return evaluateReport(reportType, definitionEngineDto.getKey(), definitionEngineDto.getVersionAsString(), filter);
  }

}
