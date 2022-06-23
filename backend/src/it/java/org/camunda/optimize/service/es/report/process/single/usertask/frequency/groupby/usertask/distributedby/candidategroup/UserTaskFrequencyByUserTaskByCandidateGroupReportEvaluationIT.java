/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.usertask.distributedby.candidategroup;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Triple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
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
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.VERSION_TAG;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class UserTaskFrequencyByUserTaskByCandidateGroupReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";

  @BeforeEach
  public void init() {
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
  }

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);

    importAndRefresh();

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
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_A)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstance_whenCandidateGroupCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

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
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_ID)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndLeaveTask2BUnassigned(processInstanceDto);

    importAndRefresh();

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
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessInstances() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndLeaveTask2BUnassigned(processInstanceDto2);

    importAndRefresh();

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
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 2., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 2., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
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
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto1.getId(), FIRST_CANDIDATE_GROUP_ID);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto2.getId(), SECOND_CANDIDATE_GROUP_ID);
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
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 and 2 of instance 1 with first candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 of instance 2 with second candidate group and leave task 2 of instance 2 unassigned
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());

    importAndRefresh();

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
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(actualResult);
    // @formatter:on
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

    final ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(bpmnModelInstance);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish tasks 1 and 2 of instance 1 with first candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish tasks 1 and 2 of instance 2 with second candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());

    importAndRefresh();

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
        .groupByContains(USER_TASK_1, "thisLabelComesSecond")
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2, "thisLabelComesFirst")
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 1., SECOND_CANDIDATE_GROUP_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    final ProcessDefinitionEngineDto processDefinition2 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto3.getId());

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
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 2., FIRST_CANDIDATE_GROUP_NAME)
      .doAssert(actualResult1);

    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
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
    assertThat(actualResult.getInstanceCount()).isEqualTo(selectedTenants.size());
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
    assertThat(actualResult.getFirstMeasureData()).hasSize(0);
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyCountsUserTaskWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

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
      .measure(ViewProperty.FREQUENCY);
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

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  public void instanceLevelFilterByAssigneeOnlyCountsUserTaskFromInstancesWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

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
      .measure(ViewProperty.FREQUENCY);
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

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyCountsUserTaskWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

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
      .measure(ViewProperty.FREQUENCY);
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount ->
          groupByAdder.distributedByContains(
            candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
          )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  public void instanceLevelFilterByCandidateGroupOnlyCountsUserTaskFromInstancesWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

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
      .measure(ViewProperty.FREQUENCY);
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

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondGroupTriple(1.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.singletonList(createFirstGroupTriple(1.)))
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyMap())
    );
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(1.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(2.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(2.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(1.))
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

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondGroupTriple(1.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.singletonList(createFirstGroupTriple(1.)))
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

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(null)))
          .put(USER_TASK_2, Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(1.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(2.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(2.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(1.), createSecondGroupTriple(1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.emptyList())
          .put(USER_TASK_2, Collections.emptyList())
          .build()
      )
    );
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP)
      .build();
  }

  private void finishUserTask1AWithFirstAndTaskB2WithSecondGroup(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish user task 2 and B with second user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  private void finishUserTask1AWithFirstAndLeaveTask2BUnassigned(final ProcessInstanceEngineDto processInstanceEngineDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(
          processKey,
          tenant
        );
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition(PROCESS_DEFINITION_KEY, null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram(
      PROCESS_DEFINITION_KEY));
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
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

  private void importAndRefresh() {
    importAllEngineEntitiesFromScratch();
  }

}

