/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.assignee.distributed_by.user_task;

import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractUserTaskDurationByAssigneeByUserTaskReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_1_NAME = "userTask1Name";
  private static final String USER_TASK_2_NAME = "userTask2Name";
  private static final String USER_TASK_2 = "userTask2";
  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";
  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy(), is(DistributedBy.USER_TASK));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 20L)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, 20L)
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, 20L)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, 20L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 20L)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, 20L)
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, 20L)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, 20L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurations(setDurations).get(aggType))
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurations(setDurations).get(aggType))
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(setDurations).get(aggType))
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, calculateExpectedValueGivenDurations(setDurations).get(aggType))
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 20L, USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    final Long[] setDurations = new Long[]{10L, 20L};
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, setDurations[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, setDurations[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(
          USER_TASK_2,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_2_NAME
        )
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .isComplete(false)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    final Long[] setDurations = new Long[]{10L, 20L};
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, setDurations[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, setDurations[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
      .groupByContains(SECOND_USER)
        .distributedByContains(
          USER_TASK_2,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_2_NAME
        )
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    final Long[] setDurations = new Long[]{10L, 20L};
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, setDurations[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, setDurations[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
      .groupByContains(SECOND_USER)
        .distributedByContains(
          USER_TASK_2,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_2_NAME
        )
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    final Long[] setDurations = new Long[]{10L, 20L};
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId());
    changeDuration(processInstanceDto1, setDurations[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto2.getId());
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40L), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40L), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L), USER_TASK_1_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L), USER_TASK_1_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 20L);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto3);
    changeDuration(processInstanceDto3, 60L);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto4);
    changeDuration(processInstanceDto4, 80L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportHyperMapResultDto actualResult1 = evaluateHyperMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportHyperMapResultDto actualResult2 = evaluateHyperMapReport(reportData2).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(40L, 20L), USER_TASK_1_NAME)
      .doAssert(actualResult1);
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(60L, 80L), USER_TASK_1_NAME)
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    Long[] setDurations = new Long[]{100L, 300L, 600L};
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[2]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1, calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @Data
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;

    HyperMapResultEntryDto expectedIdleDurationValues;
    HyperMapResultEntryDto expectedWorkDurationValues;
    HyperMapResultEntryDto expectedTotalDurationValues;
  }

  private static HyperMapResultEntryDto getExpectedResultsMap(Long userTask1Result, Long userTask2Result) {
    List<MapResultEntryDto> groupByResults = new ArrayList<>();
    MapResultEntryDto firstUserTask = new MapResultEntryDto(USER_TASK_1, userTask1Result, USER_TASK_1_NAME);
    groupByResults.add(firstUserTask);
    MapResultEntryDto secondUserTask = new MapResultEntryDto(USER_TASK_2, userTask2Result, USER_TASK_2_NAME);
    groupByResults.add(secondUserTask);
    return new HyperMapResultEntryDto(DEFAULT_USERNAME, groupByResults);
  }

  protected static Stream<ExecutionStateTestValues> getExecutionStateExpectedValues() {
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
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 200L),
      200L
    );
    allStateValues.expectedWorkDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 500L),
      500L
    );
    allStateValues.expectedTotalDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 700L),
      700L
    );

    return Stream.of(runningStateValues, completedStateValues, allStateValues);
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues executionStateTestValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100L);
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 700L);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK_2, 500L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto2.getId());
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700L);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(executionStateTestValues.executionState);
    final ReportHyperMapResultDto actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(1));
    assertEvaluateReportWithExecutionState(actualResult, executionStateTestValues);
  }

  protected abstract void assertEvaluateReportWithExecutionState(ReportHyperMapResultDto result,
                                                                 ExecutionStateTestValues expectedValues);
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

    final ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, i);
    }

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 5L, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime = engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
      .getStartTime();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(0));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(1));
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
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
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
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
      engineDatabaseExtension.changeUserTaskStartDate(
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

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
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


  private void finishUserTaskRoundsOneWithDefaultAndSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId());
    // finish second task with
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto1.getId());
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
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
        .name(USER_TASK_1_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
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

  private Map<AggregationType, ReportHyperMapResultDto> evaluateHypeMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportHyperMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

}
