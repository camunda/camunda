/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskValidatorTest {

  public static final String TEST_USER = "TestUser";

  @Mock private UserReader userReader;

  @InjectMocks private TaskValidator instance;

  private MockedStatic<TasklistAuthenticationUtil> authenticationUtil;

  @BeforeEach
  public void setUp() {
    authenticationUtil = mockStatic(TasklistAuthenticationUtil.class);
  }

  @AfterEach
  public void tearDown() {
    authenticationUtil.close();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToPersistDraftTaskVariablesIfTaskIsNotActive(
      final TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(taskState);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_IS_NOT_ACTIVE",
              "detail": "Task is not active"
            }
            """);
  }

  @Test
  public void userCanNotPersistDraftTaskVariablesIfAssignedToAnotherPerson() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final UserDTO user = new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_NOT_ASSIGNED_TO_CURRENT_USER",
              "detail": "Task is not assigned to TestUser"
            }
            """);
  }

  @Test
  public void userCanNotPersistDraftTaskVariablesIfAssigneeIsNull() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanPersistDraftTaskVariables(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_NOT_ASSIGNED",
              "detail": "Task is not assigned"
            }
            """);
  }

  @Test
  public void userCanPersistDraftTaskVariablesWhenTaskIsAssignedToItself() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final UserDTO user = new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanPersistDraftTaskVariables(task));
  }

  @Test
  public void apiUserShouldBeAbleToPersistDraftTaskVariablesEvenIfTaskIsAssignedToAnotherPerson() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanPersistDraftTaskVariables(task));
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToCompleteIfTaskIsNotActive(final TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(taskState);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_IS_NOT_ACTIVE",
              "detail": "Task is not active"
            }
            """);
  }

  @Test
  public void userCanNotCompleteTaskIfAssignedToAnotherPerson() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final UserDTO user = new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_NOT_ASSIGNED_TO_CURRENT_USER",
              "detail": "Task is not assigned to %s"
            }
            """
                .formatted(TEST_USER));
  }

  @Test
  public void userCanNotCompleteTaskIfAssigneeIsNull() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanComplete(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_NOT_ASSIGNED",
              "detail": "Task is not assigned"
            }
            """);
  }

  @Test
  public void userCanCompleteTheirOwnTask() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final UserDTO user = new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
    when(userReader.getCurrentUser()).thenReturn(user);
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanComplete(task));
  }

  @Test
  public void apiUserShouldBeAbleToCompleteOtherPersonTask() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanComplete(task));
  }

  @Test
  public void apiUserShouldBeAbleToAssignToDifferentUsers() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity taskBefore = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUser() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUserWhenOverrideAllowed() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    // when - then
    assertDoesNotThrow(() -> instance.validateCanAssign(taskBefore, true));
  }

  @Test
  public void apiUserShouldNoBeAbleToReassignToAnotherUserWhenOverrideForbidden() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    final TaskEntity taskBefore =
        new TaskEntity()
            .setAssignee("previously assigned user")
            .setState(TaskState.CREATED)
            .setId("123");

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(taskBefore, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_ALREADY_ASSIGNED",
              "detail": "Task is already assigned"
            }
            """);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToClaimTaskIfTaskIsNotActive(final TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee("AnotherTestUser").setState(taskState);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_IS_NOT_ACTIVE",
              "detail": "Task is not active"
            }
            """);
  }

  @Test
  public void nonApiUserShouldNotBeAbleToReassignToAnotherUser() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_ALREADY_ASSIGNED",
              "detail": "Task is already assigned"
            }
            """);
  }

  /** allowOverrideAssignment works only for API user case. */
  @Test
  public void nonApiUserShouldNotBeAbleToReassignToAnotherUserWhenOverrideAllowed() {
    // given
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(false);
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    // when - then
    assertThatThrownBy(() -> instance.validateCanAssign(task, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_ALREADY_ASSIGNED",
              "detail": "Task is already assigned"
            }
            """);
  }

  @Test
  public void usersShouldNotBeAbleToUnassignNotAssignedTask() {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    // when - then
    verifyNoInteractions(userReader);
    assertThatThrownBy(() -> instance.validateCanUnassign(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_NOT_ASSIGNED",
              "detail": "Task is not assigned"
            }
            """);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToUnassignTaskIfTaskIsNotActive(final TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee("AnotherTestUser").setState(taskState);

    // when - then
    assertThatThrownBy(() -> instance.validateCanUnassign(task))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            """
            { "title": "TASK_IS_NOT_ACTIVE",
              "detail": "Task is not active"
            }
            """);
  }

  protected UserDTO getTestUser() {
    return new UserDTO().setUserId(TEST_USER).setDisplayName(TEST_USER);
  }
}
