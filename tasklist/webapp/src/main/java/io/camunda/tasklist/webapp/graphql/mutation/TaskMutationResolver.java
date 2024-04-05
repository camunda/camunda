/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.service.TaskService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskMutationResolver.class);
  private static final String ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED =
      "This operation is not supported using Tasklist graphql API. Please use the latest REST API. For more information, refer to the documentation: %s";

  @Autowired private TaskService taskService;
  @Autowired private TasklistProperties tasklistProperties;

  @PreAuthorize("hasPermission('write')")
  public TaskDTO completeTask(String taskId, List<VariableInputDTO> variables) {
    checkTaskImplementation(taskId);
    return taskService.completeTask(taskId, variables, false);
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO claimTask(String taskId, String assignee, Boolean allowOverrideAssignment) {
    checkTaskImplementation(taskId);
    return taskService.assignTask(taskId, assignee, allowOverrideAssignment);
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO unclaimTask(String taskId) {
    checkTaskImplementation(taskId);
    return taskService.unassignTask(taskId);
  }

  private void checkTaskImplementation(String taskId) {
    final var task = taskService.getTask(taskId);
    if (task.getImplementation() != TaskImplementation.JOB_WORKER) {
      LOGGER.warn(
          "GraphQL API is used for task with id={} implementation={}",
          task.getId(),
          task.getImplementation());
      throw new InvalidRequestException(
          String.format(
              ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED,
              tasklistProperties.getDocumentation().getApiMigrationDocsUrl()));
    }
  }
}
