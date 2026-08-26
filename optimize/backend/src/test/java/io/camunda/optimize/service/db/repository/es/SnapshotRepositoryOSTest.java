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

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.RichOpenSearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.OpenSearchSnapshotAsyncClient;

class SnapshotRepositoryOSTest {

  private OpenSearchSnapshotAsyncClient snapshotAsyncClient;
  private SnapshotRepositoryOS snapshotRepositoryOS;

  @BeforeEach
  void init() throws IOException {
    final OpenSearchAsyncClient openSearchAsyncClient = mock(OpenSearchAsyncClient.class);
    snapshotAsyncClient = mock(OpenSearchSnapshotAsyncClient.class);
    when(openSearchAsyncClient.snapshot()).thenReturn(snapshotAsyncClient);

    final RichOpenSearchClient richOpenSearchClient =
        new RichOpenSearchClient(
            mock(OpenSearchClient.class),
            openSearchAsyncClient,
            new OptimizeIndexNameService("optimize"));
    final OptimizeOpenSearchClient osClient = mock(OptimizeOpenSearchClient.class);
    when(osClient.getRichOpenSearchClient()).thenReturn(richOpenSearchClient);

    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    snapshotRepositoryOS = new SnapshotRepositoryOS(osClient, configurationService);
  }

  @Test
  void shouldCompleteReturnedFutureNormallyOnSuccessfulSnapshot() throws IOException {
    // given
    final CreateSnapshotResponse response =
        CreateSnapshotResponse.of(
            b ->
                b.snapshot(
                    s ->
                        s.snapshot("snap-1")
                            .uuid("uuid")
                            .state("SUCCESS")
                            .indices("job-registry")));
    when(snapshotAsyncClient.create(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryOS.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldCompleteReturnedFutureNormallyEvenWhenSnapshotCreationFails() throws IOException {
    // given - a failed snapshot attempt must not block a subsequently chained snapshot request
    when(snapshotAsyncClient.create(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(new IOException("connection reset")));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryOS.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }

  @Test
  void shouldCompleteReturnedFutureNormallyOnConcurrentSnapshotExecutionException()
      throws IOException {
    // given
    final OpenSearchException exception =
        new OpenSearchException(
            new ErrorResponse.Builder()
                .status(503)
                .error(
                    new ErrorCause.Builder()
                        .type("concurrent_snapshot_execution_exception")
                        .reason("a snapshot is already running")
                        .build())
                .build());
    when(snapshotAsyncClient.create(any(CreateSnapshotRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(exception));

    // when
    final CompletableFuture<Void> result =
        snapshotRepositoryOS.triggerSnapshot("snap-1", new String[] {"job-registry"});

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
  }
}
