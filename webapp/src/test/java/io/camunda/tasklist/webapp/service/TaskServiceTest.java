/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.es.contract.UsageMetricsContract;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  @Mock private UserReader userReader;
  @Mock private ZeebeClient zeebeClient;
  @Mock private TaskReaderWriter taskReaderWriter;
  @Mock private VariableService variableService;
  @Spy private ObjectMapper objectMapper = CommonUtils.getObjectMapper();
  @Mock private Metrics metrics;
  @Mock private UsageMetricsContract metricsContract;
  @Mock private AssigneeMigrator assigneeMigrator;
  @Mock private TaskValidator taskValidator;

  @InjectMocks private TaskService instance;

  @Test
  void getTasks() {
    // Given
    final var taskQuery = new TaskQueryDTO().setSearchAfter(new String[] {"123", "456"});
    final var providedTask =
        new TaskDTO()
            .setId("123")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"});

    when(taskReaderWriter.getTasks(taskQuery, Collections.emptyList()))
        .thenReturn(List.of(providedTask));

    // When
    final var result = instance.getTasks(taskQuery, Collections.emptyList());

    // Then
    assertThat(result).containsExactly(providedTask);
  }

  private static Stream<Arguments> getTasksTestData() {
    final var arr = new String[] {"a", "b"};
    return Stream.of(
        Arguments.of(null, null, arr, arr),
        Arguments.of(arr, null, null, arr),
        Arguments.of(null, arr, arr, null),
        Arguments.of(null, arr, arr, arr),
        Arguments.of(arr, arr, arr, arr));
  }

  @ParameterizedTest
  @MethodSource("getTasksTestData")
  void getTasksWhenMoreThanOneSearchQueryProvided(
      String[] searchBefore,
      String[] searchBeforeOrEqual,
      String[] searchAfter,
      String[] searchAfterOrEqual) {
    // Given
    final var taskQuery =
        new TaskQueryDTO()
            .setSearchBefore(searchBefore)
            .setSearchBeforeOrEqual(searchBeforeOrEqual)
            .setSearchAfter(searchAfter)
            .setSearchAfterOrEqual(searchAfterOrEqual);

    // When - Then
    assertThatThrownBy(() -> instance.getTasks(taskQuery, Collections.emptyList()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

    verifyNoInteractions(taskReaderWriter);
  }

  @Test
  void getTask() {
    // Given
    final String taskId = "123";
    final var providedTask = new TaskDTO().setId(taskId).setTaskState(TaskState.CREATED);
    when(taskReaderWriter.getTaskDTO(taskId, Collections.emptyList())).thenReturn(providedTask);

    // When
    final var result = instance.getTask(taskId, Collections.emptyList());

    // Then
    assertThat(result).isEqualTo(providedTask);
  }

  private static Stream<Arguments> assignTaskTestData() {
    final var userA = "userA";
    final var userB = "userB";
    return Stream.of(
        Arguments.of(null, true, userA, new UserDTO().setUserId(userA), userA),
        Arguments.of(false, false, userA, new UserDTO().setUserId(userB).setApiUser(true), userA),
        Arguments.of(true, true, null, new UserDTO().setUserId(userB), userB),
        Arguments.of(true, true, "", new UserDTO().setUserId(userB), userB));
  }

  @ParameterizedTest
  @MethodSource("assignTaskTestData")
  void assignTask(
      Boolean providedAllowOverrideAssignment,
      boolean expectedAllowOverrideAssignment,
      String providedAssignee,
      UserDTO user,
      String expectedAssignee) {

    // Given
    final var taskId = "123";
    final var taskBefore = mock(TaskEntity.class);
    when(taskReaderWriter.getTask(taskId)).thenReturn(taskBefore);
    when(userReader.getCurrentUser()).thenReturn(user);
    final var assignedTask = new TaskEntity().setAssignee(expectedAssignee);
    when(taskReaderWriter.persistTaskClaim(taskBefore, expectedAssignee)).thenReturn(assignedTask);

    // When
    final var result =
        instance.assignTask(taskId, providedAssignee, providedAllowOverrideAssignment);

    // Then
    verify(taskValidator).validateCanAssign(taskBefore, expectedAllowOverrideAssignment);
    assertThat(result).isEqualTo(TaskDTO.createFrom(assignedTask, objectMapper));
  }

  @Test
  public void assignTaskByApiUser() {
    // given
    final var taskId = "123";
    when(userReader.getCurrentUser()).thenReturn(new UserDTO().setUserId("userA").setApiUser(true));

    // when - then
    verifyNoInteractions(taskReaderWriter, taskValidator);
    assertThatThrownBy(() -> instance.assignTask(taskId, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Assignee must be specified");
  }

  @Test
  public void assignTaskWhenUserTriesToAssignTaskToAnotherAssignee() {
    // given
    final var taskId = "123";
    when(userReader.getCurrentUser()).thenReturn(new UserDTO().setUserId("userA"));

    // when - then
    verifyNoInteractions(taskReaderWriter, taskValidator);
    assertThatThrownBy(() -> instance.assignTask(taskId, "userB", true))
        .isInstanceOf(ForbiddenActionException.class)
        .hasMessage("User doesn't have the permission to assign another user to this task");
  }

  @Test
  void unassignTask() {
    // Given
    final var taskId = "123";
    final var taskBefore = mock(TaskEntity.class);
    when(taskReaderWriter.getTask(taskId)).thenReturn(taskBefore);
    final var unassignedTask = new TaskEntity().setId(taskId).setState(TaskState.CREATED);
    when(taskReaderWriter.persistTaskUnclaim(taskBefore)).thenReturn(unassignedTask);

    // When
    final var result = instance.unassignTask(taskId);

    // Then
    verify(taskValidator).validateCanUnassign(taskBefore);
    assertThat(result).isEqualTo(TaskDTO.createFrom(unassignedTask, objectMapper));
  }

  @Test
  void completeTask() {
    // Given
    final var taskId = "123";
    final var variables = List.of(new VariableInputDTO().setName("a").setValue("1"));
    final Map<String, Object> variablesMap = Map.of("a", 1);

    final var mockedUser = mock(UserDTO.class);
    when(userReader.getCurrentUser()).thenReturn(mockedUser);
    final var taskBefore = mock(TaskEntity.class);
    when(taskReaderWriter.getTask(taskId)).thenReturn(taskBefore);
    final var completedTask =
        new TaskEntity()
            .setId(taskId)
            .setState(TaskState.COMPLETED)
            .setAssignee("demo")
            .setCompletionTime(OffsetDateTime.now());
    when(taskReaderWriter.persistTaskCompletion(taskBefore)).thenReturn(completedTask);

    // mock zeebe command
    final var mockedJobCommandStep1 = mock(CompleteJobCommandStep1.class);
    when(zeebeClient.newCompleteCommand(123)).thenReturn(mockedJobCommandStep1);
    final var mockedJobCommandStep2 = mock(CompleteJobCommandStep1.class);
    when(mockedJobCommandStep1.variables(variablesMap)).thenReturn(mockedJobCommandStep2);
    final var mockedZeebeFuture = mock(ZeebeFuture.class);
    when(mockedJobCommandStep2.send()).thenReturn(mockedZeebeFuture);

    // When
    final var result = instance.completeTask(taskId, variables);

    // Then
    verify(taskValidator).validateCanComplete(taskBefore);
    verify(variableService).persistTaskVariables(taskId, variables);
    assertThat(result).isEqualTo(TaskDTO.createFrom(completedTask, objectMapper));
  }
}
