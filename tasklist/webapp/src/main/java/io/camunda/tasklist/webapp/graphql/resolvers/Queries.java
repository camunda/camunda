/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.resolvers;

import static io.camunda.tasklist.util.SpringContextHolder.getBean;
import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.kickstart.annotations.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionsService;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@GraphQLQueryResolver
public class Queries {

  @GraphQLField
  @GraphQLNonNull
  public static UserDTO currentUser() {
    return getBean(UserReader.class).getCurrentUser();
  }

  @GraphQLField
  public static List<TaskDTO> tasks(final TaskQueryDTO query) {
    return getBean(TaskService.class).getTasks(query);
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO task(final String id) {
    return getBean(TaskService.class).getTask(id);
  }

  @GraphQLField
  public static List<VariableDTO> variables(
      final String taskId, final List<String> variableNames, final DataFetchingEnvironment env) {
    return getBean(VariableService.class).getVariables(taskId, variableNames, getFieldNames(env));
  }

  /**
   * Variable id here can be either "scopeId-varName" for runtime variables or "taskId-varName" for
   * completed task variables
   *
   * @param id: variableId
   * @return
   */
  @GraphQLField
  @GraphQLNonNull
  public static VariableDTO variable(final String id, final DataFetchingEnvironment env) {
    return getBean(VariableService.class).getVariable(id, getFieldNames(env));
  }

  @GraphQLField
  public static FormDTO form(final String id, final String processDefinitionId) {
    return FormDTO.createFrom(getBean(FormStore.class).getForm(id, processDefinitionId, null));
  }

  @GraphQLField
  public static List<ProcessDTO> processes(final String search) {
    return getBean(ProcessStore.class)
        .getProcesses(
            search,
            getBean(TasklistPermissionsService.class)
                .getProcessDefinitionIdsWithCreateInstancePermission(),
            DEFAULT_TENANT_IDENTIFIER,
            null)
        .stream()
        .map(ProcessDTO::createFrom)
        .collect(Collectors.toList());
  }

  private static Set<String> getFieldNames(final DataFetchingEnvironment env) {
    return env.getSelectionSet().getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toSet());
  }
}
