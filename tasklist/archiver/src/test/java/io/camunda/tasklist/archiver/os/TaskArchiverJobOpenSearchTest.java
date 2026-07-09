/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.archiver.AbstractArchiverJob.ArchiveBatch;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.property.ArchiverProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import jakarta.json.JsonArray;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskArchiverJobOpenSearchTest {

  private static final String TASK_INDEX = "tasklist-task-index";
  private static final String TASK_VAR_INDEX = "tasklist-task-variable-index";
  private static final String FINISH_DATE = "2026-03-16";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @InjectMocks
  private TaskArchiverJobOpenSearch underTest = new TaskArchiverJobOpenSearch(List.of(1));

  @Mock private ArchiverUtil archiverUtil;
  @Mock private TaskTemplate taskTemplate;
  @Mock private TaskVariableTemplate taskVariableTemplate;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private ArchiverProperties archiverProperties;

  @BeforeEach
  public void setUp() {
    when(taskTemplate.getFullQualifiedName()).thenReturn(TASK_INDEX);
    when(taskVariableTemplate.getFullQualifiedName()).thenReturn(TASK_VAR_INDEX);
    when(tasklistProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.getRolloverInterval()).thenReturn("1d");
    when(archiverProperties.getElsRolloverDateFormat()).thenReturn("date");
  }

  @Test
  public void archiveBatchReturnsNothingToArchiveWhenBatchIsNull() {
    final CompletableFuture<Map.Entry<String, Integer>> res = underTest.archiveBatch(null);

    assertThat(res).succeedsWithin(TIMEOUT).extracting(Map.Entry::getValue).isEqualTo(0);
    verify(archiverUtil, never()).moveDocuments(any(), any(), any(), any());
  }

  @Test
  public void archiveBatchMovesTasksAndVariables() {
    when(archiverUtil.moveDocuments(
            eq(TASK_VAR_INDEX), eq(TaskVariableTemplate.TASK_ID), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(archiverUtil.moveDocuments(eq(TASK_INDEX), eq(TaskTemplate.ID), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var batch = new ArchiveBatch(FINISH_DATE, List.of("task-1", "task-2"));
    final CompletableFuture<Map.Entry<String, Integer>> res = underTest.archiveBatch(batch);

    assertThat(res).succeedsWithin(TIMEOUT).extracting(Map.Entry::getValue).isEqualTo(2);
    verify(archiverUtil)
        .moveDocuments(
            eq(TASK_VAR_INDEX), eq(TaskVariableTemplate.TASK_ID), eq(FINISH_DATE), any());
    verify(archiverUtil).moveDocuments(eq(TASK_INDEX), eq(TaskTemplate.ID), eq(FINISH_DATE), any());
  }

  @Test
  public void archiveBatchFailsWhenVariableMovesFail() {
    final CompletableFuture<Void> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("OS variable move failed"));
    when(archiverUtil.moveDocuments(eq(TASK_VAR_INDEX), any(), any(), any())).thenReturn(failed);
    when(archiverUtil.moveDocuments(eq(TASK_INDEX), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var batch = new ArchiveBatch(FINISH_DATE, List.of("task-1"));
    final CompletableFuture<Map.Entry<String, Integer>> res = underTest.archiveBatch(batch);

    assertThat(res).failsWithin(TIMEOUT);
  }

  @Test
  public void archiveBatchFailsWhenTaskMoveFails() {
    when(archiverUtil.moveDocuments(eq(TASK_VAR_INDEX), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    final CompletableFuture<Void> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("OS task move failed"));
    when(archiverUtil.moveDocuments(eq(TASK_INDEX), any(), any(), any())).thenReturn(failed);

    final var batch = new ArchiveBatch(FINISH_DATE, List.of("task-1"));
    final CompletableFuture<Map.Entry<String, Integer>> res = underTest.archiveBatch(batch);

    assertThat(res).failsWithin(TIMEOUT);
  }

  @Test
  public void createArchiveBatchReturnsNullWhenNoHits() {
    final SearchResponse<?> response = mockSearchResponse(List.of());

    assertThat(underTest.createArchiveBatch(response)).isNull();
  }

  @Test
  public void createArchiveBatchIncludesAllHitsInSameBucket() {
    final SearchResponse<?> response =
        mockSearchResponse(List.of(mockHit("id1", "2026-03-16"), mockHit("id2", "2026-03-16")));

    final ArchiveBatch batch = underTest.createArchiveBatch(response);

    assertThat(batch).isNotNull();
    assertThat(batch.getFinishDate()).isEqualTo("2026-03-16");
    assertThat(batch.getIds()).containsExactly("id1", "id2");
  }

  @Test
  public void createArchiveBatchTrimsToCrossedBucketBoundary() {
    final SearchResponse<?> response =
        mockSearchResponse(List.of(mockHit("id1", "2026-03-16"), mockHit("id2", "2026-03-17")));

    final ArchiveBatch batch = underTest.createArchiveBatch(response);

    assertThat(batch).isNotNull();
    assertThat(batch.getFinishDate()).isEqualTo("2026-03-16");
    assertThat(batch.getIds()).containsExactly("id1");
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<?> mockSearchResponse(final List<Hit<?>> hits) {
    final SearchResponse<Object> response = mock(SearchResponse.class);
    final HitsMetadata<Object> metadata = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(metadata);
    when(metadata.hits()).thenReturn((List) hits);
    return response;
  }

  @SuppressWarnings("unchecked")
  private Hit<?> mockHit(final String id, final String completionTime) {
    final Hit<Object> hit = mock(Hit.class);
    final JsonData jsonData = mock(JsonData.class);
    final JsonArray jsonArray = mock(JsonArray.class);
    when(jsonArray.getString(0)).thenReturn(completionTime);
    when(jsonArray.asJsonArray()).thenReturn(jsonArray);
    when(jsonData.toJson()).thenReturn(jsonArray);
    when(hit.fields()).thenReturn(Map.of(TaskTemplate.COMPLETION_TIME, jsonData));
    when(hit.id()).thenReturn(id);
    return hit;
  }
}
