/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
@Slf4j
public class UserOperationsLogEntryWriter extends AbstractUserTaskWriter {
  private final OptimizeElasticsearchClient esClient;

  public UserOperationsLogEntryWriter(final OptimizeElasticsearchClient esClient,
                                      final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importUserOperationLogEntries(final List<UserOperationLogEntryDto> userOperationLogEntries) throws
                                                                                                          Exception {
    log.debug("Writing [{}] user operation log entries to elasticsearch", userOperationLogEntries.size());

    final BulkRequest userOperationsBulkRequest = new BulkRequest();
    userOperationsBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, List<UserOperationLogEntryDto>> operationsByTaskId = userOperationLogEntries.stream()
      .collect(groupingBy(UserOperationLogEntryDto::getUserTaskId));
    final Map<String, List<UserTaskInstanceDto>> userTasksByProcessInstance = operationsByTaskId
      .entrySet()
      .stream()
      .map(entry -> {
        final UserOperationLogEntryDto firstOperationEntry = entry.getValue().get(0);
        final UserTaskInstanceDto userTaskInstanceDto = new UserTaskInstanceDto(
          firstOperationEntry.getUserTaskId(),
          firstOperationEntry.getProcessInstanceId(),
          firstOperationEntry.getEngine(),
          mapToUserOperationDtos(entry.getValue())
        );
        return userTaskInstanceDto;
      })
      .collect(groupingBy(UserTaskInstanceDto::getProcessInstanceId));

    for (Map.Entry<String, List<UserTaskInstanceDto>> processInstanceTasks : userTasksByProcessInstance.entrySet()) {
      addImportUserTaskInstanceDtoWithOperationsLogRequest(
        userOperationsBulkRequest, processInstanceTasks.getKey(), processInstanceTasks.getValue()
      );
    }

    final BulkResponse bulkResponse = esClient.bulk(userOperationsBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      throw new OptimizeRuntimeException(
        "There were failures while writing user operation log entries with message: " + bulkResponse.buildFailureMessage()
      );
    }
  }

  private void addImportUserTaskInstanceDtoWithOperationsLogRequest(final BulkRequest bulkRequest,
                                                                    final String processInstanceId,
                                                                    final List<UserTaskInstanceDto> userTasks)
    throws JsonProcessingException {

    final UserTaskInstanceDto firstUserTaskEntry = userTasks.stream()
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Expected at least one user task entry"));

    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(firstUserTaskEntry.getProcessInstanceId())
      .setEngine(firstUserTaskEntry.getEngine())
      .setUserTasks(userTasks);

    final String newProcessInstanceIfAbsent = objectMapper.writeValueAsString(procInst);
    final Script updateScript = createUpdateUserOperationsScript(userTasks);
    final UpdateRequest request =
      new UpdateRequest(PROC_INSTANCE_TYPE, PROC_INSTANCE_TYPE, processInstanceId)
        .script(updateScript)
        .upsert(newProcessInstanceIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private Set<UserOperationDto> mapToUserOperationDtos(final List<UserOperationLogEntryDto> userOperationLogEntries) {
    return userOperationLogEntries.stream()
      .map(userOperationLogEntryDto -> new UserOperationDto(
        userOperationLogEntryDto.getId(),
        userOperationLogEntryDto.getUserId(),
        userOperationLogEntryDto.getTimestamp(),
        userOperationLogEntryDto.getOperationType(),
        userOperationLogEntryDto.getProperty(),
        userOperationLogEntryDto.getOriginalValue(),
        userOperationLogEntryDto.getNewValue()
      ))
      .collect(Collectors.toSet());
  }

  private Script createUpdateUserOperationsScript(final List<UserTaskInstanceDto> userTasksWithOperations) {
    return createDefaultScript(
      // @formatter:off
      // 1 check for existing userTask
      "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
      "def existingUserTasksById = ctx._source.userTasks.stream().collect(Collectors.toMap(task -> task.id, task -> task));\n" +
      "for (def currentUserTask : params.userTasks) {\n" +
        "def existingTask = existingUserTasksById.get(currentUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          // 2.1 if it exists add the operation to the existing ones
          "def existingOperationsById = existingTask.userOperations.stream()\n" +
            ".collect(Collectors.toMap(operation -> operation.id, operation -> operation));\n" +
          "currentUserTask.userOperations.stream()\n" +
            ".forEach(userOperation -> existingOperationsById.putIfAbsent(userOperation.id, userOperation));\n" +
          "existingTask.userOperations = existingOperationsById.values();\n" +
        "} else {\n" +
          // 2.2 if it doesn't exist add it with id and userOperations set
          "ctx._source.userTasks.add(currentUserTask);\n" +
        "}\n" +
      "}\n"
       + createUpdateUserTaskMetricsScript()
      ,
      // @formatter:on
      ImmutableMap.of(
        "userTasks", mapToParameterSet(userTasksWithOperations)
      )
    );
  }

}