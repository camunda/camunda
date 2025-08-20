/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.tasklist.property.FeatureFlagProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskValidatorTest {

  public static final String TEST_USER = "TestUser";

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  private TasklistProperties tasklistProperties;

  private TaskValidator instance;

  private FeatureFlagProperties featureFlagProperties;

  @BeforeEach
  public void setUp() {
    this.tasklistProperties = new TasklistProperties();
    this.featureFlagProperties = tasklistProperties.getFeatureFlag();

    instance = new TaskValidator(authenticationProvider, tasklistProperties);
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
    verifyNoInteractions(authenticationProvider);
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
    setAuthenticatedUser(TEST_USER);

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
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    setAuthenticatedUser(TEST_USER);

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
    setAuthenticatedUser(TEST_USER);

    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertThatCode(() -> instance.validateCanPersistDraftTaskVariables(task))
        .doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldBeAbleToPersistDraftTaskVariablesEvenIfTaskIsAssignedToAnotherPerson() {
    // given
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    setAuthenticatedClient(TEST_USER);

    // when - then
    assertThatCode(() -> instance.validateCanPersistDraftTaskVariables(task))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"COMPLETED", "CANCELED"})
  public void userShouldNotBeAbleToCompleteIfTaskIsNotActive(final TaskState taskState) {
    // given
    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(taskState);

    // when - then
    verifyNoInteractions(authenticationProvider);
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
    setAuthenticatedUser(TEST_USER);

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
    final TaskEntity task = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    setAuthenticatedUser(TEST_USER);

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

    setAuthenticatedUser(TEST_USER);

    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertThatCode(() -> instance.validateCanComplete(task)).doesNotThrowAnyException();
  }

  @Test
  public void userCanCompleteOtherPersonTaskIfAllowNonSelfAssignment() {
    // given
    featureFlagProperties.setAllowNonSelfAssignment(true);

    setAuthenticatedUser("AnotherTestUser");

    final TaskEntity task = new TaskEntity().setAssignee(TEST_USER).setState(TaskState.CREATED);

    // when - then
    assertThatCode(() -> instance.validateCanComplete(task)).doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldBeAbleToCompleteOtherPersonTask() {
    // given
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    setAuthenticatedClient(TEST_USER);

    // when - then
    assertThatCode(() -> instance.validateCanComplete(task)).doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldBeAbleToAssignToDifferentUsers() {
    // given
    final TaskEntity taskBefore = new TaskEntity().setAssignee(null).setState(TaskState.CREATED);

    setAuthenticatedClient(TEST_USER);

    // when - then
    assertThatCode(() -> instance.validateCanAssign(taskBefore, true)).doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUser() {
    // given
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    setAuthenticatedClient(TEST_USER);

    // when - then
    assertThatCode(() -> instance.validateCanAssign(taskBefore, true)).doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldBeAbleToReassignToAnotherUserWhenOverrideAllowed() {
    // given
    final TaskEntity taskBefore =
        new TaskEntity().setAssignee("previously assigned user").setState(TaskState.CREATED);

    setAuthenticatedClient(TEST_USER);

    // when - then
    assertThatCode(() -> instance.validateCanAssign(taskBefore, true)).doesNotThrowAnyException();
  }

  @Test
  public void apiUserShouldNoBeAbleToReassignToAnotherUserWhenOverrideForbidden() {
    // given
    final TaskEntity taskBefore =
        new TaskEntity()
            .setAssignee("previously assigned user")
            .setState(TaskState.CREATED)
            .setId("123");

    setAuthenticatedClient(TEST_USER);

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
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    setAuthenticatedUser(TEST_USER);

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
    final TaskEntity task =
        new TaskEntity().setAssignee("AnotherTestUser").setState(TaskState.CREATED);

    setAuthenticatedUser(TEST_USER);

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
    verifyNoInteractions(authenticationProvider);
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

  protected void setAuthenticatedUser(String name) {

    final var authentication = CamundaAuthentication.of(b -> b.user(name));
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
  }

  protected void setAuthenticatedClient(String id) {

    final var authentication = CamundaAuthentication.of(b -> b.clientId(id));
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
  }
}
