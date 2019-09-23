/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.frequency.groupby.candidate_group;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportHyperMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class UserTaskFrequencyByCandidateGroupByUserTaskReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_1_NAME = "userTask1Name";
  private static final String USER_TASK_2_NAME = "userTask2Name";
  private static final String USER_TASK_2 = "userTask2";
  private static final String FIRST_CANDIDATE_GROUP = "firstGroup";
  private static final String SECOND_CANDIDATE_GROUP = "secondGroup";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";


  @Before
  public void init() {
    engineRule.createGroup(FIRST_CANDIDATE_GROUP);
    engineRule.createGroup(SECOND_CANDIDATE_GROUP);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ProcessReportHyperMapResult> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));

    final ProcessReportHyperMapResult actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 1L)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, 1L)
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, 1L)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, 1L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForMultipleCandidateGroups() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish second task with
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();


    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 2L)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, 2L)
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, 2L)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, 2L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .isComplete(false)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 2L)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, null)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void oneAssigneeHasWorkedOnTasksThatTheOtherDidNot() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task with first group
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task with second group
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task with default user
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto2.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task with default user
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto2.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
      .groupByContains(SECOND_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 1L, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task with default user
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto2.getId());


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_2, 1L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 2L, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition1.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks();

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinition2.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto3.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ProcessReportHyperMapResult actualResult1 = evaluateHyperMapReport(reportData1).getResult();
    final ProcessReportHyperMapResult actualResult2 = evaluateHyperMapReport(reportData2).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 2L)
      .doAssert(actualResult1);

    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 1L)
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

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getProcessInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(0));
  }

  @Data
  @AllArgsConstructor
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;
    HyperMapResultEntryDto<Long> expectedFrequencyValues;
  }

  private static HyperMapResultEntryDto<Long> getExpectedResultsMap(Long userTask1Result, Long userTask2Result) {
    List<MapResultEntryDto<Long>> groupByResults = new ArrayList<>();
    MapResultEntryDto<Long> firstUserTask = new MapResultEntryDto<>(USER_TASK_1, userTask1Result, USER_TASK_1_NAME);
    groupByResults.add(firstUserTask);
    MapResultEntryDto<Long> secondUserTask = new MapResultEntryDto<>(USER_TASK_2, userTask2Result, USER_TASK_2_NAME);
    groupByResults.add(secondUserTask);
    return new HyperMapResultEntryDto<>(FIRST_CANDIDATE_GROUP, groupByResults);
  }

  protected static Object[] getExecutionStateExpectedValues() {


    return new Object[]{
      new ExecutionStateTestValues(FlowNodeExecutionState.RUNNING, getExpectedResultsMap(1L, 1L)),
      new ExecutionStateTestValues(FlowNodeExecutionState.COMPLETED, getExpectedResultsMap(1L, null)),
      new ExecutionStateTestValues(FlowNodeExecutionState.ALL, getExpectedResultsMap(2L, 1L))
    };
  }

  @Test
  @Parameters(method = "getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues testValues) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.claimAllRunningUserTasks(processInstanceDto.getId());

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    // claim first running task
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.claimAllRunningUserTasks(processInstanceDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(testValues.executionState);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(1));
    assertThat(actualResult.getData().get(0), is(testValues.expectedFrequencyValues));
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

    final ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 2L)
      .doAssert(actualResult);
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
      engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 11L)
      .doAssert(actualResult);
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());

    final OffsetDateTime processStartTime = engineRule.getHistoricProcessInstance(processInstanceDto.getId())
      .getStartTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ProcessReportHyperMapResult actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(0L)
      .doAssert(actualResult);

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(FIRST_CANDIDATE_GROUP)
        .distributedByContains(USER_TASK_1, 1L)
      .doAssert(actualResult);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  protected ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  private void finishUserTask1AWithFirstAndTaskB2WithSecondGroup(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish user task 1 and A with default user
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish user task 2 and B with second user
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineRule.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(processKey, tenant);
        engineRule.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
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
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
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
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

}
