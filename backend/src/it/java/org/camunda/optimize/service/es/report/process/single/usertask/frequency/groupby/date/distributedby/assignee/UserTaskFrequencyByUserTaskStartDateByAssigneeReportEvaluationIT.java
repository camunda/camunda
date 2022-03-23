/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.date.distributedby.assignee;

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
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class UserTaskFrequencyByUserTaskStartDateByAssigneeReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateByAssigneeReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate))
          .distributedByContains(DEFAULT_USERNAME, 1., DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, 1., getLocalisedUnassignedLabel())
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusFilterExpectedValues")
  public void evaluateReportWithFlowNodeStatusFilter(final List<ProcessFilterDto<?>> processFilter,
                                                     final Double assignee1Count,
                                                     final Double assignee2Count,
                                                     final Long expectedInstanceCount) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.claimAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto2.getId()
    );

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
    if (assignee1Count != null) {
      groupByAsserter.distributedByContains(DEFAULT_USERNAME, assignee1Count, DEFAULT_FULLNAME);
    }
    if (assignee2Count != null) {
      groupByAsserter.distributedByContains(SECOND_USER, assignee2Count, SECOND_USER_FULL_NAME);
    }
    groupByAsserter.doAssert(result);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusFilterCanceled() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.claimAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto2.getId()
    );
    engineIntegrationExtension.cancelActivityInstance(processInstanceDto2.getId(), USER_TASK_1);

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
      .distributedByContains(DEFAULT_USERNAME, 1., DEFAULT_FULLNAME)
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
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE;
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
