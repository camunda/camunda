/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.duration.distributedby.flownode;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
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
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationByModelElementIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;

public class FlowNodeFrequencyByFlowNodeDurationByFlowNodeIT
  extends ModelElementFrequencyByModelElementDurationByModelElementIT {

  private static final ImmutableList<String> FLOW_NODES = ImmutableList.of(END_EVENT, START_EVENT, USER_TASK_1);
  private static final ImmutableList<String> FLOW_NODES_2 = ImmutableList.of(
    END_EVENT + 2, START_EVENT + 2, USER_TASK_2
  );

  @Override
  protected ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
    final String definitionId,
    final Number durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), durationInMillis);
    return processInstance;
  }

  @Override
  protected ProcessViewEntity getProcessViewEntity() {
    return ProcessViewEntity.FLOW_NODE;
  }

  @Override
  protected DistributedByType getDistributedByType() {
    return DistributedByType.FLOW_NODE;
  }

  @Override
  protected ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE)
      .build();
  }

  @Override
  protected List<String> getExpectedModelElements() {
    return FLOW_NODES;
  }

  @Override
  protected List<String> getSecondProcessExpectedModelElements() {
    return FLOW_NODES_2;
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedActivityInstanceDurations = 1000;
    final OffsetDateTime startTime = DateCreationFreezer.dateFreezer(OffsetDateTime.now()).freezeDateAndReturn();
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), completedActivityInstanceDurations);

    final ProcessInstanceEngineDto runningProcessInstance =
      engineIntegrationExtension.startProcessInstance(definition.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(
      runningProcessInstance.getId(), completedActivityInstanceDurations
    );
    engineDatabaseExtension.changeFlowNodeStartDate(runningProcessInstance.getId(), USER_TASK_1, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = DateCreationFreezer
      // just one more ms to ensure we only get back two buckets for easier assertion
      .dateFreezer(startTime.plus(completedActivityInstanceDurations + 1, ChronoUnit.MILLIS))
      .freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(createDurationBucketKey(completedActivityInstanceDurations))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
        .groupByContains(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()))
        .distributedByContains(END_EVENT, null, END_EVENT)
        .distributedByContains(START_EVENT, null, START_EVENT)
        .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 5000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 10000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final List<ProcessFilterDto<?>> filterYieldingNoResults = ProcessFilterBuilder.filter()
      .flowNodeDuration()
      .flowNode(USER_TASK_1, durationFilterData(DurationUnit.SECONDS, 10L, LESS_THAN))
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    reportData.setFilter(filterYieldingNoResults);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(MapResultUtil.getDataEntryForKey(
      result.getFirstMeasureData(),
      createDurationBucketKey(1000)
    )).isPresent();
    assertThat(MapResultUtil.getDataEntryForKey(
      result.getFirstMeasureData(),
      createDurationBucketKey(5000)
    )).isPresent();
    assertThat(MapResultUtil.getDataEntryForKey(
      result.getFirstMeasureData(),
      createDurationBucketKey(10000)
    )).isNotPresent();
    assertThat(result.getFirstMeasureData()).allSatisfy(bucket -> {
      if (bucket.getKey().equals(createDurationBucketKey(1000)) ||
        bucket.getKey().equals(createDurationBucketKey(5000))) {
        assertThat(bucket.getValue())
          .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue).hasSize(3)
          .contains(Tuple.tuple(START_EVENT, null), Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(END_EVENT, null));
      } else {
        assertThat(bucket.getValue())
          .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue).hasSize(3)
          .contains(Tuple.tuple(START_EVENT, null), Tuple.tuple(USER_TASK_1, null), Tuple.tuple(END_EVENT, null));
      }
    });
  }

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        Map.of(USER_TASK_2, 1.)
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        Map.of(USER_TASK_1, 1., USER_TASK_2, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        Map.of(USER_TASK_1, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Map.of(USER_TASK_3, 1.)
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                           final String[] filterValues,
                                                                           final Map<String, Double> expectedResults) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstanceDto.getId(), 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition.getKey(), ALL_VERSIONS);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    // set sorting to allow asserting in the same order for all scenarios
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains("10.0")
        .distributedByContains(USER_TASK_1, expectedResults.getOrDefault(USER_TASK_1, null))
        .distributedByContains(USER_TASK_2, expectedResults.getOrDefault(USER_TASK_2, null))
        .distributedByContains(USER_TASK_3, expectedResults.getOrDefault(USER_TASK_3, null))
      .doAssert(result);
    // @formatter:on
  }


  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_2, 1.)
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        Map.of(USER_TASK_1, 1., USER_TASK_2, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_1, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_3, 1.)
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                                 final String[] filterValues,
                                                                                 final Map<String, Double> expectedResults) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstanceDto.getId(), 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition.getKey(), ALL_VERSIONS);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    // set sorting to allow asserting in the same order for all scenarios
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains("10.0")
        .distributedByContains(USER_TASK_1, expectedResults.getOrDefault(USER_TASK_1, null))
        .distributedByContains(USER_TASK_2, expectedResults.getOrDefault(USER_TASK_2, null))
        .distributedByContains(USER_TASK_3, expectedResults.getOrDefault(USER_TASK_3, null))
      .doAssert(result);
    // @formatter:on
  }

}
