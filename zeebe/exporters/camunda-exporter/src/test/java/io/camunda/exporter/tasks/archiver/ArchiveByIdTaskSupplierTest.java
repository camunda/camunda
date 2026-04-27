/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveByIdTaskSupplier.ArchiveDocIdsBatch;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ArchiveByIdTaskSupplierTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveByIdTaskSupplierTest.class);
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);

  @Test
  void shouldRetryOnRetryableErrorAndRecordMetric() {
    // given
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));
    final var reindexCallCount = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            historyConfigWithMaxRetry(3),
            "source-idx",
            "destination-idx",
            searchAfter ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of("doc1", "doc2"), List.of("after1"))),
            (source, dest, ids) -> {
              if (reindexCallCount.incrementAndGet() <= 2) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            metrics,
            LOGGER);

    // when - first call should encounter retryable error and return 0
    final var firstResult = taskSupplier.moveNextBatch().join();

    // then - returns 0 to signal retry, metric recorded
    assertThat(firstResult).isEqualTo(0L);
    assertThat(taskSupplier.isComplete()).isFalse();
    verify(metrics, times(1)).recordArchiverBatchRetry();

    // when - second call also fails with retryable error
    final var secondResult = taskSupplier.moveNextBatch().join();

    // then - still retrying
    assertThat(secondResult).isEqualTo(0L);
    assertThat(taskSupplier.isComplete()).isFalse();
    verify(metrics, times(2)).recordArchiverBatchRetry();

    // when - third call succeeds because reindexCallCount > 2
    final var thirdResult = taskSupplier.moveNextBatch().join();

    // then - successfully archived
    assertThat(thirdResult).isEqualTo(2L);
  }

  @Test
  void shouldThrowWhenMaxRetriesExceeded() {
    // given - maxRetryCount of 2, so only 1 retry is allowed
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            historyConfigWithMaxRetry(2),
            "source-idx",
            "destination-idx",
            searchAfter ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of("doc1"), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.failedFuture(retryableError),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            metrics,
            LOGGER);

    // when - first call retries (retryCount becomes 1, which is < maxRetryCount 2)
    final var firstResult = taskSupplier.moveNextBatch().join();
    assertThat(firstResult).isEqualTo(0L);
    verify(metrics, times(1)).recordArchiverBatchRetry();

    // when - second call exceeds max retries (retryCount becomes 2, which is NOT < 2)
    final var future = taskSupplier.moveNextBatch();

    // then - should throw
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void shouldNotRetryOnNonRetryableError() {
    // given
    final var nonRetryableError = new CompletionException(new IllegalStateException("bad state"));

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            historyConfigWithMaxRetry(3),
            "source-idx",
            "destination-idx",
            searchAfter ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of("doc1"), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.failedFuture(nonRetryableError),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            metrics,
            LOGGER);

    // when
    final var future = taskSupplier.moveNextBatch();

    // then - should throw immediately without retrying
    assertThatThrownBy(future::join).isInstanceOf(CompletionException.class);
    verify(metrics, times(0)).recordArchiverBatchRetry();
  }

  @Test
  void shouldCompleteWhenResponseIsEmpty() {
    // given
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            historyConfigWithMaxRetry(3),
            "source-idx",
            "destination-idx",
            searchAfter -> CompletableFuture.completedFuture(ArchiveDocIdsBatch.empty()),
            (source, dest, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            metrics,
            LOGGER);

    // when
    final var result = taskSupplier.moveNextBatch().join();

    // then
    assertThat(result).isEqualTo(0L);
    assertThat(taskSupplier.isComplete()).isTrue();
  }

  @Test
  void shouldResetRetryCountAfterSuccessfulBatch() {
    // given - maxRetryAttempts=3 means 2 retries allowed before throwing on the 3rd failure.
    // reindex calls 1, 3, 4, 5, 6 fail; calls 2, 7 succeed.
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));
    final var reindexCallCount = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            historyConfigWithMaxRetry(3),
            "source-idx",
            "destination-idx",
            searchAfter ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of("doc1"), List.of("after1"))),
            (source, dest, ids) -> {
              final int call = reindexCallCount.incrementAndGet();
              if (call != 2 && call != 7) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            metrics,
            LOGGER);

    // when - call 1 fails, triggers retry (retryCount=1)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(1)).recordArchiverBatchRetry();

    // when - call 2 succeeds, resets retry count (retryCount=0)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(taskSupplier.getTotalArchived()).isEqualTo(1L);

    // when - three consecutive failures exhaust retries
    // call 3 fails (retryCount=1)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(2)).recordArchiverBatchRetry();

    // call 4 fails (retryCount=2)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(3)).recordArchiverBatchRetry();

    // call 5 fails (retryCount=3, NOT < maxRetryAttempts 3) — throws and resets retryCount
    assertThatThrownBy(() -> taskSupplier.moveNextBatch().join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(SocketTimeoutException.class);

    // when - call 6 fails, retry works with fresh count proving reset on throw (retryCount=1)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(4)).recordArchiverBatchRetry();

    // when - call 7 succeeds
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(taskSupplier.getTotalArchived()).isEqualTo(2L);
  }


  private static HistoryConfiguration historyConfigWithMaxRetry(final int maxRetryCount) {
    final var config = new HistoryConfiguration();
    config.setArchiveByIdMaxRetryAttempts(maxRetryCount);
    return config;
  }

}
