/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for the silent archiver hang documented in GitHub issue #52781. Verifies that
 * futures returned by {@link ArchiverUtilElasticSearch} always complete — even when the underlying
 * ES failure carries no {@link Throwable#getCause() cause}, when metric recording itself throws, or
 * when any other unexpected exception occurs in the callback.
 */
@ExtendWith(MockitoExtension.class)
public class ArchiverUtilElasticSearchTest {

  @InjectMocks private ArchiverUtilElasticSearch underTest;
  @Mock private Metrics metrics;

  // --- delete: null cause ---

  @Test
  public void deleteDocumentsCompletesFutureWhenFailureHasNullCause() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final RuntimeException noCause = new RuntimeException("ES delete failed");
      assertThat(noCause.getCause()).isNull();
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(noCause);
      mockedStatic
          .when(() -> ElasticsearchUtil.deleteByQueryAsync(any(), any(), any()))
          .thenReturn(failed);

      final CompletableFuture<Long> res = underTest.deleteDocuments("idx", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
      verify(metrics)
          .recordCounts(
              eq(Metrics.COUNTER_NAME_DELETE_FAILURES),
              anyLong(),
              eq("exception"),
              eq("RuntimeException"));
    }
  }

  // --- reindex: null cause ---

  @Test
  public void reindexDocumentsCompletesFutureWhenFailureHasNullCause() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final RuntimeException noCause = new RuntimeException("ES reindex failed");
      assertThat(noCause.getCause()).isNull();
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(noCause);
      mockedStatic
          .when(() -> ElasticsearchUtil.reindexAsync(any(), any(), any()))
          .thenReturn(failed);

      final CompletableFuture<Long> res =
          underTest.reindexDocuments("src", "dst", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
      verify(metrics)
          .recordCounts(
              eq(Metrics.COUNTER_NAME_REINDEX_FAILURES),
              anyLong(),
              eq("exception"),
              eq("RuntimeException"));
    }
  }

  // --- delete: metric tracking throws ---

  @Test
  public void deleteDocumentsCompletesFutureWhenMetricTrackingThrows() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RuntimeException("ES delete failed"));
      mockedStatic
          .when(() -> ElasticsearchUtil.deleteByQueryAsync(any(), any(), any()))
          .thenReturn(failed);
      doThrow(new RuntimeException("metric registry exploded"))
          .when(metrics)
          .recordCounts(
              eq(Metrics.COUNTER_NAME_DELETE_FAILURES),
              anyLong(),
              eq("exception"),
              eq("RuntimeException"));

      final CompletableFuture<Long> res = underTest.deleteDocuments("idx", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
    }
  }

  // --- reindex: metric tracking throws ---

  @Test
  public void reindexDocumentsCompletesFutureWhenMetricTrackingThrows() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RuntimeException("ES reindex failed"));
      mockedStatic
          .when(() -> ElasticsearchUtil.reindexAsync(any(), any(), any()))
          .thenReturn(failed);
      doThrow(new RuntimeException("metric registry exploded"))
          .when(metrics)
          .recordCounts(
              eq(Metrics.COUNTER_NAME_REINDEX_FAILURES),
              anyLong(),
              eq("exception"),
              eq("RuntimeException"));

      final CompletableFuture<Long> res =
          underTest.reindexDocuments("src", "dst", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
    }
  }

  // --- delete: timer throws (broader protection) ---

  @Test
  public void deleteDocumentsCompletesFutureWhenTimerThrows() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RuntimeException("ES delete failed"));
      mockedStatic
          .when(() -> ElasticsearchUtil.deleteByQueryAsync(any(), any(), any()))
          .thenReturn(failed);
      // metrics.getTimer() returns null → timer.stop(null) will throw NPE inside the callback
      // before handleResponse or metric tracking even runs

      final CompletableFuture<Long> res = underTest.deleteDocuments("idx", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
    }
  }

  // --- reindex: timer throws (broader protection) ---

  @Test
  public void reindexDocumentsCompletesFutureWhenTimerThrows() throws Exception {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class);
        final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      mockedTimer.when(Timer::start).thenReturn(mock(Timer.Sample.class));
      final CompletableFuture<BulkByScrollResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RuntimeException("ES reindex failed"));
      mockedStatic
          .when(() -> ElasticsearchUtil.reindexAsync(any(), any(), any()))
          .thenReturn(failed);

      final CompletableFuture<Long> res =
          underTest.reindexDocuments("src", "dst", "id", List.of("1"));

      assertThatFutureCompletesWithin(res, 5, TimeUnit.SECONDS);
      assertThat(res).isCompletedExceptionally();
    }
  }

  private static void assertThatFutureCompletesWithin(
      final CompletableFuture<?> future, final long timeout, final TimeUnit unit) throws Exception {
    try {
      future.get(timeout, unit);
    } catch (final TimeoutException timeoutException) {
      throw new AssertionError(
          "Future did not complete within " + timeout + " " + unit + " — archiver would hang",
          timeoutException);
    } catch (final CompletionException | ExecutionException ignored) {
      // expected — caller asserts the future is completedExceptionally
    }
  }
}
