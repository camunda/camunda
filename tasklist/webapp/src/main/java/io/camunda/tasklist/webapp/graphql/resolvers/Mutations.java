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
import graphql.kickstart.annotations.GraphQLMutationResolver;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@GraphQLMutationResolver
public class Mutations {

  private static final Logger LOGGER = LoggerFactory.getLogger(Mutations.class);
  private static final String ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED =
      "This operation is not supported using Tasklist graphql API. Please use the latest REST API. For more information, refer to the documentation: %s";

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO completeTask(final String taskId, final List<VariableInputDTO> variables) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).completeTask(taskId, variables, false);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO claimTask(
      final String taskId, final String assignee, final Boolean allowOverrideAssignment) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).assignTask(taskId, assignee, allowOverrideAssignment);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static TaskDTO unclaimTask(final String taskId) {
    return delegate(
        () -> {
          checkTaskImplementation(taskId);
          return getBean(TaskService.class).unassignTask(taskId);
        });
  }

  @GraphQLField
  @GraphQLNonNull
  public static Boolean deleteProcessInstance(final String processInstanceId) {
    return delegate(
        () ->
            DeletionStatus.DELETED.equals(
                getBean(ProcessInstanceStore.class).deleteProcessInstance(processInstanceId)));
  }

  @GraphQLField
  @GraphQLNonNull
  public static ProcessInstanceDTO startProcess(final String processDefinitionId) {
    return delegate(
        () ->
            getBean(ProcessService.class)
                .startProcessInstance(processDefinitionId, DEFAULT_TENANT_IDENTIFIER));
  }

  private static <T> T delegate(final Supplier<T> supplier) {
    return getBean(MutationAuthorizationDelegate.class).call(supplier);
  }

  private static void checkTaskImplementation(final String taskId) {
    final var task = getBean(TaskService.class).getTask(taskId);
    if (task.getImplementation() != TaskImplementation.JOB_WORKER) {
      LOGGER.warn(
          "GraphQL API is used for task with id={} implementation={}",
          task.getId(),
          task.getImplementation());
      throw new InvalidRequestException(
          String.format(
              ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED,
              getBean(TasklistProperties.class).getDocumentation().getApiMigrationDocsUrl()));
    }
  }

  @Component
  public static class MutationAuthorizationDelegate {
    @PreAuthorize("hasPermission('write')")
    public <T> T call(final Supplier<T> supplier) {
      return supplier.get();
    }
  }
}
