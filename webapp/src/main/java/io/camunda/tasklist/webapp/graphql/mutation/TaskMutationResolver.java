/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.service.TaskService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  @Autowired private TaskService taskService;

  @PreAuthorize("hasPermission('write')")
  public TaskDTO completeTask(String taskId, List<VariableInputDTO> variables) {
    return taskService.completeTask(taskId, variables);
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO claimTask(String taskId, String assignee, Boolean allowOverrideAssignment) {
    return taskService.assignTask(taskId, assignee, allowOverrideAssignment);
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO unclaimTask(String taskId) {
    return taskService.unassignTask(taskId);
  }
}
