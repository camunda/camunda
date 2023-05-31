/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariablesSearchRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

  private MockMvc mockMvc;

  @Mock private TaskService taskService;
  @Mock private VariableService variableService;
  @Mock private TaskMapper taskMapper;
  @InjectMocks private TaskController instance;

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

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
    when(taskService.getTasks(searchQuery, List.of())).thenReturn(List.of(providedTask));
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
    when(taskService.getTasks(searchQuery, List.of())).thenReturn(List.of(providedTask));
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
            .setTaskState(TaskState.CREATED);
    final var taskResponse =
        new TaskResponse()
            .setId(taskId)
            .setName("Register the passenger")
            .setProcessName("Flight registration")
            .setAssignee("demo")
            .setCreationDate("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED);
    when(taskService.getTask(taskId, List.of())).thenReturn(providedTask);
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
  void assignTask() throws Exception {
    // Given
    final var taskId = "3333333";
    final var assignRequest =
        new TaskAssignRequest().setAssignee("demo1").setAllowOverrideAssignment(true);
    final var mockedTask = mock(TaskDTO.class);
    final var mappedTask = mock(TaskResponse.class);

    when(taskService.assignTask(taskId, "demo1", true)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(mappedTask);

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
    assertThat(result).isEqualTo(mappedTask);
  }

  @Test
  void unassignTask() throws Exception {
    // Given
    final var taskId = "44444444";
    final var mockedTask = mock(TaskDTO.class);
    final var mappedTask = mock(TaskResponse.class);

    when(taskService.unassignTask(taskId)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(mappedTask);

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
    assertThat(result).isEqualTo(mappedTask);
  }

  @Test
  void completeTask() throws Exception {
    // Given
    final var taskId = "55555555";
    final var mockedTask = mock(TaskDTO.class);
    final var mappedTask = mock(TaskResponse.class);
    final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
    final var completeRequest = new TaskCompleteRequest().setVariables(variables);
    when(taskService.completeTask(taskId, variables)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(mappedTask);

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
    assertThat(result).isEqualTo(mappedTask);
  }

  @Test
  void completeTaskWhenEmptyRequestBodySent() throws Exception {
    // Given
    final var taskId = "55555555";
    final var mockedTask = mock(TaskDTO.class);
    final var mappedTask = mock(TaskResponse.class);
    when(taskService.completeTask(taskId, List.of())).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(mappedTask);

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
    assertThat(result).isEqualTo(mappedTask);
  }

  @Test
  void searchTaskVariables() throws Exception {
    // Given
    final var taskId = "778899";
    final var variableA =
        new VariableDTO().setId("111").setName("varA").setValue("925.5").setPreviewValue("925.5");
    final var variableB =
        new VariableDTO()
            .setId("112")
            .setName("varB")
            .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
            .setIsValueTruncated(true)
            .setPreviewValue("\"veryVeryLongValue");
    final var variableNames = List.of("varA", "varB", "varC");
    when(variableService.getVariables(taskId, variableNames, Collections.emptySet()))
        .thenReturn(List.of(variableA, variableB));

    // When
    final var responseAsString =
        mockMvc
            .perform(
                post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(
                        CommonUtils.OBJECT_MAPPER.writeValueAsString(
                            new VariablesSearchRequest().setVariableNames(variableNames)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<VariableDTO>>() {});

    // Then
    assertThat(result)
        .extracting("name", "value", "isValueTruncated", "previewValue")
        .containsExactly(
            tuple("varA", "925.5", false, "925.5"),
            tuple("varB", null, true, "\"veryVeryLongValue"));
  }

  @Test
  void searchTaskVariablesWhenRequestBodyIsEmpty() throws Exception {
    // Given
    final var taskId = "11778899";
    when(variableService.getVariables(taskId, Collections.emptyList(), Collections.emptySet()))
        .thenReturn(Collections.emptyList());

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
            responseAsString, new TypeReference<List<VariableDTO>>() {});

    // Then
    assertThat(result).isEmpty();
  }
}
