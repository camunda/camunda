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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskStoreElasticSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @Mock private RestHighLevelClient defaultClient;

  @Mock private TenantAwareElasticsearchClient tenantAwareClient;

  @Spy private TaskTemplate taskTemplate = new TaskTemplate("test", true);

  @Mock private VariableStore variableStoreElasticSearch;

  @Mock private TaskVariableSearchUtil taskVariableSearchUtil;

  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @Spy
  private SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();

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

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.search(searchRequestCaptor.capture())).thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(taskState));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(searchRequestCaptor.getValue().indices())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    verify(tenantAwareClient).search(any());
  }

  @Test
  void queryTasksWithProvidedTenantIds() throws IOException {
    final TaskQuery taskQuery =
        new TaskQuery()
            .setTenantIds(new String[] {"tenant_a", "tenant_b"})
            .setPageSize(50)
            .setState(TaskState.CREATED);

    final SearchResponse mockedResponse = mock();
    when(tenantAwareClient.searchByTenantIds(any(), eq(Set.of("tenant_a", "tenant_b"))))
        .thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(TaskState.CREATED));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    verify(tenantAwareClient, never()).search(any());
    assertThat(result).hasSize(1);
  }

  private static String getTaskExampleAsString(final TaskState taskState) {
    return "{\n"
        + "  \"id\": \"123456789\",\n"
        + "  \"key\": 123456789,\n"
        + "  \"partitionId\": 2,\n"
        + "  \"bpmnProcessId\": \"bigFormProcess\",\n"
        + "  \"processDefinitionId\": \"00000000000\",\n"
        + "  \"flowNodeBpmnId\": \"Activity_0aaaaa\",\n"
        + "  \"flowNodeInstanceId\": \"11111111111\",\n"
        + "  \"processInstanceId\": \"2222222222\",\n"
        + "  \"creationTime\": \"2023-01-01T00:00:02.523+0200\",\n"
        + "  \"completionTime\": null,\n"
        + "  \"state\": \""
        + taskState.toString()
        + "\",\n"
        + "  \"assignee\": null,\n"
        + "  \"candidateGroups\": null,\n"
        + "  \"formKey\": \"camunda-forms:bpmn:userTaskForm_1111111\",\n"
        + "  \"implementation\": \"JOB_WORKER\"\n"
        + "}";
  }

  @Test
  void getTaskShouldWrapElasticsearchExceptionInTasklistRuntimeException() throws IOException {
    // Given a server-side ES failure (e.g. "all shards failed") surfaces as an
    // ElasticsearchException, which is a RuntimeException rather than an IOException.
    when(tenantAwareClient.search(any(SearchRequest.class)))
        .thenThrow(mock(ElasticsearchException.class));

    // When / Then it must be mapped to a TasklistRuntimeException (handled at WARN) instead of
    // leaking to the generic exception handler (logged at ERROR). See issue #35823.
    assertThatThrownBy(() -> instance.getTask("123456789"))
        .isInstanceOf(TasklistRuntimeException.class);
  }

  @Test
  void searchWithTaskVariablesAndCreatedStateShouldNotQueryCompletedTaskVariables()
      throws IOException {
    // Given a CREATED search, which is restricted to runtime indices, so snapshot variables of
    // completed tasks can never contribute to the result
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(TaskState.CREATED)
            .setTaskVariables(new TaskByVariables[] {taskVariableFilter()});

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(result).isEmpty();
    verify(defaultClient, never()).search(any(SearchRequest.class), any());
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = TaskState.class, names = "CREATED", mode = Mode.EXCLUDE)
  void searchWithTaskVariablesShouldScrollCompletedTaskVariablesUsingBatchSize(
      final TaskState taskState) throws IOException {
    // Given
    final int batchSize = 321;
    tasklistProperties.getElasticsearch().setBatchSize(batchSize);
    final TaskQuery taskQuery =
        new TaskQuery()
            .setPageSize(50)
            .setState(taskState)
            .setTaskVariables(new TaskByVariables[] {taskVariableFilter()});

    final SearchResponse emptyResponse = mock(SearchResponse.class);
    final var emptyHits = mock(SearchHits.class);
    when(emptyResponse.getHits()).thenReturn(emptyHits);
    when(emptyHits.getHits()).thenReturn(new SearchHit[0]);
    when(defaultClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(emptyResponse);

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(result).isEmpty();
    final SearchRequest searchRequest = searchRequestCaptor.getValue();
    assertThat(searchRequest.indices()).containsExactly(taskVariableTemplate.getAlias());
    assertThat(searchRequest.source().size()).isEqualTo(batchSize);
  }

  private static TaskByVariables taskVariableFilter() {
    return new TaskByVariables().setName("varName").setValue("\"varValue\"").setOperator("eq");
  }

  @SuppressWarnings("unchecked")
  private SearchResponse mockSearchResponse(final TaskState taskState) {
    final SearchResponse mockedResponse = mock(SearchResponse.class);
    final SearchHits mockedHits = mock(SearchHits.class);
    final SearchHit mockedHit = mock(SearchHit.class);

    when(mockedResponse.getHits()).thenReturn(mockedHits);
    when(mockedHits.getHits()).thenReturn(new SearchHit[] {mockedHit});
    when(mockedHit.getSourceAsMap())
        .thenReturn(objectMapper.convertValue(createTaskEntity(taskState), Map.class));
    when(mockedHit.getSortValues()).thenReturn(new Object[0]);

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
