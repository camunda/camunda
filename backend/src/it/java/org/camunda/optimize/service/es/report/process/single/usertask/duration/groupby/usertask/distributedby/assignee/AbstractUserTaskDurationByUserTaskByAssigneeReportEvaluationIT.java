/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.usertask.distributedby.assignee;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Triple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_PROCESS_ID;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.VERSION_TAG;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected static final String SECOND_USER = "secondUser";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_A = "userTaskA";
  protected static final String USER_TASK_B = "userTaskB";
  protected static final Double UNASSIGNED_TASK_DURATION = 500.;
  protected static final Double[] SET_DURATIONS = new Double[]{10., 20.};

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
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);

    final Double setDuration = 20.;
    changeDuration(processInstanceDto, setDuration);
    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    doReportDataAssertions(resultReportDataDto, processDefinition);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, 20., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 20., SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_A)
          .distributedByContains(DEFAULT_USERNAME, 20., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_B)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 20., SECOND_USER_FULL_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstance_whenAssigneeCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    changeDuration(processInstanceDto, 1.);
    importAndRefresh();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCache().resetCache();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, 1., DEFAULT_USERNAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto);

    changeDuration(processInstanceDto, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);
    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    doReportDataAssertions(resultReportDataDto, processDefinition);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertHyperMap_forOneProcessInstanceWithUnassignedTasks(actualResult);
  }

  protected void assertHyperMap_forOneProcessInstanceWithUnassignedTasks(final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]), DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]), DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessInstancesWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);

    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(result);
  }

  protected void assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
        hyperMapAsserter
          .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
            .groupByContains(USER_TASK_1)
              .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
              .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_2)
              .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
              .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_A)
              .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
              .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_B)
              .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
              .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
            .add()
          .add();
        // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    final Double expectedDuration = 20.;
    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId()
    );
    changeDuration(processInstanceDto1, expectedDuration);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto2.getId()
    );
    changeDuration(processInstanceDto2, expectedDuration);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, 20., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 20., SECOND_USER_FULL_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithSecondAndDefault(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[0]);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_2)
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT_ID)
      .userTask(USER_TASK_1).name("thisLabelComesSecond")
      .userTask(USER_TASK_2).name("thisLabelComesFirst")
      .endEvent(END_EVENT)
      .done();

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(bpmnModelInstance);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithSecondAndDefault(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[0]);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1, "thisLabelComesSecond")
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
          .groupByContains(USER_TASK_2, "thisLabelComesFirst")
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getDoubleUserTaskDiagram());
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurationsDefaultAggr(40.), SECOND_USER_FULL_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getDoubleUserTaskDiagram());
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurationsDefaultAggr(40.), SECOND_USER_FULL_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getDoubleUserTaskDiagram());
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), DEFAULT_FULLNAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getDoubleUserTaskDiagram());
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), DEFAULT_FULLNAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    final Double[] setDurations1 = new Double[]{40., 20.};
    changeDuration(processInstanceDto1, setDurations1[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations1[1]);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto3);
    final Double[] setDurations2 = new Double[]{60., 80.};
    changeDuration(processInstanceDto3, setDurations2[0]);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    changeUserTaskStartDate(processInstanceDto4, now, USER_TASK_1, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult1 = reportClient.evaluateHyperMapReport(
      reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult2 = reportClient.evaluateHyperMapReport(
      reportData2).getResult();

    // then
    assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(
      setDurations1,
      setDurations2,
      actualResult1,
      actualResult2
    );
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>> result1,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>> result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1), DEFAULT_FULLNAME)
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]), DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
      .doAssert(result2);
    // @formatter:on
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Collections.singletonList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      Arrays.asList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    Double[] setDurations = new Double[]{100., 300., 600.};
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[2]);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(setDurations).get(aggType), DEFAULT_FULLNAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondUserTriple(10.)))
          .build()

      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createDefaultUserTriple(10.), createSecondUserTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createDefaultUserTriple(null), createSecondUserTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.singletonList(createDefaultUserTriple(10.))).build()
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyMap())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesUserTaskWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(1)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createDefaultUserTriple(10.), createSecondUserTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createDefaultUserTriple(null), createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createDefaultUserTriple(15.), createSecondUserTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createDefaultUserTriple(30.), createSecondUserTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createDefaultUserTriple(15.), createSecondUserTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createDefaultUserTriple(30.), createSecondUserTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.emptyList())
          .put(USER_TASK_2, Collections.emptyList())
          .build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  public void instanceLevelFilterByAssigneeOnlyIncludesUserTaskFromInstancesWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createDefaultUserTriple(10.), createSecondUserTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createDefaultUserTriple(null), createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.singletonList(createDefaultUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyMap()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesUserTaskWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId());

    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(1)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createDefaultUserTriple(10.), createSecondUserTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createDefaultUserTriple(null), createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createDefaultUserTriple(15.), createSecondUserTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createDefaultUserTriple(30.), createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createDefaultUserTriple(15.), createSecondUserTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createDefaultUserTriple(30.), createSecondUserTriple(10.)))
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 0L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.emptyList())
          .put(USER_TASK_2, Collections.emptyList())
          .build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  public void instanceLevelFilterByCandidateGroupOnlyIncludesUserTaskWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  private static Stream<Arguments> flowNodeStatusFilter() {
    return Stream.of(
      Arguments.of(ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(), 2, 2L),
      Arguments.of(ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(), 1, 1L),
      Arguments.of(ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(), 1, 1L)
    );
  }

  @ParameterizedTest
  @MethodSource("flowNodeStatusFilter")
  public void evaluateReportWithFlowNodeStatusFilter(final List<ProcessFilterDto<?>> processFilter,
                                                     final Integer expectedDataSize,
                                                     final long expectedInstanceCount) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        getDoubleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 100.);
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 700.);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK_2, 500.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto2.getId()
    );
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700.);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(processFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).hasSize(expectedDataSize);
    assertEvaluateReportWithFlowNodeStatusFilter(actualResult, processFilter, expectedInstanceCount);
  }

  protected abstract void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
                                                                       final List<ProcessFilterDto<?>> filter,
                                                                       final long expectedInstanceCount);

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final Double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private void doReportDataAssertions(final ProcessReportDataDto resultReportDataDto,
                                      final ProcessDefinitionEngineDto processDefinition) {
    assertThat(resultReportDataDto.getProcessDefinitionKey())
      .withFailMessage("ProcessDefinitionKey does not match expected key.")
      .isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions())
      .withFailMessage("ProcessDefinition versions do not match expected versions.")
      .contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView())
      .withFailMessage("View should not be null.")
      .isNotNull();
    assertThat(resultReportDataDto.getView().getEntity())
      .withFailMessage("View should be USER_TASKS.")
      .isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty())
      .withFailMessage("View property should be DURATION.")
      .isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTimes())
      .withFailMessage("UserTaskDurationTime in report configuration does not match expected userTaskDurationTime")
      .containsExactly(getUserTaskDurationTime());
    assertThat(resultReportDataDto.getDistributedBy().getType())
      .withFailMessage("Distributed by should be ASSIGNEE.")
      .isEqualTo(DistributedByType.ASSIGNEE);
  }

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, Collections.singletonList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishUserTaskRoundsOneWithDefaultAndSecondUser(final ProcessInstanceEngineDto processInstanceDto) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
    // finish second task with
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private void finishUserTaskRoundsOneWithSecondAndDefault(final ProcessInstanceEngineDto processInstanceDto) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto.getId()
    );
    // finish second task with
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private void finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
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
        final ProcessDefinitionEngineDto processDefinitionEngineDto =
          engineIntegrationExtension.deployProcessAndGetProcessDefinition(
            getSingleUserTaskDiagram(processKey),
            tenant
          );
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleUserTaskDiagram(DEFAULT_PROCESS_ID),
      null
    );
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(DEFAULT_PROCESS_ID)
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
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  protected String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  private void importAndRefresh() {
    importAllEngineEntitiesFromScratch();
  }
}
