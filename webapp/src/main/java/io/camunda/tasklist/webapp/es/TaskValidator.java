/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.TaskValidationException;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;

public interface TaskValidator {

  TaskValidator CAN_COMPLETE =
      (taskBefore, currentUser) -> {
        if (!taskBefore.getState().equals(TaskState.CREATED)) {
          throw new TaskValidationException("Task is not active");
        }

        if (currentUser.isApiUser()) {
          // JWT Token/API users are allowed to complete any task
          return;
        }
        if (taskBefore.getAssignee() == null) {
          throw new TaskValidationException("Task is not assigned");
        }
        if (!taskBefore.getAssignee().equals(currentUser.getUserId())) {
          throw new TaskValidationException("Task is not assigned to " + currentUser.getUserId());
        }
      };

  TaskValidator CAN_CLAIM =
      (taskBefore, currentUser) -> {
        if (!taskBefore.getState().equals(TaskState.CREATED)) {
          throw new TaskValidationException("Task is not active");
        }

        if (currentUser.isApiUser()) {
          // JWT Token/API users are allowed to change task assignee
          return;
        }

        if (taskBefore.getAssignee() != null) {
          throw new TaskValidationException("Task is already assigned");
        }
      };

  TaskValidator CAN_UNCLAIM =
      (taskBefore, currentUser) -> {
        if (!taskBefore.getState().equals(TaskState.CREATED)) {
          throw new TaskValidationException("Task is not active");
        }
        if (taskBefore.getAssignee() == null) {
          throw new TaskValidationException("Task is not assigned");
        }
      };

  void validate(final TaskEntity taskBefore, final UserDTO currentUser)
      throws TaskValidationException;
}
