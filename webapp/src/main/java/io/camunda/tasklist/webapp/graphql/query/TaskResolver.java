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
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
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

  @Autowired private TaskMapper taskMapper;

  public CompletableFuture<List<VariableDTO>> getVariables(
      TaskDTO task, DataFetchingEnvironment dfe) {
    final DataLoader<GetVariablesRequest, List<VariableDTO>> dataloader =
        dfe.getDataLoader(VARIABLE_DATA_LOADER);
    return dataloader.load(GetVariablesRequest.createFrom(task, getFieldNames(dfe)));
  }

  public String getProcessName(TaskDTO task) {
    return taskMapper.getProcessName(task);
  }

  public String getName(TaskDTO task) {
    return taskMapper.getName(task);
  }

  public String getTaskDefinitionId(TaskDTO task) {
    return task.getFlowNodeBpmnId();
  }

  private static Set<String> getFieldNames(DataFetchingEnvironment dataFetchingEnvironment) {
    return dataFetchingEnvironment.getSelectionSet().getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toSet());
  }
}
