/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static io.camunda.tasklist.webapp.mapper.TaskMapper.TASK_DESCRIPTION;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.queries.RangeValueFilter;
import io.camunda.tasklist.queries.RangeValueFilter.RangeValueFilterBuilder;
import io.camunda.tasklist.queries.Sort;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskOrderBy;
import io.camunda.tasklist.queries.TaskSortFields;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistTester;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.*;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse.DraftSearchVariableValue;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.group.UserGroupService;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class TaskControllerIT extends TasklistZeebeIntegrationTest {

  @InjectMocks private IdentityProperties identityProperties;

  @MockBean private UserGroupService userGroupService;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  private TasklistTester createTask(
      final String bpmnProcessId, final String flowNodeBpmnId, final int numberOfInstances) {
    return tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  private TasklistTester createTask(
      final String bpmnProcessId, final String flowNodeBpmnId, final String payload) {
    return tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId, payload)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  private TasklistTester createTaskWithCandidateGroup(
      final String bpmnProcessId,
      final String flowNodeBpmnId,
      final int numberOfInstances,
      final String candidateGroup) {
    return tester
        .createAndDeploySimpleProcess(
            bpmnProcessId, flowNodeBpmnId, b -> b.zeebeCandidateGroups(candidateGroup))
        .processIsDeployed()
        .then()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  private TasklistTester createTaskWithCandidateUser(
      final String bpmnProcessId,
      final String flowNodeBpmnId,
      final int numberOfInstances,
      final String candidateUser) {
    return tester
        .createAndDeploySimpleProcess(
            bpmnProcessId, flowNodeBpmnId, t -> t.zeebeCandidateUsers(candidateUser))
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  private TasklistTester createTaskWithAssignee(
      final String bpmnProcessId,
      final String flowNodeBpmnId,
      final int numberOfInstances,
      final String assignee) {
    return tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId, t -> t.zeebeAssignee(assignee))
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  private TasklistTester createZeebeUserTaskWithPriority(
      final String bpmnProcessId, final String flowNodeBpmnId, final String priority) {
    return tester
        .createAndDeploySimpleProcess(
            bpmnProcessId, flowNodeBpmnId, t -> t.zeebeUserTask().zeebeTaskPriority(priority))
        .then()
        .processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  @Nested
  class SearchTaskTests {
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
              post(TasklistURIs.TASKS_URL_V1.concat("/search")),
              new TaskSearchRequest().setState(TaskState.CREATED));

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
    public void searchTasksByCandidateGroupsArray() {
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstances = 3;

      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "Admins");
      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "Users");
      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "Sales");
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins", "Users", "Sales"));

      final var searchQuery =
          new TaskQueryDTO().setCandidateGroups(new String[] {"Admins", "Users"});

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(6);
    }

    @Test
    public void searchTasksByAssigneesArray() {
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstances = 3;

      createTaskWithAssignee(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "demo");
      createTaskWithAssignee(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "admin");
      createTaskWithAssignee(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "sales");

      final var searchQuery = new TaskQueryDTO().setAssignees(new String[] {"demo", "sales"});

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(6);
    }

    @Test
    public void searchTasksByCandidateUsersArray() {
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstances = 3;
      tester
          .createAndDeploySimpleProcess(
              bpmnProcessId, flowNodeBpmnId, b -> b.zeebeCandidateUsers("demo"))
          .processIsDeployed()
          .then()
          .startProcessInstances(bpmnProcessId, numberOfInstances)
          .then()
          .taskIsCreated(flowNodeBpmnId);

      tester
          .createAndDeploySimpleProcess(
              bpmnProcessId, flowNodeBpmnId, b -> b.zeebeCandidateUsers("admin"))
          .processIsDeployed()
          .then()
          .startProcessInstances(bpmnProcessId, numberOfInstances)
          .then()
          .taskIsCreated(flowNodeBpmnId);

      tester
          .createAndDeploySimpleProcess(
              bpmnProcessId, flowNodeBpmnId, b -> b.zeebeCandidateUsers("john"))
          .processIsDeployed()
          .then()
          .startProcessInstances(bpmnProcessId, numberOfInstances)
          .then()
          .taskIsCreated(flowNodeBpmnId);
      // when(identityAuthorizationService.getUserGroups()).thenReturn(List.of("Admins", "Users",
      // "Sales"));

      final var searchQuery = new TaskQueryDTO().setCandidateUsers(new String[] {"demo"});

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(3);
    }

    @Test
    public void searchTasksByVariable() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskB_".concat(UUID.randomUUID().toString());

      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, "{\"var_0\": 0, \"var_1\": 1, \"var_2\": 2}")
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      createTask(bpmnProcessId, flowNodeBpmnId, "{\"var_0\": 2, \"var_1\": 1, \"var_2\": 2}")
          .claimHumanTask(flowNodeBpmnId);

      // when
      final var searchQuery =
          new TaskQueryDTO()
              .setTaskVariables(
                  new TaskByVariables[] {
                    new TaskByVariables().setName("var_0").setValue("0").setOperator("eq")
                  });

      final var result =
          mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(1)
          .allSatisfy(
              task -> {
                assertThat(task.getId()).isEqualTo(taskId);
              });
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
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "previewValue", "isValueTruncated", "draft")
          .containsExactlyInAnyOrder(
              tuple("varA", "\"test\"", false, null), tuple("varB", "2.02", false, null));
    }

    @Test
    public void searchAllTaskVariablesOfCreatedTask() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(
                  bpmnProcessId, flowNodeBpmnId, "{\"var1\": 111, \"var2\": 22.2, \"var2\": 22.2}")
              .getTaskId();

      // when
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "previewValue", "isValueTruncated", "draft")
          .containsExactlyInAnyOrder(
              tuple("var1", "111", false, null), tuple("var2", "22.2", false, null));
    }

    @Test
    public void searchAllTaskVariablesWithDraftValuesOfCreatedTask() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, "{\"var_0\": 0, \"var_2\": 2}")
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_1").setValue("1"),
                      new VariableInputDTO().setName("var_2").setValue("222")));

      // when
      final var persistDraftVariablesResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

      // when
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "previewValue", "isValueTruncated", "draft")
          .containsExactlyInAnyOrder(
              tuple("var_0", "0", false, null),
              tuple(
                  "var_1",
                  null,
                  false,
                  new DraftSearchVariableValue().setValue("1").setPreviewValue("1")),
              tuple(
                  "var_2",
                  "2",
                  false,
                  new DraftSearchVariableValue().setValue("222").setPreviewValue("222")));
    }

    @Test
    public void searchAllTaskVariablesWithOnlyDraftVariablesShouldHaveCorrectSize() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, null)
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      final int numberOfVariables = 11;
      final List<VariableInputDTO> variableInputs = new ArrayList<>();

      for (int i = 1; i <= numberOfVariables; i++) {
        final String variableName = "var_" + i;
        final String variableValue = String.valueOf(i);

        variableInputs.add(new VariableInputDTO().setName(variableName).setValue(variableValue));
      }

      final var saveVariablesRequest = new SaveVariablesRequest().setVariables(variableInputs);

      // when
      final var persistDraftVariablesResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

      // when
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .hasSize(11);
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
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "previewValue", "draft")
          .containsExactlyInAnyOrder(tuple("a", "1", null), tuple("c", "3", null));
    }

    // Cover Completed and Created Tasks
    @Test
    public void searchTasksShouldReturnIncludeVariablesCaseTask() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      createTask(bpmnProcessId, flowNodeBpmnId, 1)
          .claimAndCompleteHumanTask(flowNodeBpmnId, "a", "1", "b", "2", "c", "3");

      createTask(bpmnProcessId, flowNodeBpmnId, "{\"a\": 1, \"c\": 3}")
          .claimHumanTask(flowNodeBpmnId);

      final var variablesRequest =
          new TaskSearchRequest()
              .setIncludeVariables(
                  new IncludeVariable[] {
                    new IncludeVariable().setName("a"), new IncludeVariable().setName("c")
                  });

      // when
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/search")), variablesRequest);

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .satisfies(
              payload ->
                  assertThat(
                          (List<?>)
                              JsonPath.parse(payload.getContentAsString(StandardCharsets.UTF_8))
                                  .read("$.*.variables.*.draft"))
                      .isEmpty())
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(2)
          .flatExtracting("variables")
          .extracting("name", "value")
          .containsExactlyInAnyOrder(
              tuple("a", "1"), tuple("c", "3"), tuple("a", "1"), tuple("c", "3"));
    }

    @Test
    public void searchTasksShouldReturnContextVariable() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      createTask(bpmnProcessId, flowNodeBpmnId, 1)
          .claimAndCompleteHumanTask(
              flowNodeBpmnId, TASK_DESCRIPTION, "\"Context\"", "b", "2", "c", "3");

      // when
      final var result = mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(1)
          .extracting("context")
          .containsExactlyInAnyOrder("Context");
    }

    @Test
    public void searchTaskVariablesWithDraftValuesByVariableNamesOfCreatedTask() {
      // given
      final int variableSizeThreshold = 8191;
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      final String longProcessVarValue = "\"" + RandomStringUtils.randomAlphanumeric(9000) + "\"";
      final String longDraftVarValue = "\"" + RandomStringUtils.randomAlphanumeric(10000) + "\"";
      final var taskId =
          createTask(
                  bpmnProcessId,
                  flowNodeBpmnId,
                  "{\"var_int\": 128, \"var_decimal\": 553.12, \"var_array\": [\"testStr\"], \"var_long_process_str\": "
                      + longProcessVarValue
                      + "}")
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_int").setValue("998"),
                      new VariableInputDTO()
                          .setName("var_long_draft_str")
                          .setValue(longDraftVarValue),
                      new VariableInputDTO().setName("var_object").setValue("{\"propA\": 12}")));

      // when
      final var persistDraftVariablesResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

      // when
      final var variablesRequest =
          new VariablesSearchRequest()
              .setVariableNames(
                  List.of(
                      "var_int",
                      "var_decimal",
                      "var_object",
                      "var_long_draft_str",
                      "var_long_process_str",
                      "unknown_var"));
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId),
              variablesRequest);

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .extracting("name", "value", "previewValue", "isValueTruncated", "draft")
          .containsExactlyInAnyOrder(
              tuple("var_decimal", "553.12", "553.12", false, null),
              tuple(
                  "var_int",
                  "128",
                  "128",
                  false,
                  new DraftSearchVariableValue().setValue("998").setPreviewValue("998")),
              tuple(
                  "var_long_draft_str",
                  null,
                  null,
                  false,
                  new DraftSearchVariableValue()
                      .setValue(null)
                      .setIsValueTruncated(true)
                      .setPreviewValue(longDraftVarValue.substring(0, variableSizeThreshold))),
              tuple(
                  "var_long_process_str",
                  null,
                  longProcessVarValue.substring(0, variableSizeThreshold),
                  true,
                  null),
              tuple(
                  "var_object",
                  null,
                  null,
                  false,
                  new DraftSearchVariableValue()
                      .setValue("{\"propA\": 12}")
                      .setPreviewValue("{\"propA\": 12}")));
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
          .extractingListContent(objectMapper, VariableSearchResponse.class)
          .isEmpty();
    }

    @Test
    public void searchTasksWithAccessRestrictionsShouldReturnAdminsTasks() {
      // given
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstances = 3;

      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "Admins");
      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstances, "Users");

      // Mock identity service behaviour
      identityProperties.setUserAccessRestrictionsEnabled(true);
      tasklistProperties.setIdentity(identityProperties);
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));

      // when
      final var result = mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")));

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
    public void searchTasksWithAccessRestrictionsShouldReturnOnlyTasksForAllUsers() {
      // given
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstancesAdmin = 3;
      final int numberOfInstancesUser = 2;
      final int numberOfInstancesAllUsers = 1;

      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstancesAdmin, "Admins");
      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstancesUser, "Users");
      createTask(bpmnProcessId, flowNodeBpmnId, numberOfInstancesAllUsers);

      // Mock identity service behaviour
      identityProperties.setUserAccessRestrictionsEnabled(true);
      tasklistProperties.setIdentity(identityProperties);
      when(userGroupService.getUserGroups()).thenReturn(emptyList());

      // when
      final var result = mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(numberOfInstancesAllUsers)
          .allSatisfy(
              task -> {
                assertThat(task.getName()).isEqualTo(flowNodeBpmnId);
                assertThat(task.getProcessName()).isEqualTo(bpmnProcessId);
                assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
                assertThat(task.getAssignee()).isNull();
              });
    }

    @Test
    public void
        searchTasksWithAccessRestrictionsShouldReturnOnlyTaskForCandidateUserAndGroupAdmin() {
      // given
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final int numberOfInstancesAdmin = 3;
      final int numberOfInstancesUser = 2;
      final int numberOfInstancesCandidateUser = 2;

      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstancesAdmin, "Admins");
      createTaskWithCandidateGroup(bpmnProcessId, flowNodeBpmnId, numberOfInstancesUser, "Users");
      createTaskWithCandidateUser(bpmnProcessId, flowNodeBpmnId, numberOfInstancesUser, "demo");

      // Mock identity service behaviour
      identityProperties.setUserAccessRestrictionsEnabled(true);
      tasklistProperties.setIdentity(identityProperties);
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));

      // when
      final var result = mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingListContent(objectMapper, TaskSearchResponse.class)
          .hasSize(numberOfInstancesCandidateUser + numberOfInstancesAdmin)
          .allSatisfy(
              task -> {
                assertThat(task.getName()).isEqualTo(flowNodeBpmnId);
                assertThat(task.getProcessName()).isEqualTo(bpmnProcessId);
                assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
                assertThat(task.getAssignee()).isNull();
              });
    }

    @Test
    public void searchTaskWhenTaskIsNotExistThen404ErrorExpected() {
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
          .hasMessage("task with id %s was not found", randomTaskId);
    }

    @Nested
    class PrioritySearchTests {

      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA_".concat(UUID.randomUUID().toString());
      final TaskOrderBy orderBy =
          new TaskOrderBy().setField(TaskSortFields.priority).setOrder(Sort.ASC);

      @BeforeEach
      public void setUp() {
        createZeebeUserTaskWithPriority(bpmnProcessId, flowNodeBpmnId, "30");
        createZeebeUserTaskWithPriority(bpmnProcessId, flowNodeBpmnId, "45");
        createZeebeUserTaskWithPriority(bpmnProcessId, flowNodeBpmnId, "90");
      }

      private static Stream<Arguments> priorityRangeValues() {
        return Stream.of(
            Arguments.of(new RangeValueFilterBuilder().eq(30).build(), 1, new int[] {30}),
            Arguments.of(new RangeValueFilterBuilder().eq(9).build(), 0, new int[] {}),
            Arguments.of(
                new RangeValueFilterBuilder().gte(30).lte(90).build(), 3, new int[] {30, 45, 90}),
            Arguments.of(new RangeValueFilterBuilder().gt("30").build(), 2, new int[] {45, 90}),
            Arguments.of(new RangeValueFilterBuilder().gte("45").build(), 2, new int[] {45, 90}),
            Arguments.of(new RangeValueFilterBuilder().lte("45").build(), 2, new int[] {30, 45}),
            Arguments.of(new RangeValueFilterBuilder().lt("90").build(), 2, new int[] {30, 45}));
      }

      @ParameterizedTest
      @MethodSource("priorityRangeValues")
      public void searchZeebeUserTaskWithPriorityRange(
          final RangeValueFilter filter, final int resultsExpected, final int[] priorities)
          throws JsonProcessingException {
        // given

        final var searchQuery =
            new TaskQueryDTO().setPriority(filter).setSort(new TaskOrderBy[] {orderBy});

        // when
        final var result =
            mockMvcHelper.doRequest(
                post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(searchQuery)));

        // then
        final AtomicInteger i = new AtomicInteger();
        assertThat(result)
            .hasOkHttpStatus()
            .hasApplicationJsonContentType()
            .extractingListContent(objectMapper, TaskSearchResponse.class)
            .hasSize(resultsExpected)
            .allSatisfy(
                task -> {
                  assertThat(task.getName()).isEqualTo(flowNodeBpmnId);
                  assertThat(task.getProcessName()).isEqualTo(bpmnProcessId);
                  assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
                  assertThat(task.getAssignee()).isNull();
                  assertThat(task.getImplementation())
                      .isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
                  assertThat(task.getPriority()).isEqualTo(priorities[i.getAndIncrement()]);
                });
      }

      @Test
      public void searchUserTasksWithPriorityShouldExcludeJobWorkers()
          throws JsonProcessingException {
        // given
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .processIsDeployed()
            .then()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId);

        final var searchQuery =
            new TaskQueryDTO()
                .setPriority(new RangeValueFilterBuilder().gt(46).build())
                .setSort(new TaskOrderBy[] {orderBy});

        // when
        final var result =
            mockMvcHelper.doRequest(
                post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(searchQuery)));

        // then
        assertThat(result)
            .hasOkHttpStatus()
            .hasApplicationJsonContentType()
            .extractingListContent(objectMapper, TaskSearchResponse.class)
            .hasSize(1)
            .extracting(TaskSearchResponse::getImplementation, TaskSearchResponse::getPriority)
            .containsExactly(tuple(TaskImplementation.ZEEBE_USER_TASK, 90));
      }

      @Test
      public void searchUserTasksShouldIncludePriority() throws JsonProcessingException {
        // given
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .processIsDeployed()
            .then()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId);

        final var searchQuery = new TaskQueryDTO().setSort(new TaskOrderBy[] {orderBy});

        // when
        final var result =
            mockMvcHelper.doRequest(
                post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(searchQuery)));

        // then
        assertThat(result)
            .hasOkHttpStatus()
            .hasApplicationJsonContentType()
            .extractingListContent(objectMapper, TaskSearchResponse.class)
            .hasSize(4)
            .extracting(TaskSearchResponse::getImplementation, TaskSearchResponse::getPriority)
            .containsExactlyInAnyOrder(
                tuple(TaskImplementation.ZEEBE_USER_TASK, 90),
                tuple(TaskImplementation.ZEEBE_USER_TASK, 45),
                tuple(TaskImplementation.ZEEBE_USER_TASK, 30),
                tuple(TaskImplementation.JOB_WORKER, 50));
      }
    }
  }

  @Nested
  class AssignAndUnassignTaskTests {

    @Test
    public void unassignZeebeUserTask() {
      final int numberOfTasks = 1;
      final String bpmnProcessId = "testProcess";
      final String flowNodeBpmnId = "taskA";
      final String taskId =
          tester
              .createAndDeploySimpleProcess(
                  bpmnProcessId, flowNodeBpmnId, AbstractUserTaskBuilder::zeebeUserTask)
              .processIsDeployed()
              .then()
              .startProcessInstance(bpmnProcessId)
              .then()
              .taskIsCreated(flowNodeBpmnId)
              .getTaskId();
      final var result =
          mockMvcHelper.doRequest(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId));
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
                assertThat(task.getImplementation()).isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
              });

      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> tester.getTaskById(taskId).getAssignee() != null);

      final var resultUnassign =
          mockMvcHelper.doRequest(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId));

      // then
      assertThat(resultUnassign)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, TaskResponse.class)
          .satisfies(
              task -> {
                assertThat(task.getId()).isEqualTo(taskId);
                assertThat(task.getAssignee()).isNull();
                assertThat(task.getCreationDate()).isNotNull();
                assertThat(task.getCompletionDate()).isNull();
                assertThat(task.getImplementation().equals(TaskImplementation.ZEEBE_USER_TASK));
              });
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
    public void assignTaskWhenApiUserTriesToAssignAlreadyAssignedTaskThen400ErrorExpected() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskC_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();
      final var assignRequest =
          new TaskAssignRequest().setAssignee("bill_doe").setAllowOverrideAssignment(false);

      setCurrentUser(
          new UserDTO().setUserId("bob_doe").setPermissions(List.of(Permission.WRITE)), true);

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
          .hasMessage(
              """
                    { "title": "TASK_ALREADY_ASSIGNED",
                      "detail": "Task is already assigned"
                    }
                    """);
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
          .hasMessage(
              """
                    { "title": "TASK_IS_NOT_ACTIVE",
                      "detail": "Task is not active"
                    }
                    """);
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
          .hasMessage(
              """
                       { "title": "TASK_NOT_ASSIGNED",
                         "detail": "Task is not assigned"
                       }
                       """);
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
          .hasMessage(
              """
                       { "title": "TASK_IS_NOT_ACTIVE",
                         "detail": "Task is not active"
                       }
                       """);
    }
  }

  @Nested
  class GetTaskTests {
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
  }

  @Nested
  class CompleteTaskTests {
    @Test
    public void completeTaskWithVariables() throws Exception {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskE_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, "{\"var_0\": 0, \"var_1\": 1, \"var_2\": 2}")
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_2").setValue("222222"),
                      new VariableInputDTO().setName("var_b").setValue("779"),
                      new VariableInputDTO().setName("var_a").setValue("114")));

      // when
      final var persistDraftVariablesResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

      final var completeRequest =
          new TaskCompleteRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_a").setValue("225"),
                      new VariableInputDTO().setName("var_1").setValue("11111111111")));

      // when
      final var result =
          mockMvcHelper.doRequest(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId),
              completeRequest);
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
          .extracting("name", "value", "previewValue", "isValueTruncated")
          .containsExactlyInAnyOrder(
              tuple("var_0", "0", "0", false),
              tuple("var_1", "11111111111", "11111111111", false),
              tuple("var_2", "222222", "222222", false),
              tuple("var_a", "225", "225", false),
              tuple("var_b", "779", "779", false));
    }

    @Test
    public void completeZeebeUserTaskWithVariables() throws Exception {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskE_".concat(UUID.randomUUID().toString());

      final String taskId =
          tester
              .createAndDeploySimpleProcess(
                  bpmnProcessId, flowNodeBpmnId, AbstractUserTaskBuilder::zeebeUserTask)
              .processIsDeployed()
              .then()
              .startProcessInstance(bpmnProcessId)
              .then()
              .taskIsCreated(flowNodeBpmnId)
              .claimHumanTask(flowNodeBpmnId)
              .getTaskId();

      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_2").setValue("222222"),
                      new VariableInputDTO().setName("var_b").setValue("779"),
                      new VariableInputDTO().setName("var_a").setValue("114")));

      // when
      final var persistDraftVariablesResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

      final var completeRequest =
          new TaskCompleteRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("var_a").setValue("225"),
                      new VariableInputDTO().setName("var_1").setValue("11111111111")));

      // when
      final var result =
          mockMvcHelper.doRequest(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId),
              completeRequest);
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
                assertThat(task.getImplementation()).isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
              });

      assertThat(taskVariables)
          .extracting("name", "value", "previewValue", "isValueTruncated")
          .containsExactlyInAnyOrder(
              tuple("var_1", "11111111111", "11111111111", false),
              tuple("var_2", "222222", "222222", false),
              tuple("var_a", "225", "225", false),
              tuple("var_b", "779", "779", false));
    }

    @Test
    public void completeNotAssignedTaskByApiUser() throws Exception {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskD_".concat(UUID.randomUUID().toString());
      final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();
      setCurrentUser(getDefaultCurrentUser(), true);

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
          .hasMessage(
              """
                      { "title": "TASK_IS_NOT_ACTIVE",
                        "detail": "Task is not active"
                      }
                      """);
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
          .hasMessage(
              """
                       { "title": "TASK_NOT_ASSIGNED",
                         "detail": "Task is not assigned"
                       }
                       """);
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
          .hasMessage(
              """
                        { "title": "TASK_NOT_ASSIGNED_TO_CURRENT_USER",
                          "detail": "Task is not assigned to user_B"
                        }
                        """);
    }
  }

  @Nested
  class DraftTaskTests {
    @Test
    public void saveDraftTaskVariables() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskE_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();
      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(List.of(new VariableInputDTO().setName("var_a").setValue("\"test\"")));

      // when
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(result).hasHttpStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    public void saveDraftTaskVariablesWhenTaskIsNotActiveThen400ErrorExpected() {
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
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              new SaveVariablesRequest());

      // then
      assertThat(errorResult)
          .hasHttpStatus(HttpStatus.BAD_REQUEST)
          .hasApplicationProblemJsonContentType()
          .extractingErrorContent(objectMapper)
          .hasStatus(HttpStatus.BAD_REQUEST)
          .hasInstanceId()
          .hasMessage(
              """
           { "title": "TASK_IS_NOT_ACTIVE",
             "detail": "Task is not active"
           }
           """);
    }

    @Test
    public void saveDraftTaskVariablesWhenTaskIsNotAssignedThen400ErrorExpected() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskG_".concat(UUID.randomUUID().toString());
      final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();

      // when
      final var errorResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              new SaveVariablesRequest());

      // then
      assertThat(errorResult)
          .hasHttpStatus(HttpStatus.BAD_REQUEST)
          .hasApplicationProblemJsonContentType()
          .extractingErrorContent(objectMapper)
          .hasStatus(HttpStatus.BAD_REQUEST)
          .hasInstanceId()
          .hasMessage(
              """
                      { "title": "TASK_NOT_ASSIGNED",
                        "detail": "Task is not assigned"
                      }
                      """);
    }

    @Test
    public void saveDraftTaskVariablesByApiUserWhenTaskIsNotAssignedThenRequestShouldPass() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskG_".concat(UUID.randomUUID().toString());
      final var taskId = createTask(bpmnProcessId, flowNodeBpmnId, 1).getTaskId();
      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO().setName("object_var").setValue("{\"test\": true}")));
      setCurrentUser(getDefaultCurrentUser(), true);

      // when
      final var errorResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(errorResult).hasHttpStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    public void saveDraftTaskVariablesWhenTaskIsAssignedToAnotherUserThen400ErrorExpected() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();

      setCurrentUser(getDefaultCurrentUser().setUserId("user_B"));

      // when
      final var errorResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              new SaveVariablesRequest());

      // then
      assertThat(errorResult)
          .hasHttpStatus(HttpStatus.BAD_REQUEST)
          .hasApplicationProblemJsonContentType()
          .extractingErrorContent(objectMapper)
          .hasStatus(HttpStatus.BAD_REQUEST)
          .hasInstanceId()
          .hasMessage(
              """
                      { "title": "TASK_NOT_ASSIGNED_TO_CURRENT_USER",
                        "detail": "Task is not assigned to user_B"
                      }
                      """);
    }

    @Test
    public void
        saveDraftTaskVariablesByApiUserWhenTaskIsAssignedToAnotherUserThenRequestShouldPass() {
      // given
      final String bpmnProcessId = "simpleTestProcess";
      final String flowNodeBpmnId = "taskG_".concat(UUID.randomUUID().toString());
      final var taskId =
          createTask(bpmnProcessId, flowNodeBpmnId, 1).claimHumanTask(flowNodeBpmnId).getTaskId();
      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(new VariableInputDTO().setName("array_var").setValue("[30, 8, 2022]")));

      setCurrentUser(getDefaultCurrentUser().setUserId("user_B"), true);

      // when
      final var errorResult =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
              saveVariablesRequest);

      // then
      assertThat(errorResult).hasHttpStatus(HttpStatus.NO_CONTENT);
    }
  }
}
