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
import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.exceptions.TaskValidationException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.webapp.es.TaskReaderWriter;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.zeebe.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  @Autowired private UserReader userReader;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private TaskReaderWriter taskReaderWriter;

  @Autowired private ObjectMapper objectMapper;

  public boolean completeTask(String taskId, List<VariableInputDTO> variables) {
    final Map<String, String> variablesMap =
        variables.stream()
            .collect(Collectors.toMap(VariableInputDTO::getName, VariableInputDTO::getValue));
    // validate
    final GetResponse taskRawResponse = taskReaderWriter.getTaskRawResponse(taskId);
    if (!taskRawResponse.isExists()) {
      throw new TasklistRuntimeException(String.format("Task with id %s was not found", taskId));
    }
    final TaskEntity taskBefore =
        fromSearchHit(taskRawResponse.getSourceAsString(), objectMapper, TaskEntity.class);
    try {
      CAN_COMPLETE.validate(taskBefore, getCurrentUsername());
    } catch (TaskValidationException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    // complete
    zeebeClient.newCompleteCommand(Long.valueOf(taskId)).variables(variablesMap).send().join();
    // persist completion
    taskReaderWriter.persistTaskCompletion(taskRawResponse);
    return true;
  }

  public TaskDTO claimTask(String taskId) {
    final TaskDTO task = taskReaderWriter.getTask(taskId);
    final String currentUsername = getCurrentUsername();
    task.setAssigneeUsername(currentUsername);
    taskReaderWriter.persistTaskAssignee(task, currentUsername);
    return task;
  }

  public TaskDTO unclaimTask(String taskId) {
    final TaskDTO task = taskReaderWriter.getTask(taskId);
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
}
