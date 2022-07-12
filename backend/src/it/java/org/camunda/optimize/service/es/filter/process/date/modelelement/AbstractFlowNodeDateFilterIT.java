/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE_BY_FLOW_NODE;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractFlowNodeDateFilterIT extends AbstractFilterIT {

  protected static final OffsetDateTime DATE_1 = OffsetDateTime.parse("2019-05-09T00:00:00+02:00");
  protected static final OffsetDateTime DATE_2 = OffsetDateTime.parse("2021-06-07T00:00:00+02:00");
  protected static final String DEF_KEY = "defKey";
  private static final String DATE_1_STRING = "2019-05-09T00:00:00.000+0200";
  private static final String INSTANCE_1_START_DURATION_STRING = "10.0";
  private static final String INSTANCE_1_USER_TASK_1_DURATION_STRING = "20.0";
  private static final String INSTANCE_1_USER_TASK_2_DURATION_STRING = "30.0";
  private static final String INSTANCE_2_START_DURATION_STRING = "40.0";
  private static final String INSTANCE_2_USER_TASK_1_DURATION_STRING = "50.0";
  private static final String VARIABLE_1_VALUE = "value1";
  private static final String VARIABLE_2_VALUE = "value2";
  private static final String DEMO_USER = "demo";

  protected abstract void updateFlowNodeDate(final String instanceId, final String flowNodeId,
                                             final OffsetDateTime newDate);

  protected abstract List<ProcessFilterDto<?>> createViewLevelDateFilterForDate1();

  protected abstract List<ProcessFilterDto<?>> createInstanceLevelDateFilterForDate1(final List<String> flowNodeIds);

  protected abstract List<ProcessFilterDto<?>> createViewLevelDateFilterForDate2();

  protected abstract List<ProcessFilterDto<?>> createInvalidFilter();

  protected abstract ProcessGroupByType getDateReportGroupByType();

  @ParameterizedTest
  @MethodSource("flowNodeAndUserTaskMapReportTypeAndExpectedResults")
  public void viewLevel_filtersFlowNodes(final ProcessReportDataType reportType,
                                         final List<Tuple> expectedResults) {
    // given
    setupInstanceData();

    // when filtering for a flowNodeDate that exists in both instances, ie a filter that affects only flowNode data
    // and not instance count
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      evaluateReportWithFlowNodeDateFilter(
        reportType,
        createViewLevelDateFilterForDate1()
      );

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData())
      .filteredOn(r -> r.getValue() != null && r.getValue() > 0.)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResults);
  }

  @ParameterizedTest
  @MethodSource("flowNodeAndUserTaskMapReportTypeAndExpectedResults")
  public void viewLevel_filtersInstances(final ProcessReportDataType reportType) {
    // given
    setupInstanceData();

    // when filtering for a flowNodeDate that only exists in instance2, ie a filter that affects instance count
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      evaluateReportWithFlowNodeDateFilter(
        reportType,
        createViewLevelDateFilterForDate2()
      );

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
  }

  @Test
  public void viewLevel_filtersInstances_nonFlowNodeReport() {
    // given
    setupInstanceData();

    // when filtering for a flowNodeDate that only exists in instance2 (date2), ie a filter that affects instance count
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
      evaluateReportWithFlowNodeDateFilter(
        ProcessReportDataType.RAW_DATA,
        createViewLevelDateFilterForDate2()
      );

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
  }

  @Test
  public void viewLevel_distributedReport() {
    // given
    setupInstanceData();

    // when
    final ProcessReportDataDto reportData =
      buildReportData(FLOW_NODE_FREQ_GROUP_BY_VARIABLE_BY_FLOW_NODE, createViewLevelDateFilterForDate2());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
      reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getKey)
      .containsExactly(VARIABLE_2_VALUE);
    assertThat(result.getFirstMeasureData()
                 .stream()
                 .filter(hyperMapResultEntryDto -> VARIABLE_2_VALUE.equals(hyperMapResultEntryDto.getKey()))
                 .flatMap(hyperMapResultEntryDto -> hyperMapResultEntryDto.getValue().stream()))
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(
        new Tuple(START_EVENT, null),
        new Tuple(USER_TASK_1, null),
        new Tuple(USER_TASK_2, 1.),
        new Tuple(END_EVENT, null)
      );
  }

  @Test
  public void viewLevel_evaluateSavedReport_differentDateFilters() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY));
    ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance1.getId());
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance2.getId());
    final ProcessInstanceEngineDto instance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance3.getId());

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    updateFlowNodeDate(instance3.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance3.getId(), END_EVENT, DATE_2);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), DATE_1);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), DATE_1);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance3.getId(), DATE_2);
    importAllEngineEntitiesFromScratch();

    final List<ProcessFilterDto<?>> filters = createViewLevelDateFilterForDate2();
    filters.addAll(ProcessFilterBuilder.filter().fixedInstanceStartDate().start(DATE_1).end(DATE_1).add().buildList());
    final ProcessReportDataDto reportData = buildReportData(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE, filters);
    final String reportId = reportClient.createSingleProcessReport(reportData);

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReportById(reportId).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(new Tuple(END_EVENT, 1.));
  }

  @Test
  public void viewLevel_filterValidationFails() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final List<ProcessFilterDto<?>> invalidFilter = createInvalidFilter();
    final ProcessReportDataDto reportData = buildReportData(ProcessReportDataType.RAW_DATA, invalidFilter);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto(reportData))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void instanceLevel_filterWithOneFlowNodeId() {
    // given
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(DEF_KEY));
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance1.getId(), USER_TASK_1, DATE_1);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), USER_TASK_1, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      evaluateRawDataReportWithFlowNodeDateFilter(
        createInstanceLevelDateFilterForDate1(singletonList(START_EVENT))
      );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(instance1.getId());
  }

  @Test
  public void instanceLevel_filterWithMultipleFlowNodeIds() {
    // given
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto instance3 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    updateFlowNodeDate(instance3.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance3.getId(), END_EVENT, DATE_2);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      evaluateRawDataReportWithFlowNodeDateFilter(
        createInstanceLevelDateFilterForDate1(List.of(START_EVENT, END_EVENT))
      );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(instance1.getId(), instance2.getId());
  }

  @Test
  public void instanceLevel_evaluateSavedReport_differentDateFilters() {
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    final ProcessInstanceEngineDto instance3 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    updateFlowNodeDate(instance3.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance3.getId(), END_EVENT, DATE_1);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), DATE_1);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), DATE_1);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance3.getId(), DATE_2);
    importAllEngineEntitiesFromScratch();

    final List<ProcessFilterDto<?>> filters = createInstanceLevelDateFilterForDate1(singletonList(START_EVENT));
    filters.addAll(ProcessFilterBuilder.filter().fixedInstanceStartDate().start(DATE_1).end(DATE_1).add().buildList());
    final ProcessReportDataDto reportData = buildReportData(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE, filters);
    final String reportId = reportClient.createSingleProcessReport(reportData);

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReportById(reportId).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(new Tuple(START_EVENT, 1.), new Tuple(END_EVENT, 1.));
  }

  @ParameterizedTest
  @MethodSource("invalidFlowNodeSelections")
  public void instanceLevel_filterValidationFails(final List<String> invalidFlowNodeIds) {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    final List<ProcessFilterDto<?>> invalidFilter = createInstanceLevelDateFilterForDate1(invalidFlowNodeIds);
    final ProcessReportDataDto reportData = buildReportData(ProcessReportDataType.RAW_DATA, invalidFilter);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto(reportData))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void mixedLevel() {
    // given
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.deployAndStartProcess(getDoubleUserTaskDiagram(DEF_KEY));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_2);
    updateFlowNodeDate(instance1.getId(), USER_TASK_1, DATE_1);
    updateFlowNodeDate(instance1.getId(), USER_TASK_2, DATE_2);
    updateFlowNodeDate(instance1.getId(), END_EVENT, DATE_2);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), USER_TASK_1, DATE_2);
    updateFlowNodeDate(instance2.getId(), USER_TASK_2, DATE_1);
    updateFlowNodeDate(instance2.getId(), END_EVENT, DATE_1);
    importAllEngineEntitiesFromScratch();

    final List<ProcessFilterDto<?>> mixedLevelFilters = createViewLevelDateFilterForDate1();
    mixedLevelFilters.addAll(createInstanceLevelDateFilterForDate1(singletonList(USER_TASK_1)));

    // when applying an instanceFilter which excludes instance2 and a viewFilter which excludes userTask2
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      evaluateReportWithFlowNodeDateFilter(
        ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK,
        mixedLevelFilters
      );

    // then only 1 instance and 1 userTask is included in the result
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(new Tuple(USER_TASK_1, 1.));
  }

  protected ReportResultResponseDto<List<MapResultEntryDto>> evaluateReportWithFlowNodeDateFilter(final ProcessReportDataType reportType,
                                                                                                  final List<ProcessFilterDto<?>> flowNodeDateFilter) {
    final ProcessReportDataDto reportData = buildReportData(reportType, flowNodeDateFilter);
    return reportClient.evaluateMapReport(reportData).getResult();
  }

  protected ReportResultResponseDto<List<RawDataProcessInstanceDto>> evaluateRawDataReportWithFlowNodeDateFilter(
    final List<ProcessFilterDto<?>> flowNodeDateFilter) {
    final ProcessReportDataDto reportData = buildReportData(ProcessReportDataType.RAW_DATA, flowNodeDateFilter);
    return reportClient.evaluateRawReport(reportData).getResult();
  }

  protected ProcessReportDataDto buildReportData(final ProcessReportDataType reportType,
                                                 final List<ProcessFilterDto<?>> flowNodeDateFilters) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEF_KEY)
      .setProcessDefinitionVersion(LATEST_VERSION)
      .setReportDataType(reportType)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setFilter(flowNodeDateFilters)
      .build();
    if (isGroupByDateReport(reportData)) {
      reportData.getGroupBy().setType(getDateReportGroupByType());
    }
    return reportData;
  }

  private boolean isGroupByDateReport(final ProcessReportDataDto reportData) {
    return reportData.getGroupBy() != null
      && (ProcessGroupByType.END_DATE.equals(reportData.getGroupBy().getType())
      || ProcessGroupByType.START_DATE.equals(reportData.getGroupBy().getType()));
  }

  private void setupInstanceData() {
    // sets up two instances with StartEvent, UserTask1 and UserTask2 where instance2 has a different date for UserTask2
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getDoubleUserTaskDiagram(DEF_KEY));
    Map<String, Object> variables = new HashMap<>();

    variables.put("stringVar", VARIABLE_1_VALUE);
    final ProcessInstanceEngineDto instance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instance1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instance1.getId());

    variables.put("stringVar", VARIABLE_2_VALUE);
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(KERMIT_USER, KERMIT_USER, instance2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(KERMIT_USER, KERMIT_USER, instance2.getId());

    // end events are irrelevant and would bloat report results, we just need 2 userTasks and 1 non userTask to test
    // userTask and flowNode report filtering
    engineDatabaseExtension.removeFlowNodeFromActivityTable(instance1.getId(), END_EVENT);
    engineDatabaseExtension.removeFlowNodeFromActivityTable(instance2.getId(), END_EVENT);

    updateFlowNodeDate(instance1.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance1.getId(), USER_TASK_1, DATE_1);
    updateFlowNodeDate(instance1.getId(), USER_TASK_2, DATE_1);
    updateFlowNodeDate(instance2.getId(), START_EVENT, DATE_1);
    updateFlowNodeDate(instance2.getId(), USER_TASK_1, DATE_1);
    updateFlowNodeDate(instance2.getId(), USER_TASK_2, DATE_2);

    engineDatabaseExtension.changeFlowNodeTotalDuration(instance1.getId(), START_EVENT, 10L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance1.getId(), USER_TASK_1, 20L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance1.getId(), USER_TASK_2, 30L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), START_EVENT, 40L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), USER_TASK_1, 50L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance2.getId(), USER_TASK_2, 60L);

    importAllEngineEntitiesFromScratch();
  }

  private static Stream<Arguments> flowNodeAndUserTaskMapReportTypeAndExpectedResults() {
    return Stream.of(
      Arguments.of(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE, List.of(
        new Tuple(START_EVENT, 2.),
        new Tuple(USER_TASK_1, 2.),
        new Tuple(USER_TASK_2, 1.)
      )),
      Arguments.of(FLOW_NODE_DUR_GROUP_BY_FLOW_NODE, List.of(
        new Tuple(START_EVENT, 25.),
        new Tuple(USER_TASK_1, 35.),
        new Tuple(USER_TASK_2, 30.)
      )),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_USER_TASK,
        List.of(new Tuple(USER_TASK_1, 2.), new Tuple(USER_TASK_2, 1.))
      ),
      Arguments.of(
        USER_TASK_DUR_GROUP_BY_USER_TASK,
        List.of(new Tuple(USER_TASK_1, 35.), new Tuple(USER_TASK_2, 30.))
      ),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION,
        List.of(
          new Tuple(INSTANCE_1_START_DURATION_STRING, 1.),
          new Tuple(INSTANCE_1_USER_TASK_1_DURATION_STRING, 1.),
          new Tuple(INSTANCE_1_USER_TASK_2_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_START_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_USER_TASK_1_DURATION_STRING, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION,
        List.of(
          new Tuple(INSTANCE_1_USER_TASK_1_DURATION_STRING, 1.),
          new Tuple(INSTANCE_1_USER_TASK_2_DURATION_STRING, 1.),
          new Tuple(INSTANCE_2_USER_TASK_1_DURATION_STRING, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE,
        singletonList(new Tuple(DATE_1_STRING, 3.))
      ),
      Arguments.of(
        USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE,
        singletonList(new Tuple(DATE_1_STRING, 33.))
      ),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE,
        singletonList(new Tuple(DATE_1_STRING, 5.))
      ),
      Arguments.of(
        FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE,
        singletonList(new Tuple(DATE_1_STRING, 30.))
      ),
      Arguments.of(
        USER_TASK_FREQ_GROUP_BY_ASSIGNEE,
        List.of(
          new Tuple(DEMO_USER, 2.),
          new Tuple(KERMIT_USER, 1.)
        )
      ),
      Arguments.of(
        USER_TASK_DUR_GROUP_BY_ASSIGNEE,
        List.of(
          new Tuple(DEMO_USER, 25.),
          new Tuple(KERMIT_USER, 50.)
        )
      ),
      Arguments.of(
        FLOW_NODE_FREQ_GROUP_BY_VARIABLE,
        List.of(
          new Tuple(VARIABLE_1_VALUE, 3.),
          new Tuple(VARIABLE_2_VALUE, 2.)
        )
      ),
      Arguments.of(
        FLOW_NODE_DUR_GROUP_BY_VARIABLE,
        List.of(
          new Tuple(VARIABLE_1_VALUE, 20.),
          new Tuple(VARIABLE_2_VALUE, 45.)
        )
      )
    );
  }

  private static Stream<List<String>> invalidFlowNodeSelections() {
    return Stream.of(Collections.emptyList(), null);
  }

}
