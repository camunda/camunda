/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;

@Component
@Slf4j
public class RunningUserTaskInstanceWriter extends AbstractUserTaskWriter<UserTaskInstanceDto> {
  private static final ImmutableSet<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_INSTANCE_ID,
    USER_TASK_START_DATE, USER_TASK_DUE_DATE
  );
  private static final String UPDATE_USER_TASK_FIELDS_SCRIPT = FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newUserTask.%s;%n", fieldKey, fieldKey))
    .collect(Collectors.joining());

  private final OptimizeElasticsearchClient esClient;

  @Autowired
  public RunningUserTaskInstanceWriter(final OptimizeElasticsearchClient esClient,
                                       final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public List<ImportRequestDto> generateUserTaskImports(final List<UserTaskInstanceDto> userTaskInstances) {
    return super.generateUserTaskImports("running user task instances", esClient, userTaskInstances);
  }

  protected String createInlineUpdateScript() {
    // @formatter:off
    return
      "if (ctx._source.userTasks == null) { ctx._source.userTasks = []; } \n" +
      "def existingUserTasksById = ctx._source.userTasks.stream()" +
        ".collect(Collectors.toMap(task -> task.id, task -> task, (t1, t2) -> t1));\n" +
      "for (def newUserTask : params.userTasks) {\n" +
        "def existingTask  = existingUserTasksById.get(newUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          UPDATE_USER_TASK_FIELDS_SCRIPT +
        "} else {\n" +
          "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
          "existingUserTasksById.put(newUserTask.id, newUserTask);\n" +
        "}\n" +
      "}\n" +
      "ctx._source.userTasks = existingUserTasksById.values();\n" +
      createUpdateUserTaskMetricsScript()
      ;
    // @formatter:on
  }

}
