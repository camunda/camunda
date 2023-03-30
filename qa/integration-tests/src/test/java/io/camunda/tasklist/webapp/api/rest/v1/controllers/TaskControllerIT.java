/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistTester;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariablesSearchRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class TaskControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void searchTasks() {
    // given
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
    final int numberOfInstances = 3;

    createTask(bpmnProcessId, flowNodeBpmnId, numberOfInstances);

    // when
    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                .param("state", TaskState.CREATED.name()));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, TaskSearchResponse.class)
        .hasSize(numberOfInstances)
        .allSatisfy(
            task -> {
              assertThat(task.getName()).isEqualTo(flowNodeBpmnId);
              assertThat(task.getProcessName()).isEqualTo(bpmnProcessId);
              assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
              assertThat(task.getAssignee()).isNull();
            });
  }

  @Test
  public void searchTasksWhenEmptyResponseReturned() {
    // given
    final var searchQuery =
        new TaskQueryDTO()
            .setState(TaskState.CREATED)
            .setAssigned(true)
            .setAssignee("user_".concat(UUID.randomUUID().toString()));

    // when
    final var result =
        mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, TaskSearchResponse.class)
        .isEmpty();
  }

  @Test
  public void searchTasksWhenSeveralSearchOptionsProvidedThenErrorReturned() {
    // given
    final var searchQuery =
        new TaskQueryDTO()
            .setState(TaskState.CREATED)
            .setAssigned(true)
            .setSearchAfter(new String[] {"123"})
            .setSearchAfterOrEqual(new String[] {"456"});

    // when
    final var errorResult =
        mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasInstanceId()
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasMessage(
            "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
  }

  @Test
  public void getTaskById() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());

    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getName()).isEqualTo(flowNodeBpmnId);
              assertThat(task.getProcessName()).isEqualTo(bpmnProcessId);
              assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
              assertThat(task.getAssignee()).isNull();
            });
  }

  @Test
  public void getTaskByIdWhenRandomIdUsedThen404ErrorExpected() {
    // given
    final var randomTaskId = randomNumeric(16);

    // when
    final var errorResult =
        mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), randomTaskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasInstanceId()
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasMessage("Task with id %s was not found", randomTaskId);
  }

  @Test
  public void assignTaskByNonApiUser() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isEqualTo(DEFAULT_USER_ID);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNull();
            });
  }

  @Test
  public void assignTaskByNonApiUserWithEmptyRequestBody() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isEqualTo(DEFAULT_USER_ID);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNull();
            });
  }

  @Test
  public void assignTaskWhenApiUserTriesToAssignTaskToEmptyUserThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());

    final var assignRequest = new TaskAssignRequest().setAssignee(null);
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();
    setCurrentUser(getDefaultCurrentUser().setApiUser(true));

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId), assignRequest);

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Assignee must be specified");
  }

  @Test
  public void assignTaskWhenDemoUserTriesToAssignTaskToAnotherAssigneeThen403ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());
    final String assignee = "john_doe";

    final var assignRequest = new TaskAssignRequest().setAssignee(assignee);
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId), assignRequest);

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.FORBIDDEN)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasInstanceId()
        .hasMessage("User doesn't have the permission to assign another user to this task");
  }

  @Test
  public void assignTaskWhenRandomTaskIdUsedThen404ErrorExpected() {
    // given
    final var randomTaskId = randomNumeric(16);

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), randomTaskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("Task with id %s was not found", randomTaskId);
  }

  @Test
  public void assignTaskWhenApiUserSendsRequestWithoutAssigneeThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());

    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();
    setCurrentUser(
        new UserDTO()
            .setUserId("bob_doe")
            .setPermissions(List.of(Permission.WRITE))
            .setApiUser(true));

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Assignee must be specified");
  }

  @Test
  public void assignTaskWhenApiUserTriesToAssignAlreadyAssignedTaskThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();
    final var assignRequest =
        new TaskAssignRequest().setAssignee("bill_doe").setAllowOverrideAssignment(false);

    setCurrentUser(
        new UserDTO()
            .setUserId("bob_doe")
            .setPermissions(List.of(Permission.WRITE))
            .setApiUser(true));

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId), assignRequest);

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is already assigned");
  }

  @Test
  public void assignTaskWhenTaskIsNotActiveThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1)
            .claimAndCompleteHumanTask(flowNodeBpmnId)
            .getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not active");
  }

  @Test
  public void unassignTask() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isNull();
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNull();
            });
  }

  @Test
  public void unassignTaskWhenTaskNotExistsThen404ErrorExpected() {
    // given
    final var randomTaskId = randomNumeric(16);

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), randomTaskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("Task with id %s was not found", randomTaskId);
  }

  @Test
  public void unassignTaskWhenTaskWasNotAssignedThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not assigned");
  }

  @Test
  public void unassignTaskWhenTaskIsNotActiveThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1)
            .claimAndCompleteHumanTask(flowNodeBpmnId)
            .getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not active");
  }

  @Test
  public void completeTaskWithoutVariables() throws Exception {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskD_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId));
    final var taskVariables = tester.getTaskVariables();

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isEqualTo(DEFAULT_USER_ID);
              assertThat(task.getTaskState()).isEqualTo(TaskState.COMPLETED);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNotNull();
            });

    assertThat(taskVariables).isEmpty();
  }

  @Test
  public void completeTaskWithVariables() throws Exception {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskE_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();
    final var completeRequest =
        new TaskCompleteRequest()
            .setVariables(List.of(new VariableInputDTO().setName("var_a").setValue("225")));

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId), completeRequest);
    final var taskVariables = tester.getTaskVariables();

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isEqualTo(DEFAULT_USER_ID);
              assertThat(task.getTaskState()).isEqualTo(TaskState.COMPLETED);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNotNull();
            });

    assertThat(taskVariables)
        .singleElement()
        .satisfies(
            var -> {
              assertThat(var.getName()).isEqualTo("var_a");
              assertThat(var.getValue()).isEqualTo("225");
              assertThat(var.getIsValueTruncated()).isFalse();
              assertThat(var.getPreviewValue()).isEqualTo("225");
            });
  }

  @Test
  public void completeNotAssignedTaskByApiUser() throws Exception {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskD_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();
    setCurrentUser(getDefaultCurrentUser().setApiUser(true));

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId));
    final var taskVariables = tester.getTaskVariables();

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isNull();
              assertThat(task.getTaskState()).isEqualTo(TaskState.COMPLETED);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNotNull();
            });

    assertThat(taskVariables).isEmpty();
  }

  @Test
  public void completeTaskWhenTaskIsNotActiveThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskF_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1)
            .claimAndCompleteHumanTask(flowNodeBpmnId)
            .getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not active");
  }

  @Test
  public void completeTaskWhenTaskIsNotAssignedThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskG_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not assigned");
  }

  @Test
  public void completeTaskWhenTaskIsAssignedToAnotherUserThen400ErrorExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();

    setCurrentUser(getDefaultCurrentUser().setUserId("user_B"));

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.BAD_REQUEST)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasInstanceId()
        .hasMessage("Task is not assigned to user_B");
  }

  @Test
  public void searchAllTaskVariablesOfCompletedTask() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1)
            .claimAndCompleteHumanTask(flowNodeBpmnId, "varA", "\"test\"", "varB", "2.02")
            .getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, VariableDTO.class)
        .extracting("name", "value", "isValueTruncated")
        .containsExactly(tuple("varA", "\"test\"", false), tuple("varB", "2.02", false));
  }

  @Test
  public void searchTaskVariablesOfCompletedTaskByVariableNamesUsingRequestBody() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskId =
        createTask(bpmnProcessId, flowNodeBpmnId, 1)
            .claimAndCompleteHumanTask(flowNodeBpmnId, "a", "1", "b", "2", "c", "3")
            .getTaskId();
    final var variablesRequest = new VariablesSearchRequest().setVariableNames(List.of("a", "c"));

    // when
    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId),
            variablesRequest);

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, VariableDTO.class)
        .extracting("name", "value")
        .containsExactly(tuple("a", "1"), tuple("c", "3"));
  }

  @Test
  public void searchAllTaskVariablesWhenTaskDoesNotHaveVariablesThenEmptyResponseExpected() {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, VariableDTO.class)
        .isEmpty();
  }

  @Test
  public void searchTaskVariablesWhenTaskIsNotExistThen404ErrorExpected() {
    // given
    final var randomTaskId = randomNumeric(16);

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), randomTaskId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("Task with id %s was not found", randomTaskId);
  }

  private TasklistTester createTask(
      String bpmnProcessId, String flowNodeBpmnId, int numberOfInstances) {
    return tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }
}
