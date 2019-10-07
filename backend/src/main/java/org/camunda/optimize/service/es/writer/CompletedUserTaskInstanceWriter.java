/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DELETE_REASON;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;

@Component
@Slf4j
public class CompletedUserTaskInstanceWriter extends AbstractUserTaskWriter<UserTaskInstanceDto> {
  private static final ImmutableSet<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_INSTANCE_ID, USER_TASK_TOTAL_DURATION,
    USER_TASK_START_DATE, USER_TASK_END_DATE, USER_TASK_DUE_DATE, USER_TASK_DELETE_REASON
  );
  private static final String UPDATE_USER_TASK_FIELDS_SCRIPT = FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newUserTask.%s;\n", fieldKey, fieldKey))
    .collect(Collectors.joining());

  private final OptimizeElasticsearchClient esClient;

  public CompletedUserTaskInstanceWriter(final OptimizeElasticsearchClient esClient,
                                         final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importUserTaskInstances(final List<UserTaskInstanceDto> userTaskInstances) throws Exception {
    String importItemName = "completed user task instances";
    log.debug("Writing [{}] {} to ES.", userTaskInstances.size(), importItemName);

    Map<String, List<UserTaskInstanceDto>> userTaskToProcessInstance = new HashMap<>();
    for (UserTaskInstanceDto userTask : userTaskInstances) {
      if (!userTaskToProcessInstance.containsKey(userTask.getProcessInstanceId())) {
        userTaskToProcessInstance.put(userTask.getProcessInstanceId(), new ArrayList<>());
      }
      userTaskToProcessInstance.get(userTask.getProcessInstanceId()).add(userTask);
    }

    ElasticsearchWriterUtil.doBulkRequestWithMap(
      esClient,
      importItemName,
      userTaskToProcessInstance,
      this::addImportUserTaskToRequest
    );
  }


  protected String createInlineUpdateScript() {
    // @formatter:off
    return
      "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
      "def existingUserTasksById = ctx._source.userTasks.stream().collect(Collectors.toMap(task -> task.id, task -> task));\n" +
      "for (def newUserTask : params.userTasks) {\n" +
        "def existingTask  = existingUserTasksById.get(newUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          UPDATE_USER_TASK_FIELDS_SCRIPT +
        "} else {\n" +
          "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
          "ctx._source.userTasks.add(newUserTask);\n" +
        "}\n" +
      "}\n"
      + createUpdateUserTaskMetricsScript()
      ;
    // @formatter:on
  }

}