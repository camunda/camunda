/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.mutation;

import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.zeebe.tasklist.webapp.es.TaskValidator.CAN_COMPLETE;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.exceptions.TaskValidationException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.webapp.es.TaskReaderWriter;
import io.zeebe.tasklist.webapp.es.VariableReaderWriter;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.graphql.entity.VariableDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  @Autowired private UserReader userReader;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private TaskReaderWriter taskReaderWriter;

  @Autowired private VariableReaderWriter variableReaderWriter;

  @Autowired private ObjectMapper objectMapper;

  public TaskDTO completeTask(String taskId, List<VariableDTO> variables) {
    final Map<String, Object> variablesMap =
        variables.stream().collect(Collectors.toMap(VariableDTO::getName, this::extractTypedValue));
    // validate
    final SearchHit taskSearchHit;
    try {
      taskSearchHit = taskReaderWriter.getTaskRawResponse(taskId);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    final TaskEntity taskBefore =
        fromSearchHit(taskSearchHit.getSourceAsString(), objectMapper, TaskEntity.class);
    try {
      CAN_COMPLETE.validate(taskBefore, getCurrentUsername());
    } catch (TaskValidationException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    // complete
    CompleteJobCommandStep1 completeJobCommand =
        zeebeClient.newCompleteCommand(Long.valueOf(taskId));
    if (variablesMap != null) {
      completeJobCommand = completeJobCommand.variables(variablesMap);
    }
    completeJobCommand.send().join();
    // persist completion and variables
    final TaskEntity completedTask = taskReaderWriter.persistTaskCompletion(taskSearchHit);
    variableReaderWriter.persistTaskVariables(taskId, variables);
    return TaskDTO.createFrom(completedTask, objectMapper);
  }

  private Object extractTypedValue(VariableDTO var) {
    try {
      return objectMapper.readValue(var.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public TaskDTO claimTask(String taskId) {
    final TaskDTO task = taskReaderWriter.getTaskDTO(taskId, null);
    final String currentUsername = getCurrentUsername();
    task.setAssigneeUsername(currentUsername);
    taskReaderWriter.persistTaskAssignee(task, currentUsername);
    return task;
  }

  public TaskDTO unclaimTask(String taskId) {
    final TaskDTO task = taskReaderWriter.getTaskDTO(taskId, null);
    task.setAssigneeUsername(null);
    taskReaderWriter.persistTaskAssignee(task, null);
    return task;
  }

  private String getCurrentUsername() {
    final UserDTO currentUser = userReader.getCurrentUser();
    if (currentUser == null) {
      throw new TasklistRuntimeException("Current user is not found.");
    }
    return currentUser.getUsername();
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }
}
