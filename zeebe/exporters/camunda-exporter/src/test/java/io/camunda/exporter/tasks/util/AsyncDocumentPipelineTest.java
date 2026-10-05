/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchProcessor;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchSupplier;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.PipelineStats;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.opensearch.client.opensearch._types.OpenSearchException;

class AsyncDocumentPipelineTest {

  @Test
  void shouldProcessInBatches() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(10L, 10L);

    final var inOrder = Mockito.inOrder(batchProcessor);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldCompleteOnceLastBatchIsBelowBatchSize() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final BatchSupplier<Integer, Integer> batchSupplier = mock(BatchSupplier.class);
    when(batchSupplier.supply(any(), anyInt()))
        .thenReturn(
            CompletableFuture.completedFuture(DocumentBatch.from(List.of(4, 5, 6), 6)),
            CompletableFuture.completedFuture(DocumentBatch.from(List.of(7, 8), 8)));

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(5L, 5L);

    final var inOrder = Mockito.inOrder(batchSupplier, batchProcessor);
    inOrder.verify(batchSupplier).supply(null, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));
    inOrder.verify(batchSupplier).supply(6, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8), 8));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotCompleteLastBatchIfRetryableErrorOccursInProcessing() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any()))
        .then(returnBatchSize())
        .thenReturn(CompletableFuture.failedFuture(new RetryableException()))
        .then(returnBatchSize());

    final BatchSupplier<Integer, Integer> batchSupplier = mock(BatchSupplier.class);
    when(batchSupplier.supply(any(), anyInt()))
        .thenReturn(
            CompletableFuture.completedFuture(DocumentBatch.from(List.of(4, 5, 6), 6)),
            CompletableFuture.completedFuture(DocumentBatch.from(List.of(7, 8), 8)));

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .addRetryableException(RetryableException.class)
            .retryRecorder(retryRecorder)
            .retryDelayMs(1)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(7L, 5L);

    final var inOrder = Mockito.inOrder(batchSupplier, batchProcessor, retryRecorder);
    inOrder.verify(batchSupplier).supply(null, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));
    inOrder.verify(batchSupplier).supply(6, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8), 8));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8), 8));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldFailIfNonRecoverableErrorOccursReadingBatches() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).thenThrow(new RuntimeException("simulated error"));

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .withMessage("simulated error");

    final var inOrder = Mockito.inOrder(batchProcessor);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verifyNoMoreInteractions();
  }

  @ParameterizedTest
  @MethodSource("retryableErrors")
  void shouldRetryIfRecoverableErrorOccursReadingBatches(final Throwable retryableError) {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .then(returnBatchSize())
        .then(returnBatchSize())
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .then(returnBatchSize());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(3)
            .batchSize(3)
            .addRetryableException(RetryableException.class)
            .maxRetryAttempts(2)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(16L, 10L);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder);
    // first retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));

    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));

    // second retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));

    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldFailIfRecoverableErrorExceedsRetryLimit() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenThrow(new RetryableException())
        .then(returnBatchSize())
        .then(returnBatchSize())
        .thenThrow(new RetryableException());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .addRetryableException(RetryableException.class)
            .maxRetryAttempts(2)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(RetryableException.class);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder);
    // first retry succeeds
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));

    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));

    // second retry keeps failing
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldReduceBatchSizeAndRetryIfSocketTimeoutExceptionOccurs() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException()))
        .then(returnBatchSize())
        .then(returnBatchSize())
        .then(returnBatchSize())
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException()))
        .then(returnBatchSize());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(1)
            .batchSize(4)
            .maxRetryAttempts(2)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(16L, 10L);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder);
    // first retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4), 4));
    inOrder.verify(retryRecorder).run();

    // should now be using smaller batch size
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(3, 4), 4));

    // second retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(5, 6), 6));
    inOrder.verify(retryRecorder).run();

    // again batch size has been reduced
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7), 7));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(8), 8));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(9), 9));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldNeverReduceBatchSizeBelowMinimum() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException()));

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(2)
            .batchSize(8)
            .maxRetryAttempts(4)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(SocketTimeoutException.class);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4, 5, 6, 7, 8), 8));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4), 4));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldBeAbleToSpecifyExceptionsThatReduceBatchSizeWithRetrying() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(new BatchReductionException()));

    final var retryRecorder = mock(Runnable.class);

    final var batchSize = new AtomicInteger(10);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(5, 16), batchProcessor)
            .addBatchReductionException(BatchReductionException.class)
            .minBatchSize(2)
            .batchSize(batchSize)
            .maxRetryAttempts(4)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(BatchReductionException.class);

    verify(batchProcessor)
        .process(DocumentBatch.from(List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), 14));
    verifyNoMoreInteractions(batchProcessor);

    verifyNoInteractions(retryRecorder);

    assertThat(batchSize.get()).isEqualTo(5);
  }

  @Test
  void shouldResetRetryCountAfterSuccessfulBatch() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        // two retries in a row
        .thenThrow(new RetryableException())
        .thenThrow(new RetryableException())
        .then(returnBatchSize())
        .then(returnBatchSize())
        .then(returnBatchSize())
        // third retry, but should be for new batch so retry count reset
        .thenThrow(new RetryableException())
        .thenThrow(new RetryableException())
        .then(returnBatchSize());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .minBatchSize(1)
            .batchSize(3)
            .addRetryableException(RetryableException.class)
            .maxRetryAttempts(2)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(18L, 10L);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder);
    // first batch has two retries before it succeeds
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));

    // next batches succeed
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));

    // last batch needs two retries before it succeeds
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldConsumeStatsWhenExecutionSucceeds() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final AtomicReference<PipelineStats> statsConsumer = new AtomicReference<>(null);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .statsConsumer(statsConsumer::set)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future).succeedsWithin(Duration.ofSeconds(5));

    final var stats = statsConsumer.get();

    assertThat(stats)
        .isNotNull()
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(10L, 10L);
  }

  @Test
  void shouldConsumeStatsWhenExecutionFails() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).thenThrow(new RetryableException());

    final AtomicReference<PipelineStats> statsConsumer = new AtomicReference<>(null);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor)
            .statsConsumer(statsConsumer::set)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2)
            .retryDelayMs(1);

    final var future = builder.buildAndExecute();
    assertThat(future).failsWithin(Duration.ofSeconds(5));

    final var stats = statsConsumer.get();

    assertThat(stats)
        .isNotNull()
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(3L, 0L);
  }

  static Stream<Throwable> retryableErrors() {
    return Stream.of(
        new CompletionException(new SocketTimeoutException()),
        new CompletionException(mock(ElasticsearchException.class)),
        new CompletionException(mock(OpenSearchException.class)),
        new CompletionException(new RetryableException()),
        new RuntimeException(new SocketTimeoutException()),
        new SocketTimeoutException(),
        mock(ElasticsearchException.class),
        mock(OpenSearchException.class),
        new RetryableException());
  }

  // little batch supplier that runs through a sequence of numbers
  private BatchSupplier<Integer, Integer> batchSupplier(
      final int startInclusive, final int endInclusive) {
    final var values = IntStream.rangeClosed(startInclusive, endInclusive).toArray();
    return (final Integer searchAfter, final int batchSize) -> {
      int start = startInclusive - 1;
      if (searchAfter != null) {
        start = searchAfter;
      }
      final var docs = new ArrayList<Integer>();
      for (final var value : values) {
        if (value > start) {
          docs.add(value);
          if (docs.size() >= batchSize) {
            break;
          }
        }
      }
      var nextSearchAfter = searchAfter;
      if (!docs.isEmpty()) {
        nextSearchAfter = docs.getLast();
      }

      return CompletableFuture.completedFuture(DocumentBatch.from(docs, nextSearchAfter));
    };
  }

  private static Answer<Object> returnBatchSize() {
    return inv -> {
      final DocumentBatch<?, ?> batch = inv.getArgument(0);
      return CompletableFuture.completedFuture(batch.documents().size());
    };
  }

  static class RetryableException extends RuntimeException {}

  static class BatchReductionException extends RuntimeException {}
}
