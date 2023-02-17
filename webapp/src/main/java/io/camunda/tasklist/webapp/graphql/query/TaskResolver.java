/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import static io.camunda.tasklist.webapp.graphql.TasklistGraphQLContextBuilder.VARIABLE_DATA_LOADER;

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.service.VariableService.GetVariablesRequest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.dataloader.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskResolver implements GraphQLResolver<TaskDTO> {

  @Autowired private ProcessCache processCache;

  public CompletableFuture<List<VariableDTO>> getVariables(
      TaskDTO task, DataFetchingEnvironment dfe) {
    final DataLoader<GetVariablesRequest, List<VariableDTO>> dataloader =
        dfe.getDataLoader(VARIABLE_DATA_LOADER);
    return dataloader.load(GetVariablesRequest.createFrom(task, getFieldNames(dfe)));
  }

  public String getProcessName(TaskDTO task) {
    final String processName = processCache.getProcessName(task.getProcessDefinitionId());
    if (processName == null) {
      return task.getBpmnProcessId();
    }
    return processName;
  }

  public String getName(TaskDTO task) {
    final String taskName =
        processCache.getTaskName(task.getProcessDefinitionId(), task.getFlowNodeBpmnId());
    if (taskName == null) {
      return task.getFlowNodeBpmnId();
    }
    return taskName;
  }

  public String getTaskDefinitionId(TaskDTO task) {
    return task.getFlowNodeBpmnId();
  }

  private Set<String> getFieldNames(DataFetchingEnvironment dataFetchingEnvironment) {
    return dataFetchingEnvironment.getSelectionSet().getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toSet());
  }
}
