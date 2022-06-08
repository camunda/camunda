/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.assignee.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;

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
      .setReportDataType(USER_TASK_DUR_GROUP_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<MapResultEntryDto>> result,
                                                              final FlowNodeStateTestValues expectedValues) {
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedTotalDurationValues().get(DEFAULT_USERNAME));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedTotalDurationValues().get(SECOND_USER));
  }

}
