/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskMapperTest {

  @Mock private ProcessCache processCache;
  @InjectMocks private TaskMapper instance;

  private static Stream<Arguments> toTaskSearchResponseTestData() {
    return Stream.of(
        Arguments.of(
            "Register the passenger1", "Register the passenger2",
            "Flight registration1", "Flight registration2",
            "Register the passenger1", "Flight registration1"),
        Arguments.of(
            "Register the passenger1",
            "Register the passenger2",
            null,
            "Flight registration2",
            "Register the passenger1",
            "Flight registration2"),
        Arguments.of(
            null,
            "Register the passenger2",
            "Flight registration1",
            "Flight registration2",
            "Register the passenger2",
            "Flight registration1"),
        Arguments.of(
            null,
            "Register the passenger2",
            null,
            "Flight registration2",
            "Register the passenger2",
            "Flight registration2"));
  }

  @ParameterizedTest
  @MethodSource("toTaskSearchResponseTestData")
  void toTaskSearchResponse(
      String cachedTaskName,
      String flowNodeBpmnId,
      String cachedProcessName,
      String bpmnProcessId,
      String name,
      String processName) {
    // Given
    final String processDefinitionId = "2251799813685257";
    final OffsetDateTime dueDate = OffsetDateTime.now().plusDays(3);
    final OffsetDateTime followUpDate = OffsetDateTime.now();
    final var providedTask =
        new TaskDTO()
            .setId("111111")
            .setFlowNodeBpmnId(flowNodeBpmnId)
            .setBpmnProcessId(bpmnProcessId)
            .setAssignee("demo")
            .setCreationTime("2023-02-20T18:37:19.214+0000")
            .setCompletionTime("2023-02-21T18:49:19.214+0000")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"})
            .setIsFirst(true)
            .setFormKey("camunda-forms:bpmn:userTaskForm")
            .setProcessDefinitionId(processDefinitionId)
            .setProcessInstanceId("1000001")
            .setDueDate(dueDate)
            .setFollowUpDate(followUpDate)
            .setCandidateGroups(new String[] {"group1"})
            .setCandidateUsers(new String[] {"users1"});
    final var expectedResponse =
        new TaskSearchResponse()
            .setId("111111")
            .setName(name)
            .setTaskDefinitionId(flowNodeBpmnId)
            .setProcessName(processName)
            .setCreationDate("2023-02-20T18:37:19.214+0000")
            .setCompletionDate("2023-02-21T18:49:19.214+0000")
            .setAssignee("demo")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"})
            .setIsFirst(true)
            .setFormKey("camunda-forms:bpmn:userTaskForm")
            .setProcessDefinitionKey(processDefinitionId)
            .setProcessInstanceKey("1000001")
            .setDueDate(dueDate)
            .setFollowUpDate(followUpDate)
            .setCandidateGroups(new String[] {"group1"})
            .setCandidateUsers(new String[] {"users1"});
    when(processCache.getTaskName(processDefinitionId, flowNodeBpmnId)).thenReturn(cachedTaskName);
    when(processCache.getProcessName(processDefinitionId)).thenReturn(cachedProcessName);

    // When
    final var result = instance.toTaskSearchResponse(providedTask);

    // Then
    assertThat(result).isEqualTo(expectedResponse);
  }

  @Test
  void toTaskQuery() {
    // Given
    final var providedSearchRequest =
        new TaskSearchRequest()
            .setState(TaskState.COMPLETED)
            .setAssigned(true)
            .setAssignee("test")
            .setTaskDefinitionId("taskDefId")
            .setCandidateGroup("candidate")
            .setProcessDefinitionKey("pdk")
            .setProcessInstanceKey("pik")
            .setSearchBefore(new String[] {"1"})
            .setSearchBeforeOrEqual(new String[] {"2"})
            .setSearchAfter(new String[] {"3"})
            .setSearchAfterOrEqual(new String[] {"4"});
    final var expectedTaskQuery =
        new TaskQueryDTO()
            .setState(TaskState.COMPLETED)
            .setAssigned(true)
            .setAssignee("test")
            .setTaskDefinitionId("taskDefId")
            .setCandidateGroup("candidate")
            .setProcessDefinitionId("pdk")
            .setProcessInstanceId("pik")
            .setPageSize(TaskQueryDTO.DEFAULT_PAGE_SIZE)
            .setSearchBefore(new String[] {"1"})
            .setSearchBeforeOrEqual(new String[] {"2"})
            .setSearchAfter(new String[] {"3"})
            .setSearchAfterOrEqual(new String[] {"4"});

    // When
    final var result = instance.toTaskQuery(providedSearchRequest);

    // Then
    assertThat(result).isEqualTo(expectedTaskQuery);
  }
}
