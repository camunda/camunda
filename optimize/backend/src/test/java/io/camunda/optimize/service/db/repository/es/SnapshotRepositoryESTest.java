/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotResponse;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotRepositoryESTest {

  private OptimizeElasticsearchClient esClient;
  private SnapshotRepositoryES snapshotRepositoryES;

  @BeforeEach
  void init() {
    esClient = mock(OptimizeElasticsearchClient.class);
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    snapshotRepositoryES = new SnapshotRepositoryES(esClient, configurationService);
  }

  @Test
  void shouldCompleteReturnedFutureNormallyOnSuccessfulSnapshot() {
    // given
    final CreateSnapshotResponse response =
        CreateSnapshotResponse.of(
            b ->
                b.snapshot(
                    s ->
                        s.snapshot("snap-1")
                            .state("SUCCESS")
                            .uuid("uuid")
                            .indices(java.util.List.of("job-registry"))
                            .dataStreams(java.util.List.of())));
    when(esClient.triggerSnapshotAsync(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryES.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldCompleteReturnedFutureNormallyEvenWhenSnapshotCreationFails() {
    // given - a failed snapshot attempt must not block a subsequently chained snapshot request
    when(esClient.triggerSnapshotAsync(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new IOException("connection reset")));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryES.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldCompleteReturnedFutureNormallyOnConcurrentSnapshotExecutionException() {
    // given
    final ElasticsearchException exception =
        new ElasticsearchException(
            "concurrent_snapshot_execution_exception",
            ErrorResponse.of(
                e ->
                    e.error(
                            ErrorCause.of(
                                c ->
                                    c.type("concurrent_snapshot_execution_exception")
                                        .reason("a snapshot is already running")))
                        .status(503)));
    when(esClient.triggerSnapshotAsync(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(exception));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryES.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }
}
