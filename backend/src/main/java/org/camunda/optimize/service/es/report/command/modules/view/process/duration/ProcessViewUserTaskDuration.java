/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Primary
public class ProcessViewUserTaskDuration extends ProcessViewDuration {

  private static ImmutableMap<UserTaskDurationTime, UserTaskDurationTimeSpecifics> userTaskDurationTimeTypeMap =
    ImmutableMap.of(
      UserTaskDurationTime.IDLE, new UserTaskDurationTimeSpecifics(USER_TASK_IDLE_DURATION, USER_TASK_START_DATE),
      UserTaskDurationTime.WORK, new UserTaskDurationTimeSpecifics(USER_TASK_WORK_DURATION, USER_TASK_CLAIM_DATE),
      UserTaskDurationTime.TOTAL, new UserTaskDurationTimeSpecifics(USER_TASK_TOTAL_DURATION, USER_TASK_START_DATE)
    );

  @AllArgsConstructor
  @Getter
  private static class UserTaskDurationTimeSpecifics {
    private String durationFieldName;
    private String ReferenceDateFieldName;
  }

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    final UserTaskDurationTime userTaskDurationTime = reportData.getConfiguration().getUserTaskDurationTime();
    return USER_TASKS + "." + userTaskDurationTimeTypeMap.get(userTaskDurationTime).getReferenceDateFieldName();
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto reportData) {
    final UserTaskDurationTime userTaskDurationTime = reportData.getConfiguration().getUserTaskDurationTime();
    return USER_TASKS + "." + userTaskDurationTimeTypeMap.get(userTaskDurationTime).getDurationFieldName();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.USER_TASK);
    view.setProperty(ProcessViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
