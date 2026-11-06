/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;

class AsyncDocumentPipelineTest {

  @Test
  void shouldThrowExceptionIfBatchSizeTooLarge() {
    assertThatThrownBy(
            () ->
                AsyncDocumentPipeline.builder(
                        batchSupplier(1, 10), mock(BatchProcessor.class), Runnable::run)
                    .batchSize(10_001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowExceptionIfBatchSizeTooSmall() {
    assertThatThrownBy(
            () ->
                AsyncDocumentPipeline.builder(
                        batchSupplier(1, 10), mock(BatchProcessor.class), Runnable::run)
                    .batchSize(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowExceptionIfRetriesLessThanZero() {
    assertThatThrownBy(
            () ->
                AsyncDocumentPipeline.builder(
                        batchSupplier(1, 10), mock(BatchProcessor.class), Runnable::run)
                    .maxRetryAttempts(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowExceptionIfRetryDelayIsLessThanZero() {
    assertThatThrownBy(
            () ->
                AsyncDocumentPipeline.builder(
                        batchSupplier(1, 10), mock(BatchProcessor.class), Runnable::run)
                    .retryDelayMs(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldProcessInBatches() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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

    verifyNoMoreInteractions(batchProcessor);
  }

  @Test
  void shouldUseExecutor() {
    // given
    final var executorInvocations = new AtomicInteger();
    final Executor countingExecutor =
        task -> {
          executorInvocations.incrementAndGet();
          task.run();
        };
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    // when
    final var future =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, countingExecutor)
            .minBatchSize(1)
            .batchSize(3)
            .buildAndExecute();

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(10L, 10L);
    assertThat(executorInvocations).hasPositiveValue();
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
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor, Runnable::run)
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

    verifyNoMoreInteractions(batchSupplier, batchProcessor);
  }

  @Test
  void shouldCompleteLastBatchIfRetryableErrorOccursInProcessing() {
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
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor, Runnable::run)
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
    inOrder.verify(batchSupplier).supply(6, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8), 8));

    verifyNoMoreInteractions(batchSupplier, batchProcessor, retryRecorder);
  }

  @Test
  void shouldFailIfNonRecoverableErrorOccursReadingBatches() {
    final BatchSupplier<Integer, Integer> batchSupplier = mock(BatchSupplier.class);
    when(batchSupplier.supply(any(), anyInt())).thenThrow(new RuntimeException("simulated error"));

    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor, Runnable::run)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .withMessage("simulated error");

    verify(batchSupplier).supply(any(), anyInt());
    verifyNoInteractions(batchProcessor);
  }

  @ParameterizedTest
  @MethodSource("retryableErrors")
  void shouldRetryIfRecoverableErrorOccursReadingBatches(final Throwable retryableError) {
    final BatchSupplier<Integer, Integer> batchSupplier = mock(BatchSupplier.class);

    when(batchSupplier.supply(any(), anyInt()))
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .thenReturn(CompletableFuture.completedFuture(DocumentBatch.from(List.of(1, 2, 3), 3)))
        .thenReturn(CompletableFuture.completedFuture(DocumentBatch.from(List.of(4, 5, 6), 6)))
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .thenReturn(CompletableFuture.completedFuture(DocumentBatch.from(List.of(7, 8, 9), 9)))
        .thenReturn(CompletableFuture.completedFuture(DocumentBatch.from(List.of(10), 10)));

    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor, Runnable::run)
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
        .containsExactly(10L, 10L);

    final var inOrder = Mockito.inOrder(batchSupplier, batchProcessor, retryRecorder);
    // first retry
    inOrder.verify(batchSupplier).supply(null, 3);
    inOrder.verify(retryRecorder).run();

    inOrder.verify(batchSupplier).supply(null, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));

    inOrder.verify(batchSupplier).supply(3, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(4, 5, 6), 6));

    // second retry
    inOrder.verify(batchSupplier).supply(6, 3);
    inOrder.verify(retryRecorder).run();

    inOrder.verify(batchSupplier).supply(6, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8, 9), 9));

    inOrder.verify(batchSupplier).supply(9, 3);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));

    verifyNoMoreInteractions(batchSupplier, batchProcessor, retryRecorder);
  }

  @Test
  void shouldFailIfNonRecoverableErrorOccursProcessingBatches() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).thenThrow(new RuntimeException("simulated error"));

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .withMessage("simulated error");

    verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3), 3));
    verifyNoMoreInteractions(batchProcessor);
  }

  @ParameterizedTest
  @MethodSource("retryableErrors")
  void shouldRetryIfRecoverableErrorOccursProcessingBatches(final Throwable retryableError) {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .then(returnBatchSize())
        .then(returnBatchSize())
        .thenReturn(CompletableFuture.failedFuture(retryableError))
        .then(returnBatchSize());

    final var retryRecorder = mock(Runnable.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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

    verifyNoMoreInteractions(batchProcessor, retryRecorder);
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
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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

    verifyNoMoreInteractions(batchProcessor, retryRecorder);
  }

  @Test
  void shouldFailWhenEncounteringExceptionWithCauseLoop() {
    final BatchSupplier<Integer, Integer> batchSupplier = mock(BatchSupplier.class);
    final var exception1 = mock(RuntimeException.class);
    final var exception2 = mock(RuntimeException.class);
    when(exception1.getCause()).thenReturn(exception2);
    when(exception2.getCause()).thenReturn(exception1);

    when(batchSupplier.supply(any(), anyInt())).thenThrow(exception1);

    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier, batchProcessor, Runnable::run)
            .minBatchSize(1)
            .batchSize(3)
            .maxRetryAttempts(2);

    final var future = builder.buildAndExecute();
    assertThat(future).failsWithin(Duration.ofSeconds(5));

    verify(batchSupplier).supply(any(), anyInt());
    verifyNoInteractions(batchProcessor);
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
    final var logger = mock(Logger.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
            .minBatchSize(1)
            .batchSize(4)
            .maxRetryAttempts(2)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder)
            .logger(logger);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting("totalDocumentsRead", "totalDocumentsProcessed")
        .containsExactly(16L, 10L);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder, logger);
    // first retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4), 4));
    verifyRetryLogged(inOrder.verify(logger));
    inOrder.verify(retryRecorder).run();

    // should now be using smaller batch size
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(3, 4), 4));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(5, 6), 6));

    // second retry
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7, 8), 8));
    verifyRetryLogged(inOrder.verify(logger));
    inOrder.verify(retryRecorder).run();

    // again batch size has been reduced
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(7), 7));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(8), 8));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(9), 9));
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(10), 10));

    verifyNoMoreInteractions(batchProcessor, retryRecorder);
  }

  @Test
  void shouldNeverReduceBatchSizeBelowMinimum() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException()));

    final var retryRecorder = mock(Runnable.class);
    final var logger = mock(Logger.class);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
            .minBatchSize(2)
            .batchSize(8)
            .maxRetryAttempts(4)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder)
            .logger(logger);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(SocketTimeoutException.class);

    final var inOrder = Mockito.inOrder(batchProcessor, retryRecorder, logger);
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4, 5, 6, 7, 8), 8));
    verifyRetryLogged(inOrder.verify(logger));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2, 3, 4), 4));
    verifyRetryLogged(inOrder.verify(logger));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    verifyRetryLogged(inOrder.verify(logger, never()));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));
    verifyRetryLogged(inOrder.verify(logger, never()));
    inOrder.verify(retryRecorder).run();
    inOrder.verify(batchProcessor).process(DocumentBatch.from(List.of(1, 2), 2));

    verifyNoMoreInteractions(batchProcessor, retryRecorder);
  }

  @Test
  void shouldBeAbleToSpecifyExceptionsThatReduceBatchSizeWithRetrying() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);

    when(batchProcessor.process(any()))
        .thenReturn(CompletableFuture.failedFuture(new BatchReductionException()));

    final var retryRecorder = mock(Runnable.class);
    final var logger = mock(Logger.class);

    final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(10, 2);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(5, 16), batchProcessor, Runnable::run)
            .addBatchReductionException(BatchReductionException.class)
            .batchSize(batchSize)
            .maxRetryAttempts(4)
            .retryDelayMs(1)
            .retryRecorder(retryRecorder)
            .logger(logger);

    final var future = builder.buildAndExecute();
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(BatchReductionException.class);

    verify(batchProcessor)
        .process(DocumentBatch.from(List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), 14));
    verifyNoMoreInteractions(batchProcessor);

    verifyRetryLogged(verify(logger));

    verifyNoInteractions(retryRecorder);

    assertThat(batchSize.current()).isEqualTo(5);
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
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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

    verifyNoMoreInteractions(batchProcessor, retryRecorder);
  }

  @Test
  void shouldConsumeStatsWhenExecutionSucceeds() {
    final BatchProcessor<Integer, Integer> batchProcessor = mock(BatchProcessor.class);
    when(batchProcessor.process(any())).then(returnBatchSize());

    final AtomicReference<PipelineStats> statsConsumer = new AtomicReference<>(null);

    final var builder =
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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
        AsyncDocumentPipeline.builder(batchSupplier(1, 10), batchProcessor, Runnable::run)
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

  private static void verifyRetryLogged(final Logger logger) {
    logger.warn(startsWith("Adjusting doc pipeline batch size"), any(Object[].class));
  }

  static class RetryableException extends RuntimeException {}

  static class BatchReductionException extends RuntimeException {}

  @Nested
  class AdaptiveBatchSizeTest {

    @Test
    void shouldStartAtTheConfiguredBatchSize() {
      // given / when
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 10);

      // then
      assertThat(batchSize.current()).isEqualTo(100);
      assertThat(batchSize.getConfiguredBatchSize()).isEqualTo(100);
      assertThat(batchSize.getConfiguredMinBatchSize()).isEqualTo(10);
      assertThat(batchSize.atMinimumSize()).isFalse();
    }

    @Test
    void shouldHalveBatchSizeOnReduce() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 1);

      // when
      final var previousSize = batchSize.reduce();

      // then - the previous size is returned so callers can log what changed
      assertThat(previousSize).isEqualTo(100);
      assertThat(batchSize.current()).isEqualTo(50);
    }

    @Test
    void shouldKeepHalvingOnEveryReduce() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 1);

      // when
      batchSize.reduce();
      batchSize.reduce();
      batchSize.reduce();

      // then
      assertThat(batchSize.current()).isEqualTo(12);
    }

    @Test
    void shouldRoundDownWhenHalvingAnOddBatchSize() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(5, 1);

      // when
      batchSize.reduce();

      // then
      assertThat(batchSize.current()).isEqualTo(2);
    }

    @Test
    void shouldNotReduceBelowTheMinimumBatchSize() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 30);

      // when - a store that refuses every write drives the size all the way down
      IntStream.range(0, 10).forEach(ignored -> batchSize.reduce());

      // then
      assertThat(batchSize.current()).isEqualTo(30);
      assertThat(batchSize.atMinimumSize()).isTrue();
    }

    @Test
    void shouldReportAtMinimumSizeOnceReducedToTheMinimum() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 50);

      // when
      batchSize.reduce();

      // then - the pipeline uses this to stop trying to make failing writes smaller
      assertThat(batchSize.current()).isEqualTo(50);
      assertThat(batchSize.atMinimumSize()).isTrue();
    }

    @Test
    void shouldNotReduceBelowTheConfiguredBatchSizeWhenMinimumIsLarger() {
      // given - a misconfiguration where the minimum exceeds the batch size
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(5, 10);

      // when
      final var previousSize = batchSize.reduce();

      // then - the batch size is already as small as it is allowed to get
      assertThat(previousSize).isEqualTo(5);
      assertThat(batchSize.current()).isEqualTo(5);
      assertThat(batchSize.atMinimumSize()).isTrue();
    }

    @Test
    void shouldReturnToTheConfiguredBatchSizeOnReset() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 1);
      batchSize.reduce();
      batchSize.reduce();

      // when
      batchSize.reset();

      // then
      assertThat(batchSize.current()).isEqualTo(100);
      assertThat(batchSize.atMinimumSize()).isFalse();
    }

    @Test
    void shouldReduceAgainAfterAReset() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 1);
      batchSize.reduce();
      batchSize.reset();

      // when
      batchSize.reduce();

      // then
      assertThat(batchSize.current()).isEqualTo(50);
    }

    @Test
    void shouldKeepConfiguredValuesWhenReducing() {
      // given
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(100, 10);

      // when
      batchSize.reduce();

      // then - the configured size is what a later reset has to restore
      assertThat(batchSize.getConfiguredBatchSize()).isEqualTo(100);
      assertThat(batchSize.getConfiguredMinBatchSize()).isEqualTo(10);
    }

    @Test
    void shouldAllowTheMaximumBatchSize() {
      // given / when
      final var batchSize = new AsyncDocumentPipeline.AdaptiveBatchSize(10_000, 1);

      // then
      assertThat(batchSize.current()).isEqualTo(10_000);
    }

    @Test
    void shouldRejectBatchSizeAboveTheMaximum() {
      // given / when / then
      assertThatThrownBy(() -> new AsyncDocumentPipeline.AdaptiveBatchSize(10_001, 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Batch size cannot be greater than 10000");
    }

    @Test
    void shouldRejectZeroBatchSize() {
      // given / when / then
      assertThatThrownBy(() -> new AsyncDocumentPipeline.AdaptiveBatchSize(0, 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Batch size must be greater than zero");
    }

    @Test
    void shouldRejectNegativeBatchSize() {
      // given / when / then
      assertThatThrownBy(() -> new AsyncDocumentPipeline.AdaptiveBatchSize(-1, 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Batch size must be greater than zero");
    }

    @Test
    void shouldRejectZeroMinBatchSize() {
      // given / when / then
      assertThatThrownBy(() -> new AsyncDocumentPipeline.AdaptiveBatchSize(100, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Min batch size must be greater than 0");
    }

    @Test
    void shouldRejectNegativeMinBatchSize() {
      // given / when / then
      assertThatThrownBy(() -> new AsyncDocumentPipeline.AdaptiveBatchSize(100, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Min batch size must be greater than 0");
    }
  }
}
