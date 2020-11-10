/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CanceledUserTaskWriter extends AbstractUserTaskWriter<UserTaskInstanceDto> {

  private final OptimizeElasticsearchClient esClient;

  public CanceledUserTaskWriter(final OptimizeElasticsearchClient esClient,
                                final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public List<ImportRequestDto> generateUserTaskImports(final List<FlowNodeEventDto> activityInstances) {
    final List<UserTaskInstanceDto> userTasks = activityInstances.stream()
      .filter(activity -> Objects.nonNull(activity.getTaskId()))
      .filter(activity -> Objects.nonNull(activity.getCanceled()))
      .map(this::toUserTask)
      .collect(Collectors.toList());
    return super.generateUserTaskImports("user tasks cancellation status", esClient, userTasks);
  }

  private UserTaskInstanceDto toUserTask(final FlowNodeEventDto activityInstance) {
    final UserTaskInstanceDto userTaskInstanceDto = new UserTaskInstanceDto();
    userTaskInstanceDto.setId(activityInstance.getTaskId());
    userTaskInstanceDto.setProcessInstanceId(activityInstance.getProcessInstanceId());
    userTaskInstanceDto.setCanceled(activityInstance.getCanceled());
    userTaskInstanceDto.setStartDate(activityInstance.getStartDate());
    userTaskInstanceDto.setEndDate(activityInstance.getEndDate());
    userTaskInstanceDto.setActivityId(activityInstance.getActivityId());
    return userTaskInstanceDto;
  }

  @Override
  protected String createInlineUpdateScript() {
    // @formatter:off
    return
      "if (ctx._source.userTasks == null) { ctx._source.userTasks = []; } \n" +
      "def existingUserTasksById = ctx._source.userTasks.stream()" +
        ".collect(Collectors.toMap(task -> task.id, task -> task, (t1, t2) -> t1));\n" +
      "for (def newUserTask : params.userTasks) {\n" +
        "def existingTask  = existingUserTasksById.get(newUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          "existingTask.canceled = newUserTask.canceled;\n" +
        "} else {\n" +
          "existingUserTasksById.put(newUserTask.id, newUserTask);\n" +
        "}\n" +
      "}\n" +
      "ctx._source.userTasks = existingUserTasksById.values();\n" +
      createUpdateUserTaskMetricsScript()
      ;
    // @formatter:on
  }

}
