/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import org.h2.jdbcx.JdbcDataSource;

/**
 * One windowed metric, defined once and used by both stages, so the two stages can never drift on
 * the {@code aggId}, window, accumulator, or codecs that must match.
 *
 * <p>Stage 1 (the combiner) uses the fact-facing fields ({@link #factType}, {@link #keySelector},
 * {@link #eventTime}, {@link #coordinate}) to fold the source into per-writer partials and publish
 * them under {@link #aggId}. Stage 2 (the reducer) uses {@link #aggregate} (its merge), the codecs,
 * the window, the {@link #drained} predicate, and {@link #sinkFactory} to merge partials into the
 * serving sink. The {@code sinkFactory} is lazy so Stage 1 never opens the serving DB.
 *
 * @param <F> the fact type the projector emits for this metric
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public record MetricSpec<F extends ProcessExecutionFact, K, ACC>(
    int aggId,
    Class<F> factType,
    AggregateFunction<F, ACC, ?> aggregate,
    KeySelector<F, K> keySelector,
    ToLongFunction<F> eventTime,
    SourceCoordinate<F> coordinate,
    long windowMs,
    long latenessMs,
    RecordValue<K> keyValue,
    RecordValue<ACC> accValue,
    Predicate<ACC> drained,
    Function<JdbcDataSource, ResultSink<Windowed<K>, ACC>> sinkFactory) {

  /** A spec whose facts are the projector's supertype directly (no type routing needed). */
  public static <K, ACC> MetricSpec<ProcessExecutionFact, K, ACC> of(
      final int aggId,
      final AggregateFunction<ProcessExecutionFact, ACC, ?> aggregate,
      final KeySelector<ProcessExecutionFact, K> keySelector,
      final ToLongFunction<ProcessExecutionFact> eventTime,
      final SourceCoordinate<ProcessExecutionFact> coordinate,
      final long windowMs,
      final long latenessMs,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final Predicate<ACC> drained,
      final Function<JdbcDataSource, ResultSink<Windowed<K>, ACC>> sinkFactory) {
    return new MetricSpec<>(
        aggId,
        ProcessExecutionFact.class,
        aggregate,
        keySelector,
        eventTime,
        coordinate,
        windowMs,
        latenessMs,
        keyValue,
        accValue,
        drained,
        sinkFactory);
  }
}
