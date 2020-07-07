/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.webapp.es.TaskReaderWriter;
import io.zeebe.tasklist.webapp.graphql.entity.VariableInputDTO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private TaskReaderWriter taskReader;

  public boolean completeTask(String taskId, List<VariableInputDTO> variables) {
    // complete the task
    final Map<String, String> variablesMap =
        variables.stream()
            .collect(Collectors.toMap(VariableInputDTO::getName, VariableInputDTO::getValue));
    try {
      zeebeClient.newCompleteCommand(Long.valueOf(taskId)).variables(variablesMap).send().join();
    } catch (Exception ex) {
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    }
    taskReader.persistTaskCompletion(taskId);
    return true;
  }
}
