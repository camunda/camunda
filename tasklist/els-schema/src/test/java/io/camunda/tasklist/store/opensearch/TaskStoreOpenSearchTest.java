/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

@ExtendWith(MockitoExtension.class)
public class TaskStoreOpenSearchTest {

  @Captor private ArgumentCaptor<SearchRequest.Builder> searchRequestCaptor;

  @Mock private TenantAwareOpenSearchClient tenantAwareClient;

  @Spy private final TaskTemplate taskTemplate = new TaskTemplate("test", true);

  @InjectMocks private TaskStoreOpenSearch instance;

  @ParameterizedTest
  @CsvSource({
    "CREATED,test-tasklist-task-,_",
    "COMPLETED,test-tasklist-task-,_alias",
    "CANCELED,test-tasklist-task-,_alias"
  })
  void getTasksForDifferentStates(
      final TaskState taskState, final String expectedIndexPrefix, final String expectedIndexSuffix)
      throws Exception {
    // Given
    final TaskQuery taskQuery = new TaskQuery().setPageSize(50).setState(taskState);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.search(searchRequestCaptor.capture(), any(Class.class)))
        .thenReturn(mockedResponse);

    final Hit mockedHit = mock();
    when(mockedHit.source()).thenReturn(getTaskEntity(taskState));

    final HitsMetadata mockedHits = mock();
    when(mockedResponse.hits()).thenReturn(mockedHits);
    when(mockedHits.hits()).thenReturn(List.of(mockedHit));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    final SearchRequest searchRequest = searchRequestCaptor.getValue().build();
    assertThat(searchRequest.index())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    verify(tenantAwareClient).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
  }

  @Test
  void queryTasksWithProvidedTenantIds() throws IOException, IOException {
    final TaskQuery taskQuery =
        new TaskQuery()
            .setTenantIds(new String[] {"tenant_a", "tenant_b"})
            .setPageSize(50)
            .setState(TaskState.CREATED);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.searchByTenantIds(
            any(), eq(TaskEntity.class), eq(Set.of("tenant_a", "tenant_b"))))
        .thenReturn(mockedResponse);

    final Hit mockedHit = mock();
    when(mockedHit.source()).thenReturn(getTaskEntity(TaskState.CREATED));

    final HitsMetadata mockedHits = mock();
    when(mockedHits.hits()).thenReturn(List.of(mockedHit));
    when(mockedResponse.hits()).thenReturn(mockedHits);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    verify(tenantAwareClient, never()).search(any(), any(Class.class));
    assertThat(result).hasSize(1);
  }

  @Test
  void getTaskShouldWrapOpenSearchExceptionInTasklistRuntimeException() throws IOException {
    // Given a server-side OS failure (e.g. "all shards failed") surfaces as an
    // OpenSearchException, which is a RuntimeException rather than an IOException.
    when(tenantAwareClient.search(any(), eq(TaskEntity.class)))
        .thenThrow(mock(OpenSearchException.class));

    // When / Then it must be mapped to a TasklistRuntimeException (handled at WARN) instead of
    // leaking to the generic exception handler (logged at ERROR). See issue #35823.
    assertThatThrownBy(() -> instance.getTask("123456789"))
        .isInstanceOf(TasklistRuntimeException.class);
  }

  @Test
  void toVariablesMapKeepsFirstValueOnDuplicateFilterName() {
    // Given two filter entries for the same variable name (e.g. "businessKey") with
    // different values -- this used to throw IllegalStateException from the unguarded
    // Collectors.toMap() and crash the whole task search request. See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };

    // When
    final Map<String, String> variablesMap =
        TaskStoreOpenSearch.toVariablesMap(taskVariablesFilter);

    // Then it does not throw and keeps the first value for the repeated name
    assertThat(variablesMap)
        .containsExactlyInAnyOrderEntriesOf(Map.of("businessKey", "first-value"));
  }

  @Test
  void toVariablesMapKeepsAllValuesForUniqueFilterNames() {
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("otherVariable").setValue("second-value").setOperator("eq")
    };

    final Map<String, String> variablesMap =
        TaskStoreOpenSearch.toVariablesMap(taskVariablesFilter);

    assertThat(variablesMap)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("businessKey", "first-value", "otherVariable", "second-value"));
  }

  private static TaskEntity getTaskEntity(final TaskState taskState) {
    final TaskEntity taskEntity = new TaskEntity();
    taskEntity.setId("123456789");
    taskEntity.setKey(123456789L);
    taskEntity.setPartitionId(2);
    taskEntity.setBpmnProcessId("bigFormProcess");
    taskEntity.setProcessDefinitionId("00000000000");
    taskEntity.setFlowNodeBpmnId("Activity_0aaaaa");
    taskEntity.setFlowNodeInstanceId("11111111111");
    taskEntity.setProcessInstanceId("2222222222");
    taskEntity.setCreationTime(OffsetDateTime.parse("2023-01-01T00:00:02.523+02:00"));
    taskEntity.setCompletionTime(null);
    taskEntity.setState(taskState);
    taskEntity.setAssignee(null);
    taskEntity.setCandidateGroups(null);
    taskEntity.setFormKey("camunda-forms:bpmn:userTaskForm_1111111");
    taskEntity.setImplementation(TaskImplementation.JOB_WORKER);
    return taskEntity;
  }
}
