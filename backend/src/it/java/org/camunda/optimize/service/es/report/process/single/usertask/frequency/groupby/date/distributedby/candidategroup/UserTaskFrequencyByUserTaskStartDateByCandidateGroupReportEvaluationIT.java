/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.date.distributedby.candidategroup;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;

public class UserTaskFrequencyByUserTaskStartDateByCandidateGroupReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateByCandidateGroupReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDayDateAsString(referenceDate))
      .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
      .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
      .doAssert(result);
  }

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusFilterExpectedValues")
  public void evaluateReportWithFlowNodeStatusFilter(final List<ProcessFilterDto<?>> processFilter,
                                                     final Double candidateGroup1Count,
                                                     final Double candidateGroup2Count,
                                                     final Long expectedInstanceCount) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksWithDifferentCandidateGroups();

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(processFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final HyperMapAsserter.GroupByAdder groupByAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()));
    if (candidateGroup1Count != null) {
      groupByAsserter.distributedByContains(FIRST_CANDIDATE_GROUP_ID, candidateGroup1Count, FIRST_CANDIDATE_GROUP_NAME);
    }
    if (candidateGroup2Count != null) {
      groupByAsserter.distributedByContains(
        SECOND_CANDIDATE_GROUP_ID, candidateGroup2Count, SECOND_CANDIDATE_GROUP_NAME
      );
    }
    groupByAsserter.doAssert(result);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusFilterCanceled() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishTwoUserTasksWithDifferentCandidateGroups();

    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
      .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_NAME)
      .doAssert(result);
  }

  protected static Stream<Arguments> getFlowNodeStatusFilterExpectedValues() {
    return Stream.of(
      Arguments.of(ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(), 1., null, 1L),
      Arguments.of(ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(), 1., 1., 1L),
      Arguments.of(ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(), 1., 1., 1L)
    );
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeUserTaskDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeAllFlowNodeStartDates(updates);
  }

  @Override
  protected void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                    final String userTaskKey,
                                    final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeFlowNodeStartDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }
}
