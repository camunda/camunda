/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import static io.zeebe.tasklist.webapp.graphql.TasklistGraphQLContextBuilder.USER_DATA_LOADER;
import static io.zeebe.tasklist.webapp.graphql.TasklistGraphQLContextBuilder.VARIABLE_DATA_LOADER;

import graphql.kickstart.execution.context.GraphQLContext;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.zeebe.tasklist.webapp.es.VariableReaderWriter.GetVariablesRequest;
import io.zeebe.tasklist.webapp.es.cache.WorkflowCache;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.graphql.entity.VariableDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskResolver implements GraphQLResolver<TaskDTO> {

  @Autowired private WorkflowCache workflowCache;

  public CompletableFuture<UserDTO> getAssignee(TaskDTO task, DataFetchingEnvironment dfe) {
    if (task.getAssigneeUsername() == null) {
      return null;
    }
    final DataLoader<String, UserDTO> dataloader =
        ((GraphQLContext) dfe.getContext())
            .getDataLoaderRegistry()
            .get()
            .getDataLoader(USER_DATA_LOADER);

    return dataloader.load(task.getAssigneeUsername());
  }

  public CompletableFuture<List<VariableDTO>> getVariables(
      TaskDTO task, DataFetchingEnvironment dfe) {
    final DataLoader<GetVariablesRequest, List<VariableDTO>> dataloader =
        ((GraphQLContext) dfe.getContext())
            .getDataLoaderRegistry()
            .get()
            .getDataLoader(VARIABLE_DATA_LOADER);
    return dataloader.load(GetVariablesRequest.createFrom(task));
  }

  public String getWorkflowName(TaskDTO task) {
    final String workflowName = workflowCache.getWorkflowName(task.getWorkflowId());
    if (workflowName == null) {
      return task.getBpmnProcessId();
    }
    return workflowName;
  }

  public String getName(TaskDTO task) {
    final String taskName =
        workflowCache.getTaskName(task.getWorkflowId(), task.getFlowNodeBpmnId());
    if (taskName == null) {
      return task.getFlowNodeBpmnId();
    }
    return taskName;
  }
}
