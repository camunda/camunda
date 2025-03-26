/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.webapp.mapper.TaskMapper.TASK_DESCRIPTION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.*;
import io.camunda.tasklist.webapp.dto.TaskDTO;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.dto.VariableDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.group.UserGroupService;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskControllerTest {

  public static final String USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK =
      "User does not have permission to perform on this task.";
  private MockMvc mockMvc;
  @Mock private TaskService taskService;
  @Mock private VariableService variableService;
  @Mock private TaskMapper taskMapper;
  @InjectMocks private TaskController instance;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TasklistProperties tasklistProperties;

  @Mock private UserReader userReader;
  @Mock private UserGroupService userGroupService;
  @Mock private TasklistPermissionServices tasklistPermissionServices;

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
    when(userReader.getCurrentUser()).thenReturn(new UserDTO());
  }

  @Nested
  class SearchTaskTests {
    @Test
    void searchTasks() throws Exception {
      // Given
      final var providedTask =
          new TaskDTO()
              .setId("111111")
              .setFlowNodeBpmnId("Register the passenger")
              .setBpmnProcessId("Flight registration")
              .setAssignee("demo")
              .setCreationTime("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"});
      final var taskResponse =
          new TaskSearchResponse()
              .setId("111111")
              .setName("Register the passenger")
              .setProcessName("Flight registration")
              .setAssignee("demo")
              .setCreationDate("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"});
      final var searchRequest =
          new TaskSearchRequest()
              .setPageSize(20)
              .setState(TaskState.CREATED)
              .setAssigned(true)
              .setSearchAfter(new String[] {"123", "456"});
      final var searchQuery = mock(TaskQueryDTO.class);
      when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
      when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
          .thenReturn(List.of(providedTask));
      when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

      // Then
      assertThat(result).singleElement().isEqualTo(taskResponse);
    }

    @Test
    void searchTaskWithContextVariable() throws Exception {
      // Given
      final var providedTask =
          new TaskDTO()
              .setId("111111")
              .setFlowNodeBpmnId("Register the passenger")
              .setBpmnProcessId("Flight registration")
              .setAssignee("demo")
              .setCreationTime("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"})
              .setVariables(
                  new VariableDTO[] {
                    new VariableDTO()
                        .setId("varA_id")
                        .setName(TASK_DESCRIPTION)
                        .setPreviewValue("\"Hi, I am a context variable\"")
                        .setValue("\"Hi, I am a context variable\"")
                        .setIsValueTruncated(true)
                  });
      final var taskResponse =
          new TaskSearchResponse()
              .setId("111111")
              .setName("Register the passenger")
              .setProcessName("Flight registration")
              .setAssignee("demo")
              .setCreationDate("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"})
              .setContext("Hi, I am a context variable");
      final var searchRequest =
          new TaskSearchRequest()
              .setPageSize(20)
              .setState(TaskState.CREATED)
              .setAssigned(true)
              .setSearchAfter(new String[] {"123", "456"});
      final var searchQuery = mock(TaskQueryDTO.class);
      when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
      when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
          .thenReturn(List.of(providedTask));
      when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

      // Then
      assertThat(result).singleElement().isEqualTo(taskResponse);
    }

    @Test
    void searchTasksWithVariablesWithFullValues() throws Exception {
      // Given
      final var providedTask =
          new TaskDTO()
              .setId("111111")
              .setFlowNodeBpmnId("Register the passenger")
              .setBpmnProcessId("Flight registration")
              .setAssignee("demo")
              .setCreationTime("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"})
              .setVariables(
                  new VariableDTO[] {
                    new VariableDTO()
                        .setId("varA_id")
                        .setName("varA")
                        .setPreviewValue("veryLon")
                        .setValue("veryLong")
                        .setIsValueTruncated(true)
                  });
      final var taskResponse =
          new TaskSearchResponse()
              .setId("111111")
              .setName("Register the passenger")
              .setProcessName("Flight registration")
              .setAssignee("demo")
              .setCreationDate("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"})
              .setVariables(
                  new VariableSearchResponse[] {
                    new VariableSearchResponse()
                        .setId("varA_id")
                        .setName("varA")
                        .setPreviewValue("veryLon")
                        .setValue("veryLong")
                        .setIsValueTruncated(false)
                  });
      final var searchRequest =
          new TaskSearchRequest()
              .setPageSize(20)
              .setState(TaskState.CREATED)
              .setAssigned(true)
              .setSearchAfter(new String[] {"123", "456"})
              .setIncludeVariables(
                  new IncludeVariable[] {
                    new IncludeVariable().setName("varA").setAlwaysReturnFullValue(true)
                  });
      final var searchQuery = mock(TaskQueryDTO.class);
      when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
      when(taskService.getTasks(searchQuery, Set.of("varA", TASK_DESCRIPTION), true))
          .thenReturn(List.of(providedTask));
      when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

      // Then
      assertThat(result).singleElement().isEqualTo(taskResponse);
    }

    @Test
    void searchTasksWhenSeveralSearchOptionsProvidedThenErrorReturned() throws Exception {
      // Given
      final var searchRequest =
          new TaskSearchRequest()
              .setPageSize(20)
              .setState(TaskState.CREATED)
              .setAssigned(true)
              .setSearchAfter(new String[] {"123"})
              .setSearchAfterOrEqual(new String[] {"456"});
      final var searchQuery = mock(TaskQueryDTO.class);
      when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
      when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
          .thenThrow(
              new InvalidRequestException(
                  "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request."));

      // When & Then
      mockMvc
          .perform(
              post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isBadRequest());
    }

    @Test
    void searchTasksWhenEmptyRequestBodySend() throws Exception {
      // Given
      final var providedTask =
          new TaskDTO()
              .setId("111111")
              .setFlowNodeBpmnId("Register the passenger")
              .setBpmnProcessId("Flight registration")
              .setAssignee("demo")
              .setCreationTime("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"});
      final var taskResponse =
          new TaskSearchResponse()
              .setId("111111")
              .setName("Register the passenger")
              .setProcessName("Flight registration")
              .setAssignee("demo")
              .setCreationDate("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setSortValues(new String[] {"123", "456"});
      final var searchRequest = new TaskSearchRequest().setPageSize(50);
      final var searchQuery = new TaskQueryDTO().setPageSize(50);
      when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
      when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
          .thenReturn(List.of(providedTask));
      when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(post(TasklistURIs.TASKS_URL_V1.concat("/search")))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

      // Then
      assertThat(result).singleElement().isEqualTo(taskResponse);
    }

    @Test
    void searchTaskVariables() throws Exception {
      // Given
      final var taskId = "778899";
      final var variableA =
          new VariableSearchResponse()
              .setId("111")
              .setName("varA")
              .setValue("925.5")
              .setPreviewValue("925.5")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5"));
      final var variableB =
          new VariableSearchResponse()
              .setId("112")
              .setName("varB")
              .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
              .setIsValueTruncated(true)
              .setPreviewValue("\"veryVeryLongValue");
      final var variableC =
          new VariableSearchResponse()
              .setId("113")
              .setName("varC")
              .setValue("\"normalValue\"")
              .setPreviewValue("\"normalValue\"")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("\"updatedVeryVeryLongValue\"")
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo"));
      final var variableNames = Set.of("varA", "varB", "varC");
      when(variableService.getVariableSearchResponses(taskId, variableNames))
          .thenReturn(List.of(variableA, variableB, variableC));

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(
                          CommonUtils.OBJECT_MAPPER.writeValueAsString(
                              new VariablesSearchRequest()
                                  .setVariableNames(new ArrayList<>(variableNames))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

      // Then
      assertThat(result)
          .extracting("name", "value", "isValueTruncated", "previewValue", "draft")
          .containsExactlyInAnyOrder(
              tuple(
                  "varA",
                  "925.5",
                  false,
                  "925.5",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5")),
              tuple("varB", null, true, "\"veryVeryLongValue", null),
              tuple(
                  "varC",
                  "\"normalValue\"",
                  false,
                  "\"normalValue\"",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo")));
    }

    @Test
    void searchTaskVariablesWithFullValues() throws Exception {
      // Given
      final var taskId = "778899";
      final var variableA =
          new VariableSearchResponse()
              .setId("111")
              .setName("varA")
              .setValue("925.5")
              .setPreviewValue("925.5")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5"));
      final var variableB =
          new VariableSearchResponse()
              .setId("112")
              .setName("varB")
              .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
              .setIsValueTruncated(true)
              .setPreviewValue("\"veryVeryLongValue");
      final var variableC =
          new VariableSearchResponse()
              .setId("113")
              .setName("varC")
              .setValue("\"normalValue\"")
              .setPreviewValue("\"normalValue\"")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("\"updatedVeryVeryLongValue\"")
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo"));
      final var variableNames = Set.of("varA", "varB", "varC");
      when(variableService.getVariableSearchResponses(taskId, variableNames))
          .thenReturn(List.of(variableA, variableB, variableC));

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(
                          CommonUtils.OBJECT_MAPPER.writeValueAsString(
                              new VariablesSearchRequest()
                                  .setIncludeVariables(
                                      List.of(
                                          new IncludeVariable()
                                              .setName("varA")
                                              .setAlwaysReturnFullValue(true),
                                          new IncludeVariable()
                                              .setName("varB")
                                              .setAlwaysReturnFullValue(true),
                                          new IncludeVariable()
                                              .setName("varC")
                                              .setAlwaysReturnFullValue(true)))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

      // Then
      assertThat(result)
          .extracting("name", "value", "isValueTruncated", "previewValue", "draft")
          .containsExactlyInAnyOrder(
              tuple(
                  "varA",
                  "925.5",
                  false,
                  "925.5",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5")),
              tuple(
                  "varB",
                  "\"veryVeryLongValueThatExceedsVariableSizeLimit\"",
                  true,
                  "\"veryVeryLongValue",
                  null),
              tuple(
                  "varC",
                  "\"normalValue\"",
                  false,
                  "\"normalValue\"",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo")
                      .setValue("\"updatedVeryVeryLongValue\"")));
    }

    @Test
    void searchTaskVariablesWithVariableNamesAndIncludeVariablesShouldReturn400() throws Exception {
      final var expectedErrorMessage =
          "Only one of [variableNames, includeVariables] must be present in request";
      mockMvc
          .perform(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), "taskId")
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(
                      CommonUtils.OBJECT_MAPPER.writeValueAsString(
                          new VariablesSearchRequest()
                              .setVariableNames(List.of("varA"))
                              .setIncludeVariables(List.of(new IncludeVariable().setName("varA")))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(
              content()
                  .string(
                      containsString(
                          expectedErrorMessage))) // Check for the error message in the response
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    void variablesSearchWhenTaskIdDoesntExistOrTenantWithoutAccess() throws Exception {
      final var taskId = "2222222";
      mockMvc
          .perform(post(TasklistURIs.TASKS_URL_V1.concat("{taskId}/variables/search"), taskId))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetTaskTests {
    @Test
    void getTaskById() throws Exception {
      // Given
      final var taskId = "2222222";
      final var providedTask =
          new TaskDTO()
              .setId(taskId)
              .setFlowNodeBpmnId("Register the passenger")
              .setBpmnProcessId("Flight registration")
              .setAssignee("demo")
              .setCreationTime("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED)
              .setImplementation(TaskImplementation.JOB_WORKER);
      final var taskResponse =
          new TaskResponse()
              .setId(taskId)
              .setName("Register the passenger")
              .setProcessName("Flight registration")
              .setAssignee("demo")
              .setCreationDate("2023-02-20T18:37:19.214+0000")
              .setTaskState(TaskState.CREATED);

      when(tasklistProperties.getIdentity()).thenReturn(null);
      when(taskService.getTask(taskId)).thenReturn(providedTask);
      when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

      // Then
      assertThat(result).isEqualTo(taskResponse);
    }

    @Test
    void getTaskByIdWhenTaskNotFoundOrTenantWithoutAccess() throws Exception {
      // Given
      final var taskId = "2222222";
      when(taskService.getTask(taskId)).thenThrow(NotFoundException.class);

      // When
      mockMvc
          .perform(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class TaskAssignmentTests {
    @Test
    void assignTask() throws Exception {
      // Given
      final var taskId = "3333333";
      final var assignRequest =
          new TaskAssignRequest().setAssignee("demo1").setAllowOverrideAssignment(true);
      final var taskDTO =
          new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER);

      final TaskResponse expectedTaskResponse = new TaskResponse();
      expectedTaskResponse.setId("3333333");
      expectedTaskResponse.setAssignee("demo1");

      when(taskService.getTask(taskId)).thenReturn(taskDTO);
      when(taskService.assignTask(taskId, "demo1", true)).thenReturn(taskDTO);
      when(taskMapper.toTaskResponse(taskDTO)).thenReturn(expectedTaskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

      // Then
      assertThat(result).isEqualTo(expectedTaskResponse);
    }

    @Test
    void assignTaskWhenApiUserTriesToAssignTaskToEmptyUserThen400ErrorExpected() throws Exception {
      // Given
      final var taskId = "3333333";
      final var assignRequest =
          new TaskAssignRequest().setAssignee("").setAllowOverrideAssignment(true);

      // Define the expected error message
      final var expectedErrorMessage = "Assignee must be specified";

      // When
      when(taskService.assignTask(taskId, "", true))
          .thenThrow(new InvalidRequestException(expectedErrorMessage));
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));

      // Then
      mockMvc
          .perform(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().is4xxClientError())
          .andExpect(
              content()
                  .string(
                      containsString(
                          expectedErrorMessage))) // Check for the error message in the response
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    void unassignTask() throws Exception {
      // Given
      final var taskId = "44444444";
      final var taskDTO =
          new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER);

      final TaskResponse expectedTaskResponse = new TaskResponse();
      expectedTaskResponse.setId("44444444");
      expectedTaskResponse.setAssignee("");

      when(taskService.getTask(taskId)).thenReturn(taskDTO);
      when(taskService.unassignTask(taskId)).thenReturn(taskDTO);
      when(taskMapper.toTaskResponse(taskDTO)).thenReturn(expectedTaskResponse);

      // When
      final var responseAsString =
          mockMvc
              .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

      // Then
      assertThat(result).isEqualTo(expectedTaskResponse);
    }

    @Test
    void unassignTaskWithoutTenantAccess() throws Exception {
      // Given
      final var taskId = "3333333";
      when(taskService.unassignTask(taskId)).thenThrow(NotFoundException.class);
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));
      // When
      mockMvc
          .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId))
          .andDo(print())
          .andExpect(status().isNotFound());
    }

    @Test
    void assignTaskWhenRandomTaskIdUsedThen404ErrorExpected() throws Exception {
      // Given
      final var taskId = "3333333";
      final var assignRequest =
          new TaskAssignRequest().setAssignee("").setAllowOverrideAssignment(true);

      // When
      when(taskService.getTask(taskId)).thenThrow(NotFoundException.class);
      when(taskService.assignTask(taskId, "", true)).thenThrow(NotFoundException.class);

      // Then
      mockMvc
          .perform(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    void unassignTaskWhenRandomTaskIdUsedThen404ErrorExpected() throws Exception {
      // Given
      final var taskId = "3333333";

      // When
      when(taskService.getTask(taskId)).thenThrow(NotFoundException.class);
      when(taskService.unassignTask(taskId)).thenThrow(NotFoundException.class);

      // Then
      mockMvc
          .perform(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }
  }

  @Nested
  class TaskOperationTests {
    @Test
    void saveDraftTaskVariables() throws Exception {
      // Given
      final var taskId = "778800";
      final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));

      when(taskService.getTask(taskId))
          .thenReturn(
              new TaskDTO()
                  .setId(taskId)
                  .setImplementation(TaskImplementation.JOB_WORKER)
                  .setCreationTime(Instant.now().toString()));
      when(tasklistPermissionServices.hasPermissionToUpdateUserTask(any())).thenReturn(true);

      // When
      mockMvc
          .perform(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(
                      CommonUtils.OBJECT_MAPPER.writeValueAsString(
                          new SaveVariablesRequest().setVariables(variables)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isNoContent())
          .andReturn()
          .getResponse();

      // Then
      verify(variableService).persistDraftTaskVariables(taskId, variables);
    }

    @Test
    void saveDraftTaskVariablesWhenWhenInvalidJsonValueProvidedThen400ErrorExpected()
        throws Exception {
      // Given
      final var taskId = "778800";
      final var saveVariablesRequest =
          new SaveVariablesRequest()
              .setVariables(
                  List.of(
                      new VariableInputDTO()
                          .setName("invalid_variable")
                          .setValue("strWithoutQuotes")));
      when(taskService.getTask(taskId))
          .thenReturn(
              new TaskDTO()
                  .setId(taskId)
                  .setImplementation(TaskImplementation.JOB_WORKER)
                  .setCreationTime(Instant.now().toString()));
      when(tasklistPermissionServices.hasPermissionToUpdateUserTask(any())).thenReturn(true);

      // When
      doThrow(
              new InvalidRequestException(
                  "Invalid JSON value provided for variable invalid_variable"))
          .when(variableService)
          .persistDraftTaskVariables(taskId, saveVariablesRequest.getVariables());

      // Then
      mockMvc
          .perform(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(saveVariablesRequest))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andReturn()
          .getResponse();
    }

    @Test
    void saveDraftTaskVariablesWhenTaskIdDoesntExistOrTenantWithoutAccess() throws Exception {
      final var taskId = "2222222";
      mockMvc
          .perform(post(TasklistURIs.TASKS_URL_V1.concat("{taskId}/variables"), taskId))
          .andExpect(status().isNotFound());
    }

    @Test
    void assignTaskWithoutTenantAccess() throws Exception {
      // Given
      final var taskId = "3333333";
      final var assignRequest =
          new TaskAssignRequest().setAssignee("demo1").setAllowOverrideAssignment(true);

      when(taskService.assignTask(
              taskId, assignRequest.getAssignee(), assignRequest.isAllowOverrideAssignment()))
          .thenThrow(NotFoundException.class);
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));
      // When
      mockMvc
          .perform(
              patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isNotFound());
    }

    @Test
    void completeTask() throws Exception {
      // Given
      final var taskId = "55555555";
      final var taskDTO =
          new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER);
      final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
      final var completeRequest = new TaskCompleteRequest().setVariables(variables);

      final TaskResponse expectedTaskResponse = new TaskResponse();
      expectedTaskResponse.setId("44444444");
      expectedTaskResponse.setTaskState(TaskState.COMPLETED);

      when(taskService.getTask(taskId)).thenReturn(taskDTO);
      when(taskService.completeTask(taskId, variables, true)).thenReturn(taskDTO);
      when(taskMapper.toTaskResponse(taskDTO)).thenReturn(expectedTaskResponse);
      when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
      when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
      when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(false);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

      // Then
      assertThat(result).isEqualTo(expectedTaskResponse);
    }

    @Test
    void completeTaskWhenEmptyRequestBodySent() throws Exception {
      // Given
      final var taskId = "55555555";
      final var taskDTO =
          new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER);

      final TaskResponse expectedTaskResponse = new TaskResponse();
      expectedTaskResponse.setId("55555555");

      when(taskService.getTask(taskId)).thenReturn(taskDTO);
      when(taskService.completeTask(taskId, List.of(), true)).thenReturn(taskDTO);
      when(taskMapper.toTaskResponse(taskDTO)).thenReturn(expectedTaskResponse);
      when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
      when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
      when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(false);

      // When
      final var responseAsString =
          mockMvc
              .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

      // Then
      assertThat(result).isEqualTo(expectedTaskResponse);
    }

    @Test
    void completeTaskWithoutTenantAccess() throws Exception {
      final var taskId = "55555555";
      final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
      final var completeRequest = new TaskCompleteRequest().setVariables(variables);

      when(tasklistProperties.getIdentity()).thenReturn(null);
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));
      when(taskService.completeTask(taskId, variables, true)).thenThrow(NotFoundException.class);
      when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
      when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
      when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
      when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
      when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(false);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isNotFound());
    }
  }

  @Nested
  class AccessRestrictionsTests {
    @Nested
    class SearchTaskTests {
      @Test
      void accessRestrictionsShouldReturnTasksForGroupAdminOnly() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setAssignee("demo")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateGroups(new String[] {"Admins"})
                .setSortValues(new String[] {"123", "456"});
        final var taskResponse =
            new TaskSearchResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setAssignee("demo")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateGroups(new String[] {"Admins"})
                .setTaskState(TaskState.CREATED)
                .setSortValues(new String[] {"123", "456"});
        final var searchRequest =
            new TaskSearchRequest().setPageSize(20).setState(TaskState.CREATED);

        final var searchQuery = mock(TaskQueryDTO.class);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
        when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
            .thenReturn(List.of(providedTask));
        when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(
                responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

        // Then
        assertThat(result).singleElement().isEqualTo(taskResponse);
      }

      @Test
      void accessRestrictionsShouldReturnTasksForCandidateUser() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setAssignee("demo")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateUsers(new String[] {"demo"})
                .setSortValues(new String[] {"123", "456"});
        final var taskResponse =
            new TaskSearchResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setAssignee("demo")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateUsers(new String[] {"demo"})
                .setTaskState(TaskState.CREATED)
                .setSortValues(new String[] {"123", "456"});
        final var searchRequest =
            new TaskSearchRequest().setPageSize(20).setState(TaskState.CREATED);

        final var searchQuery = mock(TaskQueryDTO.class);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
        when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);
        when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
        when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
            .thenReturn(List.of(providedTask));
        when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(
                responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

        // Then
        assertThat(result).singleElement().isEqualTo(taskResponse);
      }

      @Test
      void accessRestrictionsUserIsNotInTheGroupShouldSeeTaskAssigned() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setAssignee("demo")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateUsers(new String[] {"user1", "user2"})
                .setAssignee("demo")
                .setSortValues(new String[] {"123", "456"});
        final var taskResponse =
            new TaskSearchResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setAssignee("demo")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateUsers(new String[] {"user1", "user2"})
                .setAssignee("demo")
                .setTaskState(TaskState.CREATED)
                .setSortValues(new String[] {"123", "456"});
        final var searchRequest =
            new TaskSearchRequest().setPageSize(20).setState(TaskState.CREATED);

        final var searchQuery = mock(TaskQueryDTO.class);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
        when(taskService.getTasks(searchQuery, Set.of(TASK_DESCRIPTION), false))
            .thenReturn(List.of(providedTask));
        when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(
                responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

        // Then
        assertThat(result).singleElement().isEqualTo(taskResponse);
      }

      @Test
      void accessRestrictionsUserShouldNotSeeTask() throws Exception {
        // Given
        final var searchRequest =
            new TaskSearchRequest().setPageSize(20).setState(TaskState.CREATED);

        final var searchQuery = mock(TaskQueryDTO.class);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getDisplayName()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(
                responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

        // Then
        assertThat(result).isEqualTo(emptyList());
      }
    }

    @Nested
    class CompleteTaskTests {
      @Test
      void completeTask() throws Exception {
        // Given
        final var taskId = "55555555";
        final var taskDto =
            new TaskDTO()
                .setId(taskId)
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
        final var completeRequest = new TaskCompleteRequest().setVariables(variables);

        final TaskResponse expectedTaskResponse = new TaskResponse();
        expectedTaskResponse.setId("44444444");
        expectedTaskResponse.setTaskState(TaskState.COMPLETED);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskService.getTask(taskId)).thenReturn(taskDto);
        when(taskService.completeTask(taskId, variables, true)).thenReturn(taskDto);
        when(taskMapper.toTaskResponse(taskDto)).thenReturn(expectedTaskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

        // Then
        assertThat(result).isEqualTo(expectedTaskResponse);
      }

      @Test
      void completeTaskShouldReturnForbiddenWhenUserHasNoAccess() throws Exception {
        // Given
        final var taskId = "55555555";
        final var taskDto =
            new TaskDTO()
                .setId(taskId)
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
        final var completeRequest = new TaskCompleteRequest().setVariables(variables);

        final TaskResponse expectedTaskResponse = new TaskResponse();
        expectedTaskResponse.setId("44444444");
        expectedTaskResponse.setCandidateGroups(new String[] {"Admins"});
        expectedTaskResponse.setTaskState(TaskState.COMPLETED);

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Demo"));
        when(taskService.getTask(taskId)).thenReturn(taskDto);
        when(taskService.completeTask(taskId, variables, true)).thenReturn(taskDto);
        when(taskMapper.toTaskResponse(taskDto)).thenReturn(expectedTaskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        assertThat(responseAsString).contains(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK);
      }
    }

    @Nested
    class GetSingleTaskTests {
      @Test
      void accessRestrictionsShouldReturnTaskForCandidateUser() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateUsers(new String[] {"demo"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var taskResponse =
            new TaskResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateUsers(new String[] {"demo"})
                .setTaskState(TaskState.CREATED);
        final var taskId = "111111";

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskService.getTask(taskId)).thenReturn(providedTask);
        when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

        // Then
        assertThat(result).isEqualTo(taskResponse);
      }

      @Test
      void accessRestrictionsShouldReturnTaskForCandidateGroup() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var taskResponse =
            new TaskResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateGroups(new String[] {"Admins"})
                .setTaskState(TaskState.CREATED);
        final var taskId = "111111";

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(taskService.getTask(taskId)).thenReturn(providedTask);
        when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final var result =
            CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

        // Then
        assertThat(result).isEqualTo(taskResponse);
      }

      @Test
      void accessRestrictionsShouldReturnForbiddenWhenUserHasNoAccess() throws Exception {
        // Given
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateUsers(new String[] {"admin"})
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var taskResponse =
            new TaskResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateUsers(new String[] {"admin"})
                .setCandidateGroups(new String[] {"Admins"})
                .setTaskState(TaskState.CREATED);
        final var taskId = "111111";

        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Test"));
        when(taskService.getTask(taskId)).thenReturn(providedTask);
        when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        assertThat(responseAsString).contains(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK);
      }
    }

    @Nested
    class PersistTasksVariablesTest {
      @Test
      void saveDraftTaskVariables() throws Exception {
        // Given
        final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var taskResponse =
            new TaskResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateGroups(new String[] {"Admins"})
                .setTaskState(TaskState.CREATED);
        final var taskId = "111111";

        when(taskService.getTask(taskId)).thenReturn(providedTask);
        when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);
        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Admins"));
        when(tasklistPermissionServices.hasPermissionToUpdateUserTask(any())).thenReturn(true);

        // When
        mockMvc
            .perform(
                post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(
                        CommonUtils.OBJECT_MAPPER.writeValueAsString(
                            new SaveVariablesRequest().setVariables(variables)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

        // Then
        verify(variableService).persistDraftTaskVariables(taskId, variables);
      }

      @Test
      void saveDraftTaskVariablesShouldReturnForbiddenWhenUserHasNoAccess() throws Exception {
        // Given
        final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
        final var providedTask =
            new TaskDTO()
                .setId("111111")
                .setFlowNodeBpmnId("Register the passenger")
                .setBpmnProcessId("Flight registration")
                .setCreationTime("2023-02-20T18:37:19.214+0000")
                .setTaskState(TaskState.CREATED)
                .setCandidateGroups(new String[] {"Admins"})
                .setImplementation(TaskImplementation.JOB_WORKER);
        final var taskResponse =
            new TaskResponse()
                .setId("111111")
                .setName("Register the passenger")
                .setProcessName("Flight registration")
                .setCreationDate("2023-02-20T18:37:19.214+0000")
                .setCandidateGroups(new String[] {"Admins"})
                .setTaskState(TaskState.CREATED);
        final var taskId = "111111";

        when(taskService.getTask(taskId)).thenReturn(providedTask);
        when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);
        when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
        when(tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()).thenReturn(true);
        when(userReader.getCurrentUser()).thenReturn(mock(UserDTO.class));
        when(userReader.getCurrentUser().getUserId()).thenReturn("demo");
        when(userGroupService.getUserGroups()).thenReturn(List.of("Demo"));

        // When
        final var responseAsString =
            mockMvc
                .perform(
                    post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(
                            CommonUtils.OBJECT_MAPPER.writeValueAsString(
                                new SaveVariablesRequest().setVariables(variables)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        verify(variableService, never()).persistDraftTaskVariables(taskId, variables);
        assertThat(responseAsString).contains(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK);
      }
    }
  }

  @Nested
  class SearchTaskVariables {
    @Test
    void searchTaskVariablesWithVariableNamesAndIncludeVariablesShouldReturn400() throws Exception {
      final String taskId = "taskId";
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));
      final var expectedErrorMessage =
          "Only one of [variableNames, includeVariables] must be present in request";
      mockMvc
          .perform(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                  .characterEncoding(StandardCharsets.UTF_8.name())
                  .content(
                      CommonUtils.OBJECT_MAPPER.writeValueAsString(
                          new VariablesSearchRequest()
                              .setVariableNames(List.of("varA"))
                              .setIncludeVariables(List.of(new IncludeVariable().setName("varA")))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(
              content()
                  .string(
                      containsString(
                          expectedErrorMessage))) // Check for the error message in the response
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    void searchTaskVariables() throws Exception {
      // Given
      final var taskId = "778899";
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));
      final var variableA =
          new VariableSearchResponse()
              .setId("111")
              .setName("varA")
              .setValue("925.5")
              .setPreviewValue("925.5")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5"));
      final var variableB =
          new VariableSearchResponse()
              .setId("112")
              .setName("varB")
              .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
              .setIsValueTruncated(true)
              .setPreviewValue("\"veryVeryLongValue");
      final var variableC =
          new VariableSearchResponse()
              .setId("113")
              .setName("varC")
              .setValue("\"normalValue\"")
              .setPreviewValue("\"normalValue\"")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("\"updatedVeryVeryLongValue\"")
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo"));
      final var variableNames = Set.of("varA", "varB", "varC");
      when(variableService.getVariableSearchResponses(taskId, variableNames))
          .thenReturn(List.of(variableA, variableB, variableC));

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(
                          CommonUtils.OBJECT_MAPPER.writeValueAsString(
                              new VariablesSearchRequest()
                                  .setVariableNames(new ArrayList<>(variableNames))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

      // Then
      assertThat(result)
          .extracting("name", "value", "isValueTruncated", "previewValue", "draft")
          .containsExactly(
              tuple(
                  "varA",
                  "925.5",
                  false,
                  "925.5",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5")),
              tuple("varB", null, true, "\"veryVeryLongValue", null),
              tuple(
                  "varC",
                  "\"normalValue\"",
                  false,
                  "\"normalValue\"",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo")));
    }

    @Test
    void searchTaskVariablesWithFullValues() throws Exception {
      // Given
      final var taskId = "778899";
      final var variableA =
          new VariableSearchResponse()
              .setId("111")
              .setName("varA")
              .setValue("925.5")
              .setPreviewValue("925.5")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5"));
      final var variableB =
          new VariableSearchResponse()
              .setId("112")
              .setName("varB")
              .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
              .setIsValueTruncated(true)
              .setPreviewValue("\"veryVeryLongValue");
      final var variableC =
          new VariableSearchResponse()
              .setId("113")
              .setName("varC")
              .setValue("\"normalValue\"")
              .setPreviewValue("\"normalValue\"")
              .setDraft(
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("\"updatedVeryVeryLongValue\"")
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo"));
      final var variableNames = Set.of("varA", "varB", "varC");
      when(variableService.getVariableSearchResponses(taskId, variableNames))
          .thenReturn(List.of(variableA, variableB, variableC));
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(
                          CommonUtils.OBJECT_MAPPER.writeValueAsString(
                              new VariablesSearchRequest()
                                  .setIncludeVariables(
                                      List.of(
                                          new IncludeVariable()
                                              .setName("varA")
                                              .setAlwaysReturnFullValue(true),
                                          new IncludeVariable()
                                              .setName("varB")
                                              .setAlwaysReturnFullValue(true),
                                          new IncludeVariable()
                                              .setName("varC")
                                              .setAlwaysReturnFullValue(true)))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

      // Then
      assertThat(result)
          .extracting("name", "value", "isValueTruncated", "previewValue", "draft")
          .containsExactly(
              tuple(
                  "varA",
                  "925.5",
                  false,
                  "925.5",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setValue("10000.5")
                      .setPreviewValue("10000.5")),
              tuple(
                  "varB",
                  "\"veryVeryLongValueThatExceedsVariableSizeLimit\"",
                  true,
                  "\"veryVeryLongValue",
                  null),
              tuple(
                  "varC",
                  "\"normalValue\"",
                  false,
                  "\"normalValue\"",
                  new VariableSearchResponse.DraftSearchVariableValue()
                      .setIsValueTruncated(true)
                      .setPreviewValue("\"updatedVeryVeryLo")
                      .setValue("\"updatedVeryVeryLongValue\"")));
    }

    @Test
    void searchTaskVariablesWhenRequestBodyIsEmpty() throws Exception {
      // Given
      final var taskId = "11778899";
      when(variableService.getVariableSearchResponses(taskId, emptySet())).thenReturn(emptyList());
      when(taskService.getTask(taskId))
          .thenReturn(new TaskDTO().setId(taskId).setImplementation(TaskImplementation.JOB_WORKER));

      // When
      final var responseAsString =
          mockMvc
              .perform(post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class UnsupportedZeebeUserTaskOperationsForApiCustomerTests {

    private static final String ERROR_MSG =
        "This operation is not supported using Tasklist V1 API. Please use the latest API. For more information, refer to the documentation: https://docs.camunda.tasklist";

    private final String taskId = "taskId";
    private MockedStatic<TasklistAuthenticationUtil> authenticationUtil;

    @BeforeEach
    public void setUp() {
      when(tasklistProperties.getDocumentation().getApiMigrationDocsUrl())
          .thenReturn("https://docs.camunda.tasklist");
      when(taskService.getTask(taskId))
          .thenReturn(
              new TaskDTO().setId(taskId).setImplementation(TaskImplementation.ZEEBE_USER_TASK));
      when(userReader.getCurrentUser()).thenReturn(new UserDTO());

      authenticationUtil = mockStatic(TasklistAuthenticationUtil.class);
      authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(true);
    }

    @AfterEach
    public void tearDown() {
      authenticationUtil.close();
    }

    @Test
    void completeUserTaskShouldReturnBadRequest() throws Exception {
      // Given
      final var completeRequest =
          new TaskCompleteRequest()
              .setVariables(List.of(new VariableInputDTO().setName("var_a").setValue("val_a")));

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Then
      assertThat(responseAsString).contains(ERROR_MSG);
    }

    @Test
    void assignUserTaskShouldReturnBadRequest() throws Exception {
      // When
      final var responseAsString =
          mockMvc
              .perform(
                  patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                      .characterEncoding(StandardCharsets.UTF_8.name())
                      .content(
                          CommonUtils.OBJECT_MAPPER.writeValueAsString(
                              new TaskAssignRequest()
                                  .setAssignee("demo1")
                                  .setAllowOverrideAssignment(true)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andDo(print())
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Then
      assertThat(responseAsString).contains(ERROR_MSG);
    }

    @Test
    void unassignUserTaskShouldReturnBadRequest() throws Exception {
      // When
      final var responseAsString =
          mockMvc
              .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId))
              .andDo(print())
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Then
      assertThat(responseAsString).contains(ERROR_MSG);
    }
  }
}
