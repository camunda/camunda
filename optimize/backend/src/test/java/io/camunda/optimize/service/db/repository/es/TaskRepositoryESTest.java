/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.BulkIndexByScrollFailure;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeByQueryFailureException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskRepositoryESTest {

  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private TaskRepositoryES taskRepositoryES;

  @BeforeEach
  void init() {
    esClient = mock(OptimizeElasticsearchClient.class);
    lenient().when(esClient.addPrefixesToIndices(any())).thenReturn(List.of("test-index"));
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService
        .getElasticSearchConfiguration()
        .getConnection()
        .setClusterTaskCheckingEnabled(false);
    taskRepositoryES = new TaskRepositoryES(esClient, configurationService);
  }

  @Test
  void shouldUseProceedConflictsModeByDefault() throws IOException {
    // given
    final ArgumentCaptor<DeleteByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(DeleteByQueryRequest.class);
    when(esClient.submitDeleteTask(requestCaptor.capture()))
        .thenReturn(DeleteByQueryResponse.of(b -> b.deleted(1L).timedOut(false)));

    // when
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.matchAll(m -> m)), "test instances", true, "test-index");

    // then -- unrelated callers keep skipping (rather than failing on) version conflicts
    assertThat(requestCaptor.getValue().conflicts()).isEqualTo(Conflicts.Proceed);
  }

  @Test
  void shouldUseAbortConflictsModeWhenFailOnVersionConflictsIsRequested() throws IOException {
    // given
    final ArgumentCaptor<DeleteByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(DeleteByQueryRequest.class);
    when(esClient.submitDeleteTask(requestCaptor.capture()))
        .thenReturn(DeleteByQueryResponse.of(b -> b.deleted(1L).timedOut(false)));

    // when
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.matchAll(m -> m)), "test instances", true, true, "test-index");

    // then
    assertThat(requestCaptor.getValue().conflicts()).isEqualTo(Conflicts.Abort);
  }

  @Test
  void shouldThrowWhenAbortedDeleteReportsAVersionConflictFailure() throws IOException {
    // given -- with conflicts=abort, ES surfaces a version conflict via the failures list
    final BulkIndexByScrollFailure conflictFailure =
        BulkIndexByScrollFailure.of(
            f ->
                f.id("instance-1")
                    .index("test-index")
                    .cause(c -> c.type("version_conflict_engine_exception").reason("conflict"))
                    .status(409));
    when(esClient.submitDeleteTask(any(DeleteByQueryRequest.class)))
        .thenReturn(
            DeleteByQueryResponse.of(b -> b.deleted(0L).timedOut(false).failures(conflictFailure)));

    // when / then
    assertThatThrownBy(
            () ->
                taskRepositoryES.tryDeleteByQueryRequest(
                    Query.of(q -> q.matchAll(m -> m)), "test instances", true, true, "test-index"))
        .isInstanceOf(OptimizeByQueryFailureException.class);
  }

  @Test
  void shouldUseProceedConflictsModeByDefaultForUpdate() throws IOException {
    // given
    final ArgumentCaptor<UpdateByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateByQueryRequest.class);
    when(esClient.submitUpdateTask(requestCaptor.capture()))
        .thenReturn(UpdateByQueryResponse.of(b -> b.updated(1L).timedOut(false)));

    // when
    taskRepositoryES.tryUpdateByQueryRequest(
        "test reports", mock(Script.class), Query.of(q -> q.matchAll(m -> m)), "test-index");

    // then -- unrelated callers keep skipping (rather than failing on) version conflicts
    assertThat(requestCaptor.getValue().conflicts()).isEqualTo(Conflicts.Proceed);
  }

  @Test
  void shouldUseAbortConflictsModeWhenFailOnVersionConflictsIsRequestedForUpdate()
      throws IOException {
    // given
    final ArgumentCaptor<UpdateByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateByQueryRequest.class);
    when(esClient.submitUpdateTask(requestCaptor.capture()))
        .thenReturn(UpdateByQueryResponse.of(b -> b.updated(1L).timedOut(false)));

    // when
    taskRepositoryES.tryUpdateByQueryRequest(
        "test reports", mock(Script.class), Query.of(q -> q.matchAll(m -> m)), true, "test-index");

    // then
    assertThat(requestCaptor.getValue().conflicts()).isEqualTo(Conflicts.Abort);
  }

  @Test
  void shouldThrowWhenAbortedUpdateReportsAVersionConflictFailure() throws IOException {
    // given
    final BulkIndexByScrollFailure conflictFailure =
        BulkIndexByScrollFailure.of(
            f ->
                f.id("report-1")
                    .index("test-index")
                    .cause(c -> c.type("version_conflict_engine_exception").reason("conflict"))
                    .status(409));
    when(esClient.submitUpdateTask(any(UpdateByQueryRequest.class)))
        .thenReturn(
            UpdateByQueryResponse.of(b -> b.updated(0L).timedOut(false).failures(conflictFailure)));

    // when / then
    assertThatThrownBy(
            () ->
                taskRepositoryES.tryUpdateByQueryRequest(
                    "test reports",
                    mock(co.elastic.clients.elasticsearch._types.Script.class),
                    Query.of(q -> q.matchAll(m -> m)),
                    true,
                    "test-index"))
        .isInstanceOf(OptimizeByQueryFailureException.class);
  }
}
