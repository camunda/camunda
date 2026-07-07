/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.frequencies.ItemsSketch.Row;

/**
 * Approximate top-k heavy hitters of a categorical value as a mergeable {@link AggregateFunction},
 * backed by a frequent-items sketch. Parameterized by an extractor that maps a fact to the item
 * whose occurrences are ranked (e.g. the {@code bpmnProcessId}, a customer id); a {@code null} item
 * is ignored. The accumulator is the sketch; its {@code merge} is the sketch's frequency merge, so
 * it is commutative and associative and the top-k is pre-aggregatable across partitions with
 * fixed-size state. Counts are approximate but bounded by an error the sketch reports.
 *
 * <p>{@link #getResult} returns the heavy hitters under {@link ErrorType#NO_FALSE_POSITIVES}, so an
 * item only appears when it is genuinely frequent.
 *
 * @param <F> the fact type
 */
public final class TopKAggregateFunction<F>
    implements AggregateFunction<F, ItemsSketch<String>, TopKResult> {

  /** Default number of heavy hitters to report. */
  public static final int DEFAULT_K = 10;

  /** Default sketch map size (a power of two); bounds memory and the count error. */
  public static final int DEFAULT_MAX_MAP_SIZE = 256;

  private final Function<F, String> item;
  private final int k;
  private final int maxMapSize;

  public TopKAggregateFunction(final Function<F, String> item) {
    this(item, DEFAULT_K, DEFAULT_MAX_MAP_SIZE);
  }

  public TopKAggregateFunction(final Function<F, String> item, final int k, final int maxMapSize) {
    this.item = item;
    this.k = k;
    this.maxMapSize = maxMapSize;
  }

  @Override
  public ItemsSketch<String> createAccumulator() {
    return new ItemsSketch<>(maxMapSize);
  }

  @Override
  public ItemsSketch<String> add(final F fact, final ItemsSketch<String> sketch) {
    final String value = item.apply(fact);
    if (value != null) {
      sketch.update(value);
    }
    return sketch;
  }

  @Override
  public ItemsSketch<String> merge(final ItemsSketch<String> a, final ItemsSketch<String> b) {
    final ItemsSketch<String> merged = new ItemsSketch<>(maxMapSize);
    merged.merge(a);
    merged.merge(b);
    return merged;
  }

  /** In place: the frequency merge folds directly into the caller-owned target sketch. */
  @Override
  public ItemsSketch<String> mergeInto(
      final ItemsSketch<String> target, final ItemsSketch<String> delta) {
    return target.merge(delta);
  }

  @Override
  public TopKResult getResult(final ItemsSketch<String> sketch) {
    final Row<String>[] rows = sketch.getFrequentItems(ErrorType.NO_FALSE_POSITIVES);
    final List<TopKResult.Item> items = new ArrayList<>(Math.min(k, rows.length));
    for (int i = 0; i < rows.length && i < k; i++) {
      final Row<String> row = rows[i];
      items.add(
          new TopKResult.Item(
              row.getItem(), row.getEstimate(), row.getLowerBound(), row.getUpperBound()));
    }
    return new TopKResult(items);
  }
}
