/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.assignee.distributedby.none;

import com.google.common.collect.ImmutableMap;
import lombok.Data;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultAsserter;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractUserTaskDurationByAssigneeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final Double UNASSIGNED_TASK_DURATION = 500.;
  protected static final Double[] SET_DURATIONS = new Double[]{10., 20.};

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);

    final double setDuration = 20;
    changeDuration(processInstanceDto, setDuration);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTimes())
      .containsExactly(getUserTaskDurationTime());

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue, MapResultEntryDto::getLabel)
      .containsExactly(calculateExpectedValueGivenDurationsDefaultAggr(setDuration), DEFAULT_FULLNAME);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)).isPresent().get()
      .extracting(MapResultEntryDto::getValue, MapResultEntryDto::getLabel)
      .containsExactly(calculateExpectedValueGivenDurationsDefaultAggr(setDuration), SECOND_USER_FULL_NAME);

    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void reportEvaluationForOneProcessInstance_whenAssigneeCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    changeDuration(processInstanceDto, 1.);
    importAllEngineEntitiesFromScratch();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCache().resetCache();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue, MapResultEntryDto::getLabel)
      .containsExactly(calculateExpectedValueGivenDurationsDefaultAggr(1.), DEFAULT_USERNAME);
  }

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, USER_TASK_1, setDuration);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTimes())
      .containsExactly(getUserTaskDurationTime());

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertMap_ForOneProcessInstanceWithUnassignedTasks(setDuration, result);
  }

  protected void assertMap_ForOneProcessInstanceWithUnassignedTasks(final double setDuration,
                                                                    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration));
    assertThat(MapResultUtil.getEntryForKey(
      result.getFirstMeasureData(),
      DISTRIBUTE_BY_IDENTITY_MISSING_KEY
    )).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .withFailMessage(getIncorrectValueForKeyAssertionMsg(DISTRIBUTE_BY_IDENTITY_MISSING_KEY))
      .isEqualTo(UNASSIGNED_TASK_DURATION);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";

    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId()
    );
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto2.getId()
    );
    changeDuration(processInstanceDto2, SET_DURATIONS[1]);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupedByContains(
          DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]), DEFAULT_FULLNAME
        )
        .groupedByContains(
          SECOND_USER, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]), SECOND_USER_FULL_NAME
        )
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessInstances() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertMap_forSeveralProcessInstances(result);
  }

  protected void assertMap_forSeveralProcessInstances(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS)));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));
    assertThat(MapResultUtil.getEntryForKey(
      result.getFirstMeasureData(),
      DISTRIBUTE_BY_IDENTITY_MISSING_KEY
    )).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DISTRIBUTE_BY_IDENTITY_MISSING_KEY))
        .isEqualTo(UNASSIGNED_TASK_DURATION));
    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertMap_ForSeveralProcessesWithAllAggregationTypes(result);
  }

  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertDurationMapReportResultsForAllAggregationTypes(
      result,
      ImmutableMap.of(
        DEFAULT_USERNAME, SET_DURATIONS,
        SECOND_USER, new Double[]{SET_DURATIONS[0]},
        DISTRIBUTE_BY_IDENTITY_MISSING_KEY, new Double[]{UNASSIGNED_TASK_DURATION}
      )
    );
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[1]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertMap_ForMultipleEvents(result);
  }

  protected void assertMap_ForMultipleEvents(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1])));
    assertThat(MapResultUtil.getEntryForKey(
      result.getFirstMeasureData(),
      DISTRIBUTE_BY_IDENTITY_MISSING_KEY
    )).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .isEqualTo(UNASSIGNED_TASK_DURATION));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[1]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertMap_ForMultipleEventsWithAllAggregationTypes(result);
  }

  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertDurationMapReportResultsForAllAggregationTypes(
      result,
      ImmutableMap.of(
        DEFAULT_USERNAME, new Double[]{SET_DURATIONS[0], SET_DURATIONS[0]},
        SECOND_USER, new Double[]{SET_DURATIONS[1]},
        DISTRIBUTE_BY_IDENTITY_MISSING_KEY, new Double[]{UNASSIGNED_TASK_DURATION}
      )
    );
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      assertThat(measureData)
        .hasSize(2)
        .extracting(MapResultEntryDto::getKey)
        .isSortedAccordingTo(Comparator.reverseOrder());
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(2);
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    // expect ascending order
    assertThat(resultLabels).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 100L);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(getSupportedAggregationTypes()));
    result.getMeasures().forEach(measureResult -> {
      assertThat(measureResult.getData())
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
    });
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
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, SET_DURATIONS[0]);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto3);
    changeDuration(processInstanceDto3, SET_DURATIONS[1]);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    changeUserTaskStartDate(processInstanceDto4, now, USER_TASK_1, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = reportClient.evaluateMapReport(reportData1)
      .getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = reportClient.evaluateMapReport(reportData2)
      .getResult();

    // then
    assertMap_otherProcessDefinitionsDoNotInfluenceResult(result1, result2);
  }

  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportResultResponseDto<List<MapResultEntryDto>> result1,
                                                                       final ReportResultResponseDto<List<MapResultEntryDto>> result2) {
    assertThat(result1.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " in result 1")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));

    assertThat(result2.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result2.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " in result 2")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1])));
    assertThat(MapResultUtil.getEntryForKey(
      result2.getFirstMeasureData(),
      DISTRIBUTE_BY_IDENTITY_MISSING_KEY
    )).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DISTRIBUTE_BY_IDENTITY_MISSING_KEY) + " in result 2")
        .isEqualTo(UNASSIGNED_TASK_DURATION));
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
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final Double[] expectedDurations = {100., 300., 600.};
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, expectedDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, expectedDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, expectedDurations[2]);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertDurationMapReportResultsForAllAggregationTypes(
      result, ImmutableMap.of(DEFAULT_USERNAME, expectedDurations)
    );
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Data
  static class FlowNodeStateTestValues {
    List<ProcessFilterDto<?>> processFilter;
    Map<String, Double> expectedIdleDurationValues;
    Map<String, Double> expectedWorkDurationValues;
    Map<String, Double> expectedTotalDurationValues;
  }

  private static Map<String, Double> getExpectedResultsMap(Double userTask1Results, Double userTask2Results) {
    Map<String, Double> result = new HashMap<>();
    if (nonNull(userTask1Results)) {
      result.put(DEFAULT_USERNAME, userTask1Results);
    }
    if (nonNull(userTask2Results)) {
      result.put(SECOND_USER, userTask2Results);
    }
    return result;
  }

  protected static Stream<FlowNodeStateTestValues> getFlowNodeStatusFilterValues() {
    FlowNodeStateTestValues runningStateValues =
      new FlowNodeStateTestValues();
    runningStateValues.processFilter = ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList();
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200., 200.);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500., 500.);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);

    FlowNodeStateTestValues completedStateValues =
      new FlowNodeStateTestValues();
    completedStateValues.processFilter = ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList();
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);

    FlowNodeStateTestValues completedOrCanceledStateValues = new FlowNodeStateTestValues();
    completedOrCanceledStateValues.processFilter = ProcessFilterBuilder.filter()
      .completedOrCanceledFlowNodesOnly().add().buildList();
    completedOrCanceledStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedOrCanceledStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedOrCanceledStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);

    FlowNodeStateTestValues canceledStateValues = new FlowNodeStateTestValues();
    canceledStateValues.processFilter = ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList();
    canceledStateValues.expectedIdleDurationValues = getExpectedResultsMap(700., 700.);
    canceledStateValues.expectedWorkDurationValues = getExpectedResultsMap(700., 700.);
    canceledStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);

    return Stream.of(runningStateValues, completedStateValues, completedOrCanceledStateValues, canceledStateValues);
  }

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusFilterValues")
  public void evaluateReportWithFlowNodeStatus(FlowNodeStateTestValues flowNodeStatusFilterValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
    changeDuration(firstInstance, USER_TASK_1, 100L);
    engineIntegrationExtension.claimAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    changeUserTaskStartDate(firstInstance, now, USER_TASK_2, 700.);
    changeUserTaskClaimDate(firstInstance, now, USER_TASK_2, 500.);
    if (isSingleFilterOfType(flowNodeStatusFilterValues.processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
      engineIntegrationExtension.cancelActivityInstance(firstInstance.getId(), USER_TASK_2);
      changeDuration(firstInstance, USER_TASK_2, 700L);
    }

    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      secondInstance.getId()
    );

    changeUserTaskStartDate(secondInstance, now, USER_TASK_1, 700.);
    changeUserTaskClaimDate(secondInstance, now, USER_TASK_1, 500.);
    if (isSingleFilterOfType(flowNodeStatusFilterValues.processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
      engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);
      changeDuration(secondInstance, USER_TASK_1, 700L);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(flowNodeStatusFilterValues.processFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertEvaluateReportWithFlowNodeStatusFilter(result, flowNodeStatusFilterValues);
  }

  protected abstract void assertEvaluateReportWithFlowNodeStatusFilter(ReportResultResponseDto<List<MapResultEntryDto>> result,
                                                                       FlowNodeStateTestValues expectedValues);

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(DEFAULT_USERNAME).multiInstance().cardinality("2").multiInstanceDone()
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
    changeDuration(processInstanceDto, 10L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
        processDefinition.getId());
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, 10L);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
        .getStartTime();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        Collections.singletonList(Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, Collections.singletonList(Tuple.tuple(DEFAULT_USERNAME, 10.))),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesUserTaskWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                            final String[] filterValues,
                                                                            final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
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
    changeDuration(processInstanceDto, USER_TASK_1, 10L);
    changeDuration(processInstanceDto, USER_TASK_2, 10L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  public void instanceLevelFilterByAssigneeOnlyConsidersInstancesWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                                  final String[] filterValues,
                                                                                  final Long expectedInstanceCount,
                                                                                  final List<Tuple> expectedResultData) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
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

    changeDuration(firstInstance, USER_TASK_1, 10L);
    changeDuration(firstInstance, USER_TASK_2, 10L);
    changeDuration(secondInstance, USER_TASK_1, 20L);
    changeDuration(secondInstance, USER_TASK_2, 30L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResultData);
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(Tuple.tuple(SECOND_USER, 20.))
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 20.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(Tuple.tuple(DEFAULT_USERNAME, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesUserTaskWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                        final String[] filterValues,
                                                                                        final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10L);
    changeDuration(processInstanceDto, USER_TASK_2, 20L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyList()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  public void instanceLevelFilterByCandidateGroupIncludesAllTasksForInstancesWithMatchingCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                                        final String[] filterValues,
                                                                                                        final Long expectedInstanceCount,
                                                                                                        final List<Tuple> expectedResultData) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
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

    changeDuration(firstInstance, USER_TASK_1, 10L);
    changeDuration(firstInstance, USER_TASK_2, 10L);
    changeDuration(secondInstance, USER_TASK_1, 20L);
    changeDuration(secondInstance, USER_TASK_2, 30L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResultData);
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

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final Number durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Number durationInMs);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishTwoUserTasksOneWithDefaultAndSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    // finish second task with
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto1.getId()
    );
  }

  private void finishUserTaskOneWithDefaultAndLeaveOneUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
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
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  protected void assertDurationMapReportResultsForAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result,
    final Map<String, Double[]> expectedUserTaskValues) {
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      expectedUserTaskValues.keySet().forEach(userTaskKey -> {
        assertThat(MapResultUtil.getEntryForKey(measureData, userTaskKey))
          .isPresent().get()
          .extracting(MapResultEntryDto::getValue)
          .withFailMessage(getIncorrectValueForKeyAssertionMsg(userTaskKey))
          .isEqualTo(
            calculateExpectedValueGivenDurations(expectedUserTaskValues.get(userTaskKey))
              .get(measureResult.getAggregationType())
          );
      });
    });
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  protected String getIncorrectValueForKeyAssertionMsg(final String key) {
    return String.format("Incorrect value for key [%s]", key);
  }
}
