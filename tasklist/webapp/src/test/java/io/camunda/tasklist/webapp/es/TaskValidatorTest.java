/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.UserReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskValidatorTest {

  public static final String TEST_USER = "TestUser";

  @Mock private UserReader userReader;

  @InjectMocks private TaskValidator instance;

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToPersistDraftTaskVariablesIfTaskIsNotActive(TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(taskState);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not active");
  }

  @Test
  public void userCanNotPersistDraftTaskVariablesIfAssignedToAnotherPerson() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not assigned to TestUser");
  }

  @Test
  public void userCanNotPersistDraftTaskVariablesIfAssigneeIsNull() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not assigned");
  }

  @Test
  public void userCanPersistDraftTaskVariablesWhenTaskIsAssignedToItself() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanPersistDraftTaskVariables(task));
  }

  @Test
  public void apiUserShouldBeAbleToPersistDraftTaskVariablesEvenIfTaskIsAssignedToAnotherPerson() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanPersistDraftTaskVariables(task));
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToCompleteIfTaskIsNotActive(TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(taskState);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not active");
  }

  @Test
  public void userCanNotCompleteTaskIfAssignedToAnotherPerson() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not assigned to TestUser");
  }

  @Test
  public void userCanNotCompleteTaskIfAssigneeIsNull() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not assigned");
  }

  @Test
  public void userCanCompleteTheirOwnTask() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanComplete(task));
  }

  @Test
  public void apiUserShouldBeAbleToCompleteOtherPersonTask() {
    // given
    final UserDTO user =
        new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER).setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanComplete(task));
  }

  @Test
  public void apiUserShouldBeAbleToAssignToDifferentUsers() {
    // given
    final UserDTO user = getTestUser().setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity taskBefore = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUser() {
    // given
    final UserDTO user = getTestUser().setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUserWhenOverrideAllowed() {
    // given
    final UserDTO user = getTestUser().setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldNoBeAbleToReassignToAnotherUserWhenOverrideForbidden() {
    // given
    final UserDTO user = getTestUser().setApiUser(true);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(taskBefore, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is already assigned");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToClaimTaskIfTaskIsNotActive(TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee("AnotherTestUser").setState(taskState);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not active");
  }

  @Test
  public void nonApiUserShouldNotBeAbleToReassignToAnotherUser() {
    // given
    final UserDTO user = getTestUser().setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is already assigned");
  }

  /** allowOverrideAssignment works only for API user case. */
  @Test
  public void nonApiUserShouldNotBeAbleToReassignToAnotherUserWhenOverrideAllowed() {
    // given
    final UserDTO user = getTestUser().setApiUser(false);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is already assigned");
  }

  @Test
  public void usersShouldNotBeAbleToUnassignNotAssignedTask() {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanUnassign(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not assigned");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToUnassignTaskIfTaskIsNotActive(TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee("AnotherTestUser").setState(taskState);

    // when - then
    assertThatThrownBy(() -> instance.validateCanUnassign(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Task is not active");
  }

  protected UserDTO getTestUser() {
    return new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
  }
}
