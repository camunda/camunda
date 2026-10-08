/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchSupplier;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

class ArchiveByIdPipelineTest {
  private static final String SOURCE_INDEX = "source-index";
  private static final String DESTINATION_INDEX = "destination-index";
  private static final int BATCH_SIZE = 100;
  private static final List<IdWithRouting> IDS = List.of(id("1"), id("2"));
  private static final DocumentBatch<IdWithRouting, SearchAfter> BATCH =
      DocumentBatch.from(IDS, new TestSearchAfter());

  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);
  private final Function<String, CompletableFuture<Void>> setIndexLifeCycle = setIndexLifeCycle();

  @Test
  void shouldSearchReindexDeleteThenSetIndexLifeCycleWhenMovingBetweenIndex() {
    // given
    final var idsSupplier = idsSupplier(BATCH);
    final var reindexer = reindexer(2);
    final var deleter = deleter(2);
    final var pipeline = pipeline(reindexer, deleter);

    // when
    final var result =
        pipeline.moveBetweenIndexes(idsSupplier, SOURCE_INDEX, DESTINATION_INDEX, Runnable::run);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));

    final var inOrder = inOrder(idsSupplier, reindexer, deleter, setIndexLifeCycle, metrics);
    inOrder.verify(idsSupplier).supply(null, BATCH_SIZE);
    inOrder.verify(reindexer).apply(SOURCE_INDEX, DESTINATION_INDEX, IDS);
    inOrder.verify(deleter).apply(SOURCE_INDEX, IDS);
    inOrder.verify(setIndexLifeCycle).apply(DESTINATION_INDEX);
    inOrder.verify(metrics).measureArchiveIndexDuration(eq(SOURCE_INDEX), any(), eq(2L));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotSetIndexLifeCycleWhenMoveFails() {
    // given
    final var idsSupplier = idsSupplier(BATCH);
    final var reindexer = reindexer(1);
    final var deleter = deleter(2);
    final var pipeline = pipeline(reindexer, deleter);

    // when
    final var result =
        pipeline.moveBetweenIndexes(idsSupplier, SOURCE_INDEX, DESTINATION_INDEX, Runnable::run);

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(BatchCountMismatchException.class);

    verifyNoInteractions(setIndexLifeCycle);
    final var inOrder = inOrder(idsSupplier, reindexer);
    inOrder.verify(idsSupplier).supply(null, BATCH_SIZE);
    inOrder.verify(reindexer).apply(SOURCE_INDEX, DESTINATION_INDEX, IDS);
    inOrder.verify(reindexer).apply(SOURCE_INDEX, DESTINATION_INDEX, IDS);
    Mockito.verify(metrics).recordArchiverBatchRetry();
    verifyNoInteractions(deleter, setIndexLifeCycle);
    verifyNoMoreInteractions(reindexer, metrics);
  }

  private ArchiveByIdPipeline pipeline(
      final io.camunda.zeebe.util.function.TriFunction<
              String, String, List<IdWithRouting>, CompletableFuture<Integer>>
          reindexer,
      final BiFunction<String, List<IdWithRouting>, CompletableFuture<Integer>> deleter) {
    final var config = new HistoryConfiguration();
    config.setReindexBatchSize(BATCH_SIZE);
    config.setArchiveByIdMaxRetryAttempts(1);
    config.setArchiveByIdRetryDelayMs(1);

    return new ArchiveByIdPipeline(
        config, reindexer, deleter, setIndexLifeCycle, metrics, mock(Logger.class));
  }

  @SuppressWarnings("unchecked")
  private BatchSupplier<IdWithRouting, SearchAfter> idsSupplier(
      final DocumentBatch<IdWithRouting, SearchAfter> batch) {
    final var idsSupplier = mock(BatchSupplier.class);
    when(idsSupplier.supply(null, BATCH_SIZE)).thenReturn(CompletableFuture.completedFuture(batch));
    return idsSupplier;
  }

  @SuppressWarnings("unchecked")
  private io.camunda.zeebe.util.function.TriFunction<
          String, String, List<IdWithRouting>, CompletableFuture<Integer>>
      reindexer(final int processedCount) {
    final var reindexer = mock(io.camunda.zeebe.util.function.TriFunction.class);
    when(reindexer.apply(Mockito.eq(SOURCE_INDEX), Mockito.eq(DESTINATION_INDEX), Mockito.eq(IDS)))
        .thenReturn(CompletableFuture.completedFuture(processedCount));
    return reindexer;
  }

  @SuppressWarnings("unchecked")
  private BiFunction<String, List<IdWithRouting>, CompletableFuture<Integer>> deleter(
      final int processedCount) {
    final var deleter = mock(BiFunction.class);
    when(deleter.apply(Mockito.eq(SOURCE_INDEX), Mockito.eq(IDS)))
        .thenReturn(CompletableFuture.completedFuture(processedCount));
    return deleter;
  }

  @SuppressWarnings("unchecked")
  private Function<String, CompletableFuture<Void>> setIndexLifeCycle() {
    final var setIndexLifeCycle = mock(Function.class);
    when(setIndexLifeCycle.apply(any())).thenReturn(CompletableFuture.completedFuture(null));
    return setIndexLifeCycle;
  }

  private static IdWithRouting id(final String id) {
    return new IdWithRouting(id, null);
  }

  private record TestSearchAfter() implements SearchAfter {}
}
