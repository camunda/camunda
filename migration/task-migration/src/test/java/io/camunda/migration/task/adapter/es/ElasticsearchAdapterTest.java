/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.task.adapter.TaskEntityPair;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchAdapterTest {

  private static final int BATCH_SIZE = 15;
  private static final String INDEX_PREFIX = "test";
  @Mock private ElasticsearchClient client;

  private final MigrationConfiguration migrationConfiguration = new MigrationConfiguration();
  private final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
  private final RetentionConfiguration retentionConfiguration = new RetentionConfiguration();
  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;
  private ElasticsearchAdapter adapter;

  @BeforeEach
  void setUp() {
    connectConfiguration.setIndexPrefix(INDEX_PREFIX);
    migrationConfiguration.setBatchSize(BATCH_SIZE);
    adapter =
        new ElasticsearchAdapter(
            migrationConfiguration, connectConfiguration, retentionConfiguration, client);
  }

  @Test
  void nextBatchWithNullLastMigratedTaskIdShouldQueryFromBeginning() throws Exception {
    // Given

    final var destinationResponse = mockSearchResponse(List.of(createTaskHit("1", 1L, "index-1")));
    final var sourceResponse = mockSearchResponse(List.of(createTaskHit("1", 1L, "legacy-index")));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    final List<TaskEntityPair> result = adapter.nextBatch(null);

    // Then - Verify TWO queries were made
    verify(client, times(2)).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
    final List<SearchRequest> allRequests = searchRequestCaptor.getAllValues();
    assertThat(allRequests).hasSize(2);

    // First request: Query the DESTINATION/NEW index (with _alias suffix)
    final SearchRequest destinationRequest = allRequests.get(0);
    assertThat(destinationRequest.index())
        .containsExactly(INDEX_PREFIX + "-tasklist-task-8.8.0_alias");
    assertThat(destinationRequest.size()).isEqualTo(BATCH_SIZE);

    // Verify sort order on destination query
    assertThat(destinationRequest.sort()).hasSize(1);
    assertThat(destinationRequest.sort().get(0).field().field()).isEqualTo(TaskTemplate.KEY);
    assertThat(destinationRequest.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);

    // Verify query conditions on destination query
    final BoolQuery boolQuery = destinationRequest.query().bool();
    assertThat(boolQuery.must()).hasSize(2);
    assertThat(boolQuery.mustNot()).hasSize(1);

    // Second request: Query the LEGACY/SOURCE index (without _alias suffix) - version 8.5.0
    final SearchRequest sourceRequest = allRequests.get(1);
    assertThat(sourceRequest.index()).containsExactly(INDEX_PREFIX + "-tasklist-task-8.5.0_");
    assertThat(sourceRequest.query().terms().field()).isEqualTo(TaskTemplate.KEY);

    // Verify results
    assertThat(result).hasSize(1);
    assertThat(result.get(0).target().task().getId()).isEqualTo("1");
    assertThat(result.get(0).source().getKey()).isEqualTo(1L);
  }

  @Test
  void nextBatchWithNonNullLastMigratedTaskIdShouldQueryFromLastId() throws Exception {
    // Given

    final String lastTaskId = "100";
    final var destinationResponse =
        mockSearchResponse(List.of(createTaskHit("101", 101L, "index-1")));
    final var sourceResponse =
        mockSearchResponse(List.of(createTaskHit("101", 101L, "legacy-index")));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    final List<TaskEntityPair> result = adapter.nextBatch(lastTaskId);

    // Then - Verify TWO queries were made
    verify(client, times(2)).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
    final List<SearchRequest> allRequests = searchRequestCaptor.getAllValues();

    // First request: Query the DESTINATION index with range filter
    final SearchRequest destinationRequest = allRequests.get(0);
    assertThat(destinationRequest.index())
        .containsExactly(INDEX_PREFIX + "-tasklist-task-8.8.0_alias");

    // Verify query includes range condition with lastTaskId
    final BoolQuery boolQuery = destinationRequest.query().bool();
    assertThat(boolQuery.must()).hasSize(2);

    // Verify range query uses correct ID field (should filter by ID > lastTaskId)
    assertThat(boolQuery.must().get(0).range().term().field()).isEqualTo("id");

    // Second request: Query the LEGACY index - version 8.5.0
    final SearchRequest sourceRequest = allRequests.get(1);
    assertThat(sourceRequest.index()).containsExactly(INDEX_PREFIX + "-tasklist-task-8.5.0_");

    assertThat(result).hasSize(1);
  }

  @Test
  void nextBatchWithEmptyResultsShouldReturnEmptyList() throws Exception {
    // Given

    final var emptyResponse = mockSearchResponse(List.of());

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class))).thenReturn(emptyResponse);

    // When
    final List<TaskEntityPair> result = adapter.nextBatch(null);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void nextBatchWithMatchingSourceTasksShouldReturnTaskEntityPairs() throws Exception {
    // Given

    final var task1 = createTaskHit("1", 1L, "index-1");
    final var task2 = createTaskHit("2", 2L, "index-2");
    final var destinationResponse = mockSearchResponse(List.of(task1, task2));

    final var sourceTask1 = createTaskHit("1", 1L, "legacy-index");
    final var sourceTask2 = createTaskHit("2", 2L, "legacy-index");
    final var sourceResponse = mockSearchResponse(List.of(sourceTask1, sourceTask2));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    final List<TaskEntityPair> result = adapter.nextBatch(null);

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).source().getKey()).isEqualTo(1L);
    assertThat(result.get(0).target().task().getKey()).isEqualTo(1L);
    assertThat(result.get(1).source().getKey()).isEqualTo(2L);
    assertThat(result.get(1).target().task().getKey()).isEqualTo(2L);
  }

  @Test
  void nextBatchWithMissingOriginalTaskShouldLogErrorAndExcludeFromResults() throws Exception {
    // Given

    final var task1 = createTaskHit("1", 1L, "index-1");
    final var task2 = createTaskHit("2", 2L, "index-2");
    final var destinationResponse = mockSearchResponse(List.of(task1, task2));

    // Source response only has task1, task2 is missing
    final var sourceTask1 = createTaskHit("1", 1L, "legacy-index");
    final var sourceResponse = mockSearchResponse(List.of(sourceTask1));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    final List<TaskEntityPair> result = adapter.nextBatch(null);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).source().getKey()).isEqualTo(1L);
    assertThat(result.get(0).target().task().getKey()).isEqualTo(1L);
  }

  @Test
  void nextBatchShouldQueryWithCorrectFields() throws Exception {
    // Given

    final var destinationResponse = mockSearchResponse(List.of(createTaskHit("1", 1L, "index-1")));
    final var sourceResponse = mockSearchResponse(List.of(createTaskHit("1", 1L, "legacy-index")));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    adapter.nextBatch("0");

    // Then - Verify TWO queries were made
    verify(client, times(2)).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
    final List<SearchRequest> allRequests = searchRequestCaptor.getAllValues();

    // Verify FIRST query (DESTINATION index) has correct fields
    final SearchRequest destinationRequest = allRequests.get(0);
    final BoolQuery boolQuery = destinationRequest.query().bool();

    // Verify must conditions
    assertThat(boolQuery.must()).hasSize(2);
    // First must: range query on 'id' field
    assertThat(boolQuery.must().get(0).range().term().field()).isEqualTo("id");

    // Second must: term query on 'join' field with TASK type
    assertThat(boolQuery.must().get(1).term().field()).isEqualTo("join");
    assertThat(boolQuery.must().get(1).term().value().stringValue())
        .isEqualTo(TaskJoinRelationshipType.TASK.getType());

    // Verify mustNot condition
    assertThat(boolQuery.mustNot()).hasSize(1);
    // mustNot: exists query on 'creationTime' field
    assertThat(boolQuery.mustNot().get(0).exists().field()).isEqualTo("creationTime");

    // Verify SECOND query (SOURCE/LEGACY index) queries by task keys - version 8.5.0
    final SearchRequest sourceRequest = allRequests.get(1);
    assertThat(sourceRequest.index()).containsExactly(INDEX_PREFIX + "-tasklist-task-8.5.0_");
    assertThat(sourceRequest.query().terms().field()).isEqualTo(TaskTemplate.KEY);
  }

  @Test
  void nextBatchOnIOExceptionShouldThrowMigrationException() throws Exception {
    // Given

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenThrow(new IOException("Connection failed"));

    // When/Then
    assertThatThrownBy(() -> adapter.nextBatch(null))
        .isInstanceOf(MigrationException.class)
        .hasMessageContaining("Failed to fetch next task batch");
  }

  @Test
  void nextBatchOnSourceIndexExceptionShouldThrowMigrationException() throws Exception {
    // Given

    final var destinationResponse = mockSearchResponse(List.of(createTaskHit("1", 1L, "index-1")));

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenThrow(new IOException("Legacy index not found"));

    // When/Then
    assertThatThrownBy(() -> adapter.nextBatch(null))
        .isInstanceOf(MigrationException.class)
        .hasMessageContaining("Failed to fetch original tasks for the batch update");
  }

  @Test
  void nextBatchShouldQuerySourceIndexWithCorrectTaskKeys() throws Exception {
    // Given - Create 12 tasks to exceed the default ES search size of 10
    final var destinationHits =
        List.of(
            createTaskHit("1", 101L, "index-1"),
            createTaskHit("2", 102L, "index-1"),
            createTaskHit("3", 103L, "index-1"),
            createTaskHit("4", 104L, "index-1"),
            createTaskHit("5", 105L, "index-1"),
            createTaskHit("6", 106L, "index-1"),
            createTaskHit("7", 107L, "index-1"),
            createTaskHit("8", 108L, "index-1"),
            createTaskHit("9", 109L, "index-1"),
            createTaskHit("10", 110L, "index-1"),
            createTaskHit("11", 111L, "index-1"),
            createTaskHit("12", 112L, "index-1"));
    final var destinationResponse = mockSearchResponse(destinationHits);

    final var sourceHits =
        List.of(
            createTaskHit("1", 101L, "legacy-index"),
            createTaskHit("2", 102L, "legacy-index"),
            createTaskHit("3", 103L, "legacy-index"),
            createTaskHit("4", 104L, "legacy-index"),
            createTaskHit("5", 105L, "legacy-index"),
            createTaskHit("6", 106L, "legacy-index"),
            createTaskHit("7", 107L, "legacy-index"),
            createTaskHit("8", 108L, "legacy-index"),
            createTaskHit("9", 109L, "legacy-index"),
            createTaskHit("10", 110L, "legacy-index"),
            createTaskHit("11", 111L, "legacy-index"),
            createTaskHit("12", 112L, "legacy-index"));
    final var sourceResponse = mockSearchResponse(sourceHits);

    when(client.search(any(SearchRequest.class), eq(TaskEntity.class)))
        .thenReturn(destinationResponse)
        .thenReturn(sourceResponse);

    // When
    adapter.nextBatch(null);

    // Then - Verify TWO queries were made
    verify(client, times(2)).search(searchRequestCaptor.capture(), eq(TaskEntity.class));
    final List<SearchRequest> allRequests = searchRequestCaptor.getAllValues();
    assertThat(allRequests).hasSize(2);

    // Verify source index request - version 8.5.0
    final SearchRequest sourceRequest = allRequests.get(1);
    assertThat(sourceRequest.index()).containsExactly(INDEX_PREFIX + "-tasklist-task-8.5.0_");
    assertThat(sourceRequest.query().terms().field()).isEqualTo(TaskTemplate.KEY);
    // Size should match the number of unique task keys from the destination query (12 tasks)
    // This ensures we exceed the default ES search size of 10
    assertThat(sourceRequest.size()).isEqualTo(12);
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<TaskEntity> mockSearchResponse(final List<Hit<TaskEntity>> hits) {
    final SearchResponse<TaskEntity> response = mock(SearchResponse.class);
    final HitsMetadata<TaskEntity> hitsMetadata = mock(HitsMetadata.class);

    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(hits);
    when(response.timedOut()).thenReturn(false);
    when(response.terminatedEarly()).thenReturn(false);

    return response;
  }

  @SuppressWarnings("unchecked")
  private Hit<TaskEntity> createTaskHit(final String id, final Long key, final String index) {
    final Hit<TaskEntity> hit = mock(Hit.class);
    final TaskEntity task = new TaskEntity();
    task.setId(id);
    task.setKey(key);
    task.setFlowNodeBpmnId("taskNode");
    task.setProcessInstanceId("pi-123");

    when(hit.source()).thenReturn(task);
    when(hit.index()).thenReturn(index);

    return hit;
  }
}
