/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.none;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE;

public class UserTaskTotalDurationByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Number durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Number durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ReportMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedTotalDurationValues().get(DEFAULT_USERNAME));
    assertThat(result.getEntryForKey(SECOND_USER).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedTotalDurationValues().get(SECOND_USER));
  }

}
