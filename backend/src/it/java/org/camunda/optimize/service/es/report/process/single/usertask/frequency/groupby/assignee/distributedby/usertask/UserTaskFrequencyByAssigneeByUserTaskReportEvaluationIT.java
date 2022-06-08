/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.assignee.distributedby.usertask;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class UserTaskFrequencyByAssigneeByUserTaskReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1.)
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, 1.)
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 1.)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstance_whenAssigneeCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    importAllEngineEntitiesFromScratch();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCache().resetCache();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_USERNAME)
          .distributedByContains(USER_TASK_1, 1.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndLeaveTasks2BUnassigned(processInstanceDto);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1.)
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, 1.)
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 1.)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessesInstances() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithDefaultAndLeaveTasks2BUnassigned(processInstanceDto2);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 2.)
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, 2.)
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 1.)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, 1.)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 1.)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";

    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance1.getId());
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstance2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1.)
          .distributedByContains(USER_TASK_2, null)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void oneAssigneeHasWorkedOnTasksThatTheOtherDidNot() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first task with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    // finish second task with second user
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto1.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first task with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto2.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto2.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first task with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto2.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto2.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(SECOND_USER, SECOND_USER_FULL_NAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    final ProcessDefinitionEngineDto processDefinition2 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto3.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult1 = reportClient.evaluateHyperMapReport(
      reportData1).getResult();
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult2 = reportClient.evaluateHyperMapReport(
      reportData2).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 2.)
      .doAssert(actualResult1);

    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(actualResult2);
    // @formatter:on
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).isEmpty();
  }

  @Data
  @AllArgsConstructor
  static class FlowNodeStatusTestValues {
    List<ProcessFilterDto<?>> processFilter;
    HyperMapResultEntryDto expectedFrequencyValues;
  }

  private static HyperMapResultEntryDto getExpectedResultsMap(Double userTask1Result, Double userTask2Result) {
    List<MapResultEntryDto> groupByResults = new ArrayList<>();
    MapResultEntryDto firstUserTask = new MapResultEntryDto(USER_TASK_1, userTask1Result, USER_TASK_1_NAME);
    groupByResults.add(firstUserTask);
    MapResultEntryDto secondUserTask = new MapResultEntryDto(USER_TASK_2, userTask2Result, USER_TASK_2_NAME);
    groupByResults.add(secondUserTask);
    return new HyperMapResultEntryDto(DEFAULT_USERNAME, groupByResults, DEFAULT_FULLNAME);
  }

  protected static Stream<FlowNodeStatusTestValues> getFlowNodeStatusExpectedValues() {
    return Stream.of(
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(),
        getExpectedResultsMap(1., 1.)
      ),
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(),
        getExpectedResultsMap(1., null)
      ),
      new FlowNodeStatusTestValues(
        ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(),
        getExpectedResultsMap(1., null)
      )
    );
  }

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusExpectedValues")
  public void evaluateReportWithFlowNodeStatusFilter(FlowNodeStatusTestValues flowNodeStatusTestValues) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      firstInstance.getId()
    );
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());

    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(secondInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(flowNodeStatusTestValues.processFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).hasSize(1);
    assertThat(actualResult.getFirstMeasureData().get(0)).isEqualTo(flowNodeStatusTestValues.expectedFrequencyValues);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusFilterCanceled() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, claim and cancel second
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      firstInstance.getId()
    );
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(firstInstance.getId(), USER_TASK_2);

    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim and cancel first running task
    engineIntegrationExtension.claimAllRunningUserTasks(secondInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).hasSize(1);
    assertThat(actualResult.getFirstMeasureData().get(0)).isEqualTo(getExpectedResultsMap(1., 1.));
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(USER_TASK_1).multiInstance().cardinality("2").multiInstanceDone()
        .endEvent()
        .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        processWithMultiInstanceUserTask
      );
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
      .distributedByContains(USER_TASK_1, 2.)
      .doAssert(actualResult);
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
        processDefinition.getId());
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .processInstanceCountWithoutFilters(11L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
      .distributedByContains(USER_TASK_1, 11.)
      .doAssert(actualResult);
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    final OffsetDateTime processStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
        .getStartTime();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(0L)
      .processInstanceCountWithoutFilters(0L)
      .measure(ViewProperty.FREQUENCY)
      .doAssert(actualResult);

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(DEFAULT_USERNAME, DEFAULT_FULLNAME)
      .distributedByContains(USER_TASK_1, 1.)
      .doAssert(actualResult);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  protected ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  private void finishUserTask1AWithDefaultAndLeaveTasks2BUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private void finishUserTask1AWithDefaultAndTaskB2WithSecondUser(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
    // finish user task 2 and B with second user
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .name(USER_TASK_1_NAME)
      .userTask(USER_TASK_2)
      .name(USER_TASK_2_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .parallelGateway()
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent()
      .moveToLastGateway()
      .userTask(USER_TASK_A)
      .userTask(USER_TASK_B)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }
}
