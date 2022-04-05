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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TaskValidatorTest {

  public static final String TEST_USER = "TestUser";

  public TaskValidatorTest() {}

  @Test
  public void userCanNotCompleteTaskIfNotInCompletedState() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee(TEST_USER).setState(TaskState.COMPLETED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void userCanNotCompleteTaskIfNotInCanceledState() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CANCELED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void userCanNotCompleteTaskIfAssignedToAnotherPerson() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user =
                  new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not assigned to TestUser", exception.getMessage());
  }

  @Test
  public void userCanNotCompleteTaskIfAssigneeIsNull() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user =
                  new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee(null).setState(TaskState.CREATED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not assigned", exception.getMessage());
  }

  @Test
  public void userCanCompleteTheirOwnTask() throws TaskValidationException {
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);
    TaskValidator.CAN_COMPLETE.validate(task, user);
  }

  @Test
  public void apiUserShouldBeAbleToCompleteOtherPersonTask() throws TaskValidationException {
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(true);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);
    TaskValidator.CAN_COMPLETE.validate(task, user);
  }

  @Test
  public void apiUserShouldNotBeAbleToCompleteCompletedTasks() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user =
                  new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(true);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.COMPLETED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void apiUserShouldNotBeAbleToCompleteCanceledTasks() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(true);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CANCELED);
              TaskValidator.CAN_COMPLETE.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void apiUserShouldBeAbleToAssignToDifferentUsers() throws TaskValidationException {
    final UserDTO user = getTestUser().setApiUser(true);
    final TaskEntity taskBefore = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);
    TaskValidator.CAN_CLAIM.validate(taskBefore, user);
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUser() throws TaskValidationException {
    final UserDTO user = getTestUser().setApiUser(true);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);
    TaskValidator.CAN_CLAIM.validate(taskBefore, user);
  }

  @Test
  public void apiUserShouldNotBeAbleToClaimTaskIfTaskIsCompleted() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(true);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.COMPLETED);
              TaskValidator.CAN_CLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void apiUserShouldNotBeAbleToClaimTaskIfTaskIsCanceled() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(true);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CANCELED);
              TaskValidator.CAN_CLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void nonApiUserShouldNotBeAbleToReassignToAnotherUser() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);
              TaskValidator.CAN_CLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is already assigned", exception.getMessage());
  }

  @Test
  public void usersShouldNotBeAbleToUnclaimNotClaimed() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee(null).setState(TaskState.CREATED);
              TaskValidator.CAN_UNCLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is not assigned", exception.getMessage());
  }

  @Test
  public void usersShouldNotBeAbleToUnclaimTaskIfTaskIsCanceled() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CANCELED);
              TaskValidator.CAN_UNCLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  @Test
  public void usersShouldNotBeAbleToUnclaimTaskIfTaskIsCompleted() throws TaskValidationException {
    final TaskValidationException exception =
        Assertions.assertThrows(
            TaskValidationException.class,
            () -> {
              final UserDTO user = getTestUser().setApiUser(false);
              final TaskEntity task =
                  new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.COMPLETED);
              TaskValidator.CAN_UNCLAIM.validate(task, user);
            });
    Assertions.assertEquals("Task is not active", exception.getMessage());
  }

  protected UserDTO getTestUser() {
    return new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
  }
}
