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
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
@Slf4j
public class RunningUserTaskInstanceWriter extends AbstractUserTaskWriter {
  private static final ImmutableSet<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_INSTANCE_ID,
    USER_TASK_START_DATE, USER_TASK_DUE_DATE
  );
  private static final String UPDATE_USER_TASK_FIELDS_SCRIPT = FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newUserTask.%s;\n", fieldKey, fieldKey))
    .collect(Collectors.joining());

  private RestHighLevelClient esClient;

  @Autowired
  public RunningUserTaskInstanceWriter(final RestHighLevelClient esClient,
                                       final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importUserTaskInstances(final List<UserTaskInstanceDto> userTaskInstances) throws Exception {
    log.debug("Writing [{}] running user task instances to elasticsearch", userTaskInstances.size());

    final BulkRequest userTaskToProcessInstanceBulkRequest = new BulkRequest();
    userTaskToProcessInstanceBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, List<UserTaskInstanceDto>> userTasksByProcessInstance =
      userTaskInstances.stream().collect(groupingBy(UserTaskInstanceDto::getProcessInstanceId));

    for (Map.Entry<String, List<UserTaskInstanceDto>> processInstanceEntry : userTasksByProcessInstance.entrySet()) {
      addUserTaskToProcessInstanceRequest(
        userTaskToProcessInstanceBulkRequest,
        processInstanceEntry.getKey(),
        processInstanceEntry.getValue()
      );
    }

    final BulkResponse bulkResponse = esClient.bulk(userTaskToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing running user task instances with message: %s",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }

  }

  private void addUserTaskToProcessInstanceRequest(final BulkRequest bulkRequest,
                                                   final String processInstanceId,
                                                   final List<UserTaskInstanceDto> userTasks)
    throws IOException {

    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(USER_TASKS, mapToParameterSet(userTasks));
    final Script updateScript = createDefaultScript(createInlineUpdateScript(), scriptParameters);

    final UserTaskInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(firstUserTaskInstance.getProcessInstanceId())
      .setEngine(firstUserTaskInstance.getEngine())
      .setUserTasks(userTasks);
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE), PROC_INSTANCE_TYPE, processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private String createInlineUpdateScript() {
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
