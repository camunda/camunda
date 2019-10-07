/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.candidate_group.distributed_by.none;

import com.google.common.collect.ImmutableMap;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.nonNull;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public abstract class AbstractUserTaskDurationByCandidateGroupReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK1 = "userTask1";
  private static final String USER_TASK2 = "userTask2";

  protected static final String FIRST_CANDIDATE_GROUP = "firstGroup";
  protected static final String SECOND_CANDIDATE_GROUP = "secondGroup";
  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @Before
  public void init() {
    engineRule.createGroup(FIRST_CANDIDATE_GROUP);
    engineRule.createGroup(SECOND_CANDIDATE_GROUP);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse =
      evaluateDurationMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));

    final ProcessDurationReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
    );

    assertThat(result.getProcessInstanceCount(), is(1L));
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

    changeDuration(processInstanceDto, USER_TASK1, 10L);
    changeDuration(processInstanceDto, USER_TASK2, 20L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse =
      evaluateDurationMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    final ProcessDurationReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L, 20L))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L))
    );

    assertThat(result.getProcessInstanceCount(), is(1L));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDurations))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDurations))
    );

    assertThat(result.getProcessInstanceCount(), is(2L));
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ProcessDurationReportMapResultDto> results =
      evaluateDurationMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(FIRST_CANDIDATE_GROUP, setDurations, SECOND_CANDIDATE_GROUP, setDurations)
    );
    assertThat(results.get(MIN).getProcessInstanceCount(), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 10L);
    changeDuration(processInstanceDto2, USER_TASK2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L))
    );
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 10L);
    changeDuration(processInstanceDto2, USER_TASK2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ProcessDurationReportMapResultDto> results =
      evaluateDurationMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(FIRST_CANDIDATE_GROUP, new Long[]{10L}, SECOND_CANDIDATE_GROUP, new Long[]{20L})
    );
    assertThat(results.get(MIN).getIsComplete(), is(true));
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 10L);
    changeDuration(processInstanceDto2, USER_TASK2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto resultDto = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getProcessInstanceCount(), is(2L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 10L);
    changeDuration(processInstanceDto2, USER_TASK2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto<Long>> resultData = result.getData();
      assertThat(resultData.size(), is(2));
      final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
      assertThat(
        resultKeys,
        // expect ascending order
        contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
      );
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 10L);
    changeDuration(processInstanceDto2, USER_TASK2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains(resultLabels.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }


  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK1, 10L);
    changeDuration(processInstanceDto1, USER_TASK2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK1, 100L);
    changeDuration(processInstanceDto2, USER_TASK2, 2L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = createReport(processDefinition);
      reportData.getConfiguration().setAggregationType(aggType);
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

      // then
      assertThat(result.getData().size(), is(2));
      assertCorrectValueOrdering(result);
    });
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition1.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinition2.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto3);
    changeDuration(processInstanceDto3, 20L);
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(processDefinition2.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto4);
    changeDuration(processInstanceDto4, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessDurationReportMapResultDto result1 = evaluateDurationMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ProcessDurationReportMapResultDto result2 = evaluateDurationMapReport(reportData2).getResult();

    // then
    assertThat(result1.getData().size(), is(1));
    assertThat(
      result1.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(40L))
    );

    assertThat(result2.getData().size(), is(1));
    assertThat(
      result2.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L))
    );
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
    ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getProcessInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, 300L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithFirstAndSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, 600L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ProcessDurationReportMapResultDto> results =
      evaluateDurationMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, ImmutableMap.of(FIRST_CANDIDATE_GROUP, new Long[]{100L, 300L, 600L}));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @Data
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;
    Map<String, Long> expectedIdleDurationValues;
    Map<String, Long> expectedWorkDurationValues;
    Map<String, Long> expectedTotalDurationValues;
  }

  private static Map<String, Long> getExpectedResultsMap(Long candidateGroup1Results, Long candidateGroup2Results) {
    Map<String, Long> result = new HashMap<>();
    if (nonNull(candidateGroup1Results)) {
      result.put(FIRST_CANDIDATE_GROUP, candidateGroup1Results);
    }
    if (nonNull(candidateGroup2Results)) {
      result.put(SECOND_CANDIDATE_GROUP, candidateGroup2Results);
    }
    return result;
  }

  protected static Object[] getExecutionStateExpectedValues() {

    ExecutionStateTestValues runningStateValues =
      new ExecutionStateTestValues();
    runningStateValues.executionState = FlowNodeExecutionState.RUNNING;
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200L, 200L);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500L, 500L);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700L, 700L);


    ExecutionStateTestValues completedStateValues = new ExecutionStateTestValues();
    completedStateValues.executionState = FlowNodeExecutionState.COMPLETED;
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100L, null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100L, null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100L, null);


    ExecutionStateTestValues allStateValues = new ExecutionStateTestValues();
    allStateValues.executionState = FlowNodeExecutionState.ALL;
    allStateValues.expectedIdleDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 200L, 200L),
      200L
    );
    allStateValues.expectedWorkDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 500L, 500L),
      500L
    );
    allStateValues.expectedTotalDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 700L, 700L),
      700L
    );

    return new Object[]{
      runningStateValues,
      completedStateValues,
      allStateValues
    };
  }

  @Test
  @Parameters(method = "getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues testValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK1, 100L);
    engineRule.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineRule.claimAllRunningUserTasks(processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK2, 700L);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK2, 500L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    // claim first running task
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.claimAllRunningUserTasks(processInstanceDto2.getId());

    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK1, 700L);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK1, 500L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(testValues.executionState);
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertEvaluateReportWithExecutionState(result, testValues);
  }

  protected abstract void assertEvaluateReportWithExecutionState(ProcessDurationReportMapResultDto result,
                                                                 ExecutionStateTestValues expectedValues);

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(USER_TASK1).multiInstance().cardinality("2").multiInstanceDone()
        .endEvent()
        .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
      engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, 10L);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime = engineRule.getHistoricProcessInstance(processInstanceDto.getId())
      .getStartTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = evaluateDurationMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
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

  private void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                       final OffsetDateTime now,
                                       final String userTaskId,
                                       final long offsetDuration) {
    try {
      engineDatabaseRule.changeUserTaskStartDate(
        processInstanceDto.getId(),
        userTaskId,
        now.minus(offsetDuration, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                       final OffsetDateTime now,
                                       final String userTaskKey,
                                       final long offsetDuration) {

    engineRule.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseRule.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  private void finishTwoUserTasksOneWithFirstAndSecondGroup(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineRule.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineRule.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task with
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
      .userTask(USER_TASK1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK1)
      .userTask(USER_TASK2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private void assertCorrectValueOrdering(ProcessDurationReportMapResultDto result) {
    List<MapResultEntryDto<Long>> resultData = result.getData();
    final List<Long> bucketValues = resultData.stream()
      .map(MapResultEntryDto::getValue)
      .collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  private Map<AggregationType, ProcessDurationReportMapResultDto> evaluateDurationMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ProcessDurationReportMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ProcessDurationReportMapResultDto result = evaluateDurationMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private void assertDurationMapReportResults(Map<AggregationType, ProcessDurationReportMapResultDto> results,
                                              Map<String, Long[]> expectedUserTaskValues) {

    aggregationTypes.forEach((AggregationType aggType) -> {
      ProcessDurationReportMapResultDto result = results.get(aggType);
      assertThat(result.getData(), is(notNullValue()));

      expectedUserTaskValues.keySet().forEach((String userTaskKey) -> assertThat(
        result.getDataEntryForKey(userTaskKey).get().getValue(),
        is(calculateExpectedValueGivenDurations(expectedUserTaskValues.get(userTaskKey)).get(aggType))
      ));

    });
  }


}
