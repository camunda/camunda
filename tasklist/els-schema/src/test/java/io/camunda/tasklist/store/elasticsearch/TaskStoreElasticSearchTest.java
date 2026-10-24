/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

@ExtendWith(MockitoExtension.class)
class TaskStoreElasticSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @Mock private ElasticsearchClient esClient;

  @Mock private ElasticsearchTenantHelper tenantHelper;

  @Spy private TaskTemplate taskTemplate = new TaskTemplate("test", true);

  @Spy
  private SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();

  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @Mock private VariableStore variableStoreElasticSearch;

  @Mock private TaskVariableSearchUtil taskVariableSearchUtil;

  @InjectMocks private TaskStoreElasticSearch instance;

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

    // Mock tenant helper to return query as-is
    when(tenantHelper.makeQueryTenantAware(any(Query.class))).thenAnswer(i -> i.getArgument(0));

    final SearchResponse<TaskEntity> mockedResponse = mockSearchResponse(taskState);
    when(esClient.search(searchRequestCaptor.capture(), eq(TaskEntity.class)))
        .thenReturn(mockedResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(searchRequestCaptor.getValue().index())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    verify(esClient).search(any(SearchRequest.class), eq(TaskEntity.class));
  }

  @Test
  void queryTasksWithProvidedTenantIds() throws IOException {
    final TaskQuery taskQuery =
        new TaskQuery()
            .setTenantIds(new String[] {"tenant_a", "tenant_b"})
            .setPageSize(50)
            .setState(TaskState.CREATED);

    // Mock tenant helper with specific tenant ids
    when(tenantHelper.makeQueryTenantAware(any(Query.class), eq(Set.of("tenant_a", "tenant_b"))))
        .thenAnswer(i -> i.getArgument(0));

    final SearchResponse<TaskEntity> mockedResponse = mockSearchResponse(TaskState.CREATED);
    when(esClient.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(mockedResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    verify(tenantHelper).makeQueryTenantAware(any(Query.class), eq(Set.of("tenant_a", "tenant_b")));
    assertThat(result).hasSize(1);
  }

  @Test
  void getTaskShouldWrapElasticsearchExceptionInTasklistRuntimeException() throws IOException {
    // Given a server-side ES failure (e.g. "all shards failed") surfaces as an
    // ElasticsearchException, which is a RuntimeException rather than an IOException.
    when(tenantHelper.makeQueryTenantAware(any(Query.class))).thenAnswer(i -> i.getArgument(0));
    when(esClient.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenThrow(mock(ElasticsearchException.class));

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
        TaskStoreElasticSearch.toVariablesMap(taskVariablesFilter);

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
        TaskStoreElasticSearch.toVariablesMap(taskVariablesFilter);

    assertThat(variablesMap)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("businessKey", "first-value", "otherVariable", "second-value"));
  }

  @Test
  void getTasksAppliesKeepFirstConsistentlyToActiveTaskMatchingForDuplicateFilterNames()
      throws Exception {
    // Given two "businessKey" filters with different values, and a process instance whose
    // businessKey is set to only the first value. Pre-fix, getTasksContainsVarNameAndValue
    // queried getProcessInstanceKeysWithMatchingVars with BOTH duplicate entries, intersecting
    // the per-entry matches and finding no process instance at all -- even this one, which does
    // have the first value. The dedup fix (keep first value) must apply to this branch too, not
    // only to retrieveTaskIdByProcessInstanceId. See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(TaskState.CREATED)
            .setTaskVariables(taskVariablesFilter);

    when(tenantHelper.makeQueryTenantAware(any(Query.class))).thenAnswer(i -> i.getArgument(0));

    // Only the deduplicated single-entry filter (businessKey=first-value) matches a process
    // instance; the un-deduplicated two-entry filter would find nothing.
    when(variableStoreElasticSearch.getProcessInstanceKeysWithMatchingVars(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              final List<String> names = invocation.getArgument(0);
              final List<String> values = invocation.getArgument(1);
              return names.equals(List.of("businessKey")) && values.equals(List.of("first-value"))
                  ? List.of(100L)
                  : Collections.emptyList();
            });
    when(taskVariableSearchUtil.getTaskIdsContainingVariables(anyList(), anyMap()))
        .thenReturn(List.of("task-active-1"));

    // No completed tasks match in this scenario.
    final SearchResponse<Map<String, Object>> emptyCompletedResponse = mockEmptyMapSearchResponse();
    when(esClient.search(any(SearchRequest.class), eq(ElasticsearchUtil.MAP_CLASS)))
        .thenReturn(emptyCompletedResponse);

    // Active-task lookup and the final task listing both search TaskEntity documents.
    final SearchResponse<TaskEntity> taskEntityResponse = mockSearchResponse(TaskState.CREATED);
    when(esClient.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(taskEntityResponse);
    final ScrollResponse<TaskEntity> emptyTaskEntityScroll = mockEmptyScrollResponse();
    when(esClient.scroll(any(Function.class), eq(TaskEntity.class)))
        .thenReturn(emptyTaskEntityScroll);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(result).hasSize(1);
  }

  @Test
  void getTasksAppliesKeepFirstConsistentlyToCompletedTaskMatchingForDuplicateFilterNames()
      throws Exception {
    // Given two "businessKey" filters with different values, and a completed task whose only
    // recorded businessKey is the first value. Pre-fix, getTasksIdsCompletedWithMatchingVars ran
    // once per duplicate filter entry and intersected the per-entry task-id sets, so this task
    // would incorrectly disappear instead of being kept per the documented keep-first behavior.
    // See #63182.
    final TaskByVariables[] taskVariablesFilter = {
      new TaskByVariables().setName("businessKey").setValue("first-value").setOperator("eq"),
      new TaskByVariables().setName("businessKey").setValue("second-value").setOperator("eq")
    };
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(TaskState.COMPLETED)
            .setTaskVariables(taskVariablesFilter);

    when(tenantHelper.makeQueryTenantAware(any(Query.class))).thenAnswer(i -> i.getArgument(0));

    // No active (created) process instances match in this scenario.
    when(variableStoreElasticSearch.getProcessInstanceKeysWithMatchingVars(anyList(), anyList()))
        .thenReturn(Collections.emptyList());
    when(taskVariableSearchUtil.getTaskIdsContainingVariables(anyList(), anyMap()))
        .thenReturn(Collections.emptyList());

    final SearchResponse<Map<String, Object>> completedMapResponse =
        mockMapSearchResponse("task-completed-1");
    when(esClient.search(any(SearchRequest.class), eq(ElasticsearchUtil.MAP_CLASS)))
        .thenReturn(completedMapResponse);
    final ScrollResponse<Map<String, Object>> emptyMapScroll = mockEmptyScrollResponse();
    when(esClient.scroll(any(Function.class), eq(ElasticsearchUtil.MAP_CLASS)))
        .thenReturn(emptyMapScroll);

    final SearchResponse<TaskEntity> completedTaskEntityResponse =
        mockSearchResponse(TaskState.COMPLETED);
    when(esClient.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(completedTaskEntityResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then the completed-task lookup ran exactly once, for the deduplicated single filter entry
    // -- not once per duplicate filter entry.
    verify(esClient, times(1)).search(any(SearchRequest.class), eq(ElasticsearchUtil.MAP_CLASS));
    assertThat(result).hasSize(1);
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<Map<String, Object>> mockMapSearchResponse(final String taskId) {
    final SearchResponse<Map<String, Object>> response = mock(SearchResponse.class);
    final HitsMetadata<Map<String, Object>> hitsMetadata = mock(HitsMetadata.class);
    final Hit<Map<String, Object>> hit = mock(Hit.class);
    when(hit.source()).thenReturn(Map.of(SnapshotTaskVariableTemplate.TASK_ID, taskId));
    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    return response;
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<Map<String, Object>> mockEmptyMapSearchResponse() {
    final SearchResponse<Map<String, Object>> response = mock(SearchResponse.class);
    final HitsMetadata<Map<String, Object>> hitsMetadata = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(List.of());
    return response;
  }

  @SuppressWarnings("unchecked")
  private <T> ScrollResponse<T> mockEmptyScrollResponse() {
    final ScrollResponse<T> response = mock(ScrollResponse.class);
    final HitsMetadata<T> hitsMetadata = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(List.of());
    return response;
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<TaskEntity> mockSearchResponse(final TaskState taskState) {
    final SearchResponse<TaskEntity> mockedResponse = mock(SearchResponse.class);
    final HitsMetadata<TaskEntity> mockedHits = mock(HitsMetadata.class);
    final Hit<TaskEntity> mockedHit = mock(Hit.class);

    when(mockedResponse.hits()).thenReturn(mockedHits);
    when(mockedHits.hits()).thenReturn(List.of(mockedHit));
    when(mockedHit.source()).thenReturn(createTaskEntity(taskState));
    when(mockedHit.sort()).thenReturn(List.of());

    return mockedResponse;
  }

  private TaskEntity createTaskEntity(final TaskState taskState) {
    final TaskEntity entity = new TaskEntity();
    entity.setId("123456789");
    entity.setKey(123456789L);
    entity.setPartitionId(2);
    entity.setBpmnProcessId("bigFormProcess");
    entity.setProcessDefinitionId("00000000000");
    entity.setFlowNodeBpmnId("Activity_0aaaaa");
    entity.setFlowNodeInstanceId("11111111111");
    entity.setProcessInstanceId("2222222222");
    entity.setState(taskState);
    entity.setFormKey("camunda-forms:bpmn:userTaskForm_1111111");
    entity.setImplementation(TaskImplementation.JOB_WORKER);
    return entity;
  }
}
