/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.user_task.distributed_by.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.sql.SQLException;
import java.util.List;

import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP;

public class UserTaskTotalDurationByUserTaskByCandidateGroupReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), userTaskKey, duration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), setDuration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP)
      .build();
  }


  @Override
  protected void assertEvaluateReportWithExecutionState(final ReportHyperMapResultDto result,
                                                        final FlowNodeExecutionState executionState) {
    switch (executionState) {
      case RUNNING:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 700L)
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 700L)
          .doAssert(result);
        // @formatter:on
        break;
      case COMPLETED:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 100L)
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, null)
          .doAssert(result);
        // @formatter:on
        break;
      case ALL:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(100L, 700L))
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 700L)
          .doAssert(result);
        // @formatter:on
        break;
    }
  }
}
