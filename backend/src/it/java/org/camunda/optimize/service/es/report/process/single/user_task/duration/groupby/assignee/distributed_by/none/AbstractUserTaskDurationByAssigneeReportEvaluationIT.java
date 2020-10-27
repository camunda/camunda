/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.none;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.Data;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
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
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractUserTaskDurationByAssigneeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  public static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final Double UNASSIGNED_TASK_DURATION = 500.;
  protected static final Double[] SET_DURATIONS = new Double[]{10., 20.};
  private final List<AggregationType> aggregationTypes = AggregationType.getAggregationTypesAsListWithoutSum();

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationForOneProcess() {
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
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime()).isEqualTo(getUserTaskDurationTime());

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration));
    assertThat(result.getEntryForKey(SECOND_USER).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration));

    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
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
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime()).isEqualTo(getUserTaskDurationTime());

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertMap_ForOneProcessWithUnassignedTasks(setDuration, result);
  }

  protected void assertMap_ForOneProcessWithUnassignedTasks(final double setDuration, final ReportMapResultDto result) {
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).get().getValue())
      .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration));
    assertThat(result.getEntryForKey(getLocalisedUnassignedLabel()).get().getValue())
      .withFailMessage(getIncorrectValueForKeyAssertionMsg(getLocalisedUnassignedLabel()))
      .isEqualTo(UNASSIGNED_TASK_DURATION);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertMap_ForSeveralProcesses(result);
  }

  protected void assertMap_ForSeveralProcesses(final ReportMapResultDto result) {
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS)));
    assertThat(result.getEntryForKey(SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));
    assertThat(result.getEntryForKey(getLocalisedUnassignedLabel())).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(getLocalisedUnassignedLabel()))
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

    // when
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertMap_ForSeveralProcessesWithAllAggregationTypes(results);
  }

  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, SET_DURATIONS,
        SECOND_USER, new Double[]{SET_DURATIONS[0]},
        getLocalisedUnassignedLabel(), new Double[]{UNASSIGNED_TASK_DURATION}
      )
    );
    assertThat(results.get(MIN).getInstanceCount()).isEqualTo(2L);
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertMap_ForMultipleEvents(result);
  }

  protected void assertMap_ForMultipleEvents(final ReportMapResultDto result) {
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));
    assertThat(result.getEntryForKey(SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1])));
    assertThat(result.getEntryForKey(getLocalisedUnassignedLabel())).isPresent().get()
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
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertMap_ForMultipleEventsWithAllAggregationTypes(results);
  }

  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, new Double[]{SET_DURATIONS[0]},
        SECOND_USER, new Double[]{SET_DURATIONS[1]},
        getLocalisedUnassignedLabel(), new Double[]{UNASSIGNED_TASK_DURATION}
      )
    );
    assertThat(results.get(MIN).getIsComplete()).isTrue();
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
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

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(1);
    assertThat(resultDto.getIsComplete()).isFalse();
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

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto> resultData = result.getData();
      assertThat(resultData).hasSize(2);
      final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
      // expect ascending order
      assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
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

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = createReport(processDefinition);
      reportData.getConfiguration().setAggregationType(aggType);
      reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

      // then
      assertCustomOrderOnResultValueIsApplied(result);
    });
  }

  protected void assertCustomOrderOnResultValueIsApplied(ReportMapResultDto result) {
    assertThat(result.getData()).hasSize(3);
    assertCorrectValueOrdering(result);
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
    final ReportMapResultDto result1 = reportClient.evaluateMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportMapResultDto result2 = reportClient.evaluateMapReport(reportData2).getResult();

    // then
    assertMap_otherProcessDefinitionsDoNotInfluenceResult(result1, result2);
  }

  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportMapResultDto result1,
                                                                       final ReportMapResultDto result2) {
    assertThat(result1.getData()).hasSize(1);
    assertThat(result1.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " in result 1")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0])));

    assertThat(result2.getData()).hasSize(2);
    assertThat(result2.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " in result 2")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1])));
    assertThat(result2.getEntryForKey(getLocalisedUnassignedLabel())).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(getLocalisedUnassignedLabel()) + " in result 2")
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
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 100L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 300L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 600L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, ImmutableMap.of(DEFAULT_USERNAME, new Double[]{100., 300., 600.}));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isEmpty();
  }

  @Data
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;
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

  protected static Stream<ExecutionStateTestValues> getExecutionStateExpectedValues() {
    ExecutionStateTestValues runningStateValues =
      new ExecutionStateTestValues();
    runningStateValues.executionState = FlowNodeExecutionState.RUNNING;
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200., 200.);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500., 500.);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);

    ExecutionStateTestValues completedStateValues = new ExecutionStateTestValues();
    completedStateValues.executionState = FlowNodeExecutionState.COMPLETED;
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);

    ExecutionStateTestValues canceledStateValues = new ExecutionStateTestValues();
    canceledStateValues.executionState = FlowNodeExecutionState.CANCELED;
    canceledStateValues.expectedIdleDurationValues = getExpectedResultsMap(700., 700.);
    canceledStateValues.expectedWorkDurationValues = getExpectedResultsMap(700., 700.);
    canceledStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);

    ExecutionStateTestValues allStateValues = new ExecutionStateTestValues();
    allStateValues.executionState = FlowNodeExecutionState.ALL;
    allStateValues.expectedIdleDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 200.),
      200.
    );
    allStateValues.expectedWorkDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 500.),
      500.
    );
    allStateValues.expectedTotalDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 700.),
      700.
    );

    return Stream.of(runningStateValues, completedStateValues, canceledStateValues, allStateValues);
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues executionStateTestValues) {
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
    if (FlowNodeExecutionState.CANCELED.equals(executionStateTestValues.executionState)) {
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
    if (FlowNodeExecutionState.CANCELED.equals(executionStateTestValues.executionState)) {
      engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);
      changeDuration(secondInstance, USER_TASK_1, 700L);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(executionStateTestValues.executionState);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertEvaluateReportWithExecutionState(result, executionStateTestValues);
  }

  protected abstract void assertEvaluateReportWithExecutionState(ReportMapResultDto result,
                                                                 ExecutionStateTestValues expectedValues);

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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).get().getValue())
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).get().getValue())
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
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).isEmpty();

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  public static Stream<Arguments> assigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(FilterOperator.IN, new String[]{SECOND_USER}, Lists.newArrayList(Tuple.tuple(SECOND_USER, 10.))),
      Arguments.of(
        FilterOperator.IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Lists.newArrayList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, Lists.newArrayList(Tuple.tuple(DEFAULT_USERNAME, 10.))),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, Lists.newArrayList())
    );
  }

  @ParameterizedTest
  @MethodSource("assigneeFilterScenarios")
  public void filterByAssigneeOnlyIncludesUserTaskWithThatAssignee(final FilterOperator filterOperator,
                                                                   final String[] filterValues,
                                                                   final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10L);
    changeDuration(processInstanceDto, USER_TASK_2, 10L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> candidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        FilterOperator.IN,
        new String[]{SECOND_CANDIDATE_GROUP},
        Lists.newArrayList(Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        FilterOperator.IN,
        new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP},
        Lists.newArrayList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP},
        Lists.newArrayList(Tuple.tuple(DEFAULT_USERNAME, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP}, Lists.newArrayList())
    );
  }

  @ParameterizedTest
  @MethodSource("candidateGroupFilterScenarios")
  public void filterByCandidateGroupOnlyIncludesUserTaskWithThatCandidateGroup(final FilterOperator filterOperator,
                                                                               final String[] filterValues,
                                                                               final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10L);
    changeDuration(processInstanceDto, USER_TASK_2, 10L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
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

  protected void assertCorrectValueOrdering(ReportMapResultDto result) {
    List<MapResultEntryDto> resultData = result.getData();
    final List<Double> bucketValues = resultData.stream()
      .map(MapResultEntryDto::getValue)
      .collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.nullsLast(Comparator.naturalOrder()));
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  protected void assertDurationMapReportResults(Map<AggregationType, ReportMapResultDto> results,
                                                Map<String, Double[]> expectedUserTaskValues) {

    aggregationTypes.forEach((AggregationType aggType) -> {
      ReportMapResultDto result = results.get(aggType);
      assertThat(result.getData()).isNotNull();

      expectedUserTaskValues.keySet()
        .forEach((String userTaskKey) ->
                   assertThat(result.getEntryForKey(userTaskKey).get().getValue())
                     .withFailMessage(getIncorrectValueForKeyAssertionMsg(userTaskKey))
                     .isEqualTo(calculateExpectedValueGivenDurations(expectedUserTaskValues
                                                                       .get(userTaskKey)).get(aggType)));
    });
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  protected String getIncorrectValueForKeyAssertionMsg(final String key) {
    return String.format("Incorrect value for key [%s]", key);
  }
}
