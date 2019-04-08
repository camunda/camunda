/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_DELETE_REASON;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
public class CompletedUserTaskInstanceWriter extends AbstractUserTaskWriter {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTaskInstanceWriter.class);

  private RestHighLevelClient esClient;

  @Autowired
  public CompletedUserTaskInstanceWriter(final RestHighLevelClient esClient,
                                         final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importUserTaskInstances(final List<UserTaskInstanceDto> userTaskInstances) throws Exception {
    logger.debug("Writing [{}] completed user task instances to elasticsearch", userTaskInstances.size());

    final BulkRequest userTaskToProcessInstanceBulkRequest = new BulkRequest();
    userTaskToProcessInstanceBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, List<UserTaskInstanceDto>> userTasksByProcessInstance =
      userTaskInstances.stream().collect(groupingBy(UserTaskInstanceDto::getProcessInstanceId));

    for (Map.Entry<String, List<UserTaskInstanceDto>> processInstanceEntry : userTasksByProcessInstance.entrySet()) {
      addActivityInstancesToProcessInstanceRequest(
        userTaskToProcessInstanceBulkRequest,
        processInstanceEntry.getKey(),
        processInstanceEntry.getValue()
      );
    }

    BulkResponse bulkResponse = esClient.bulk(userTaskToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing completed user task instances with message: {}",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }

  }

  private void addActivityInstancesToProcessInstanceRequest(final BulkRequest bulkRequest,
                                                            final String processInstanceId,
                                                            final List<UserTaskInstanceDto> userTasks)
    throws IOException {

    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(USER_TASKS, mapToParameterSet(userTasks));
    final Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      createInlineUpdateScript(),
      scriptParameters
    );

    final UserTaskInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(firstUserTaskInstance.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(firstUserTaskInstance.getProcessDefinitionKey());
    procInst.setProcessInstanceId(firstUserTaskInstance.getProcessInstanceId());
    procInst.getUserTasks().addAll(userTasks);
    procInst.setEngine(firstUserTaskInstance.getEngine());
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
          createUpdateFieldsScript(ImmutableSet.of(
                USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_INSTANCE_ID,
                USER_TASK_TOTAL_DURATION, USER_TASK_WORK_DURATION, USER_TASK_IDLE_DURATION,
                USER_TASK_START_DATE, USER_TASK_END_DATE, USER_TASK_DUE_DATE, USER_TASK_DELETE_REASON
              )) +
        "} else {\n" +
          "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
          "ctx._source.userTasks.add(newUserTask);\n" +
        "}\n" +
      "}\n"
      + createUpdateUserTaskMetricsScript()
      ;
    // @formatter:on
  }



  private String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("existingTask.%s = newUserTask.%s;\n", fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

}