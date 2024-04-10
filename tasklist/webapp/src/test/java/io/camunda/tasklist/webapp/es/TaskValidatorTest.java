/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
