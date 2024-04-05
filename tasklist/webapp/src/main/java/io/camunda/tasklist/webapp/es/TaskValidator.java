/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.UserReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskValidator {

  @Autowired private UserReader userReader;

  public void validateCanPersistDraftTaskVariables(final TaskEntity task) {
    validateTaskStateAndAssignment(task);
  }

  public void validateCanComplete(final TaskEntity taskBefore) {
    validateTaskStateAndAssignment(taskBefore);
  }

  /**
   * Validates whether a task is in {@code ACTIVE} state and if the task assignment is valid. This
   * method performs the following checks:
   *
   * <ul>
   *   <li>Checks if the task is active. If not, it throws an {@link InvalidRequestException}.
   *   <li>Checks if the current user is an API user. If so, no further checks are performed and the
   *       method returns immediately.
   *   <li>Checks if the task is assigned. If not, it throws an {@link InvalidRequestException}.
   *   <li>Checks if the task is assigned to the current user. If not, it throws an {@link
   *       InvalidRequestException}.
   * </ul>
   *
   * @param task The task entity to be validated.
   * @throws InvalidRequestException If the task is not active, not assigned, or not assigned to the
   *     current user.
   */
  private void validateTaskStateAndAssignment(TaskEntity task) {
    validateTaskIsActive(task);

    final UserDTO currentUser = getCurrentUser();
    if (currentUser.isApiUser()) {
      // JWT Token/API users are allowed to complete task assigned to anyone
      return;
    }

    validateTaskIsAssigned(task);
    if (!task.getAssignee().equals(currentUser.getUserId())) {
      throw new InvalidRequestException("Task is not assigned to " + currentUser.getUserId());
    }
  }

  public void validateCanAssign(final TaskEntity taskBefore, boolean allowOverrideAssignment) {
    validateTaskIsActive(taskBefore);

    if (getCurrentUser().isApiUser() && allowOverrideAssignment) {
      // JWT Token/API users can reassign task
      return;
    }

    if (taskBefore.getAssignee() != null) {
      throw new InvalidRequestException("Task is already assigned");
    }
  }

  public void validateCanUnassign(final TaskEntity taskBefore) {
    validateTaskIsActive(taskBefore);
    validateTaskIsAssigned(taskBefore);
  }

  private static void validateTaskIsActive(final TaskEntity taskBefore) {
    if (!taskBefore.getState().equals(TaskState.CREATED)) {
      throw new InvalidRequestException("Task is not active");
    }
  }

  private static void validateTaskIsAssigned(final TaskEntity taskBefore) {
    if (taskBefore.getAssignee() == null) {
      throw new InvalidRequestException("Task is not assigned");
    }
  }

  private UserDTO getCurrentUser() {
    return userReader.getCurrentUser();
  }
}
