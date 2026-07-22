/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.ArchiveDocIdsBatch;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.BatchCountMismatchException;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.operate.property.ArchiverProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.elasticsearch.ElasticsearchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ArchiveByIdTaskSupplierTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveByIdTaskSupplierTest.class);
  private static final Executor DIRECT_EXECUTOR = Runnable::run;
  private final Metrics metrics = mock(Metrics.class);

  static Stream<Throwable> retryableErrors() {
    return Stream.of(
        new CompletionException(mock(SocketTimeoutException.class)),
        new CompletionException(mock(ElasticsearchException.class)),
        new CompletionException(mock(OpenSearchException.class)),
        new CompletionException(mock(BatchCountMismatchException.class)),
        mock(SocketTimeoutException.class),
        mock(ElasticsearchException.class),
        mock(OpenSearchException.class),
        mock(BatchCountMismatchException.class));
  }

  @ParameterizedTest
  @MethodSource("retryableErrors")
  void shouldRetryOnRetryableError(final Throwable retryableError) {
    // given
    final var reindexCallCount = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(
                        List.of(IdWithRouting.of("doc1"), IdWithRouting.of("doc2")),
                        List.of("after1"))),
            (source, dest, ids) -> {
              if (reindexCallCount.incrementAndGet() <= 2) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            LOGGER);

    // when - first call fails, returns 0 (retry scheduled)
    final var firstResult = taskSupplier.moveNextBatch().join();
    assertThat(firstResult).isEqualTo(0L);
    verify(metrics, times(1)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // when - second call fails, returns 0 (retry scheduled)
    final var secondResult = taskSupplier.moveNextBatch().join();
    assertThat(secondResult).isEqualTo(0L);
    verify(metrics, times(2)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // when - third call succeeds
    final var thirdResult = taskSupplier.moveNextBatch().join();
    assertThat(thirdResult).isEqualTo(2L);
  }

  @Test
  void shouldThrowWhenMaxRetriesExceeded() {
    // given - maxRetryCount of 1, so only 1 retry is allowed
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.failedFuture(retryableError),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(1),
            metrics,
            LOGGER);

    // when - first call retries (retryCount becomes 1, which is <= maxRetryCount 1)
    final var firstResult = taskSupplier.moveNextBatch().join();
    assertThat(firstResult).isEqualTo(0L);
    verify(metrics, times(1)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // when - second call exceeds max retries (retryCount becomes 2, which is NOT <= 1)
    final var future = taskSupplier.moveNextBatch();

    // then - should throw
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void shouldThrowImmediatelyOnNonRetryableError() {
    final var nonRetryableError = new CompletionException(new RuntimeException("non-retryable"));

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.failedFuture(nonRetryableError),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            LOGGER);

    assertThatThrownBy(() -> taskSupplier.moveNextBatch().join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(RuntimeException.class);
    verify(metrics, times(0)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);
  }

  @Test
  void shouldRetryAndNotLogWarningWhenReindexProcessesFewerDocsThanExpected() {
    // given - reindex reports processing only 1 of the 2 expected docs
    final var logger = mock(Logger.class);
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(
                        List.of(IdWithRouting.of("doc1"), IdWithRouting.of("doc2")),
                        List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.completedFuture(1L),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            logger);

    // when - the undercount is treated as a retryable BatchCountMismatchException
    final var result = taskSupplier.moveNextBatch().join();

    // then
    assertThat(result).isEqualTo(0L);
    assertThat(taskSupplier.isComplete()).isFalse();
    verify(metrics, times(1)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);
    // an undercount throws before reaching the "processedCount > expectedCount" warning
    verify(logger, times(0)).warn(any(String.class), any(), any(), any(), any());
  }

  @Test
  void shouldNotThrowAndLogWarningWhenReindexProcessesMoreDocsThanExpected() {
    // given - reindex reports processing more docs than were sent; only an undercount
    // (processedCount < expectedCount) is treated as a mismatch
    final var logger = mock(Logger.class);
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.completedFuture(2L),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            logger);

    // when
    final var result = taskSupplier.moveNextBatch().join();

    // then - no exception, no retry, but the overcount is logged
    assertThat(result).isEqualTo(1L);
    assertThat(taskSupplier.isComplete()).isFalse();
    verify(metrics, times(0)).recordCounts(any(), anyLong());
    verify(logger).warn(any(String.class), eq("reindex"), eq("source-idx"), eq(2L), eq(1));
  }

  @Test
  void shouldNotThrowAndLogWarningWhenDeleteProcessesMoreDocsThanExpected() {
    // given - delete reports processing more docs than were sent
    final var logger = mock(Logger.class);
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            (source, ids) -> CompletableFuture.completedFuture(2L),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            logger);

    // when
    final var result = taskSupplier.moveNextBatch().join();

    // then - no exception, no retry, but the overcount is logged
    assertThat(result).isEqualTo(2L);
    assertThat(taskSupplier.isComplete()).isFalse();
    verify(metrics, times(0)).recordCounts(any(), anyLong());
    verify(logger).warn(any(String.class), eq("delete"), eq("source-idx"), eq(2L), eq(1));
  }

  @Test
  void shouldNotLogWarningWhenProcessedCountMatchesExpected() {
    // given - reindex and delete both process exactly the expected number of docs
    final var logger = mock(Logger.class);
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            logger);

    // when
    final var result = taskSupplier.moveNextBatch().join();

    // then - an exact match is neither a mismatch nor an overcount, so no warning is logged
    assertThat(result).isEqualTo(1L);
    verify(logger, times(0)).warn(any(String.class), any(), any(), any(), any());
  }

  @Test
  void shouldReturnZeroWhenBatchIsEmpty() {
    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) -> CompletableFuture.completedFuture(ArchiveDocIdsBatch.empty()),
            (source, dest, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            LOGGER);

    final var result = taskSupplier.moveNextBatch().join();
    assertThat(result).isEqualTo(0L);
    assertThat(taskSupplier.isComplete()).isTrue();
  }

  @Test
  void shouldResetRetryCountAfterSuccessfulBatch() {
    // given - maxRetryAttempts=2 means 2 retries allowed before throwing on the 3rd failure.
    // reindex calls 1, 3, 4, 5, 6 fail; calls 2, 7 succeed.
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));
    final var reindexCallCount = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> {
              final var call = reindexCallCount.incrementAndGet();
              if (call == 1 || call == 3 || call == 4 || call == 5 || call == 6) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(2),
            metrics,
            LOGGER);

    // call 1 fails (retryCount=1, <= 2) — retry
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(1)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // call 2 succeeds — retryCount resets to 0
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);

    // call 3 fails (retryCount=1, <= 2) — retry
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(2)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // call 4 fails (retryCount=2, <= 2) — retry
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(3)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // call 5 fails (retryCount=3, NOT <= maxRetryAttempts 2) — throws and resets retryCount
    assertThatThrownBy(() -> taskSupplier.moveNextBatch().join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(SocketTimeoutException.class);

    // call 6 fails again (retryCount=1, <= 2 since it was reset) — retry
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    verify(metrics, times(4)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);

    // call 7 succeeds
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(taskSupplier.getTotalArchived()).isEqualTo(2L);
  }

  @Test
  void shouldReduceReindexBatchSizeAfterEachSocketTimeoutRetry() {
    // given - maxRetryAttempts=3. Reindex fails on calls 1, 2; succeeds on others.
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));
    final var reindexCallCount = new AtomicInteger(0);
    final var observedBatchSize = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) -> {
              observedBatchSize.set(size);
              return CompletableFuture.completedFuture(
                  ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1")));
            },
            (source, dest, ids) -> {
              if (Set.of(1, 2).contains(reindexCallCount.incrementAndGet())) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            LOGGER);

    // attempt 1 fails, full batch size used
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(1200);

    // attempt 2 fails, batch size was halved
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(600);

    // attempt 3 succeeds, batch size was halved again
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(observedBatchSize.get()).isEqualTo(300);

    // attempt 4 successful, batch size is kept
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(observedBatchSize.get()).isEqualTo(300);
  }

  @Test
  void shouldNotReduceBatchSizeBelowMinimum() {
    // given - starting batch size of 100 so it hits the floor at 50 after one halving
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));
    final var reindexCallCount = new AtomicInteger(0);
    final var observedBatchSize = new AtomicInteger(0);

    final var archiverProperties = new ArchiverProperties();
    archiverProperties.setArchiveByIdBatchSize(100);
    archiverProperties.setArchiveByIdMaxRetryAttempts(10);
    archiverProperties.setArchiveByIdRetryDelayMs(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) -> {
              observedBatchSize.set(size);
              return CompletableFuture.completedFuture(
                  ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1")));
            },
            (source, dest, ids) -> {
              if (reindexCallCount.incrementAndGet() <= 3) {
                return CompletableFuture.failedFuture(retryableError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverProperties,
            metrics,
            LOGGER);

    // attempt 1 fails: 100 used, reduction gives 50
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(100);

    // attempt 2 fails: 50 used, reduction stays at 50 (floor)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(50);

    // attempt 3 fails: still 50 (floor enforced)
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(50);

    // attempt 4 succeeds: still 50
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(observedBatchSize.get()).isEqualTo(50);
  }

  @Test
  void shouldNotReduceBatchSizeForNonSocketTimeoutRetryableError() {
    // given - retryable error that is NOT a SocketTimeoutException
    final var batchMismatchError =
        new CompletionException(new BatchCountMismatchException("reindex", "count mismatch"));
    final var reindexCallCount = new AtomicInteger(0);
    final var observedBatchSize = new AtomicInteger(0);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) -> {
              observedBatchSize.set(size);
              return CompletableFuture.completedFuture(
                  ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1")));
            },
            (source, dest, ids) -> {
              if (reindexCallCount.incrementAndGet() <= 2) {
                return CompletableFuture.failedFuture(batchMismatchError);
              }
              return CompletableFuture.completedFuture((long) ids.size());
            },
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverPropertiesWithMaxRetry(3),
            metrics,
            LOGGER);

    // attempt 1 fails with non-SocketTimeout error
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(1200);

    // attempt 2 fails: batch size unchanged
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(0L);
    assertThat(observedBatchSize.get()).isEqualTo(1200);

    // attempt 3 succeeds: batch size still unchanged
    assertThat(taskSupplier.moveNextBatch().join()).isEqualTo(1L);
    assertThat(observedBatchSize.get()).isEqualTo(1200);
  }

  @Test
  void shouldDelayBeforeRetryingRetryableError() {
    // given
    final var retryableError = new CompletionException(new SocketTimeoutException("timeout"));

    final var archiverProperties = new ArchiverProperties();
    archiverProperties.setArchiveByIdBatchSize(1200);
    archiverProperties.setArchiveByIdMaxRetryAttempts(3);
    archiverProperties.setArchiveByIdRetryDelayMs(100);

    final var taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            "source-idx",
            "destination-idx",
            (searchAfter, size) ->
                CompletableFuture.completedFuture(
                    ArchiveDocIdsBatch.from(List.of(IdWithRouting.of("doc1")), List.of("after1"))),
            (source, dest, ids) -> CompletableFuture.failedFuture(retryableError),
            (source, ids) -> CompletableFuture.completedFuture((long) ids.size()),
            DIRECT_EXECUTOR,
            archiverProperties,
            metrics,
            LOGGER);

    final var startMs = System.currentTimeMillis();
    taskSupplier.moveNextBatch().join(); // retryCount = 1, delay = 100ms * 1
    final var elapsed = System.currentTimeMillis() - startMs;

    assertThat(elapsed).isGreaterThanOrEqualTo(100L);
    verify(metrics, times(1)).recordCounts(Metrics.COUNTER_NAME_ARCHIVER_BATCH_RETRIES, 1);
  }

  private static ArchiverProperties archiverPropertiesWithMaxRetry(final int maxRetryCount) {
    final var archiverProperties = new ArchiverProperties();
    archiverProperties.setArchiveByIdMaxRetryAttempts(maxRetryCount);
    archiverProperties.setArchiveByIdRetryDelayMs(0);
    archiverProperties.setArchiveByIdBatchSize(1200);
    return archiverProperties;
  }
}
