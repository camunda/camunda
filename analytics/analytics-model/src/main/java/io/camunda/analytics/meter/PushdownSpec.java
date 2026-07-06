/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import java.util.List;
import java.util.function.Function;

/**
 * The pushdown capability of a {@link MeterType}: how an <em>additive</em> accumulator maps onto
 * native numeric columns the store can {@code GROUP BY}/{@code SUM}/{@code MIN}/{@code MAX}, so the
 * reduction happens in the engine instead of the application. Present only when the meter's entire
 * accumulator decomposes into numeric columns; a sketch (percentile, distinct, top-k) or any
 * summary that bundles a sketch has no {@code PushdownSpec} and stays a blob + app-merge.
 *
 * <p>The contract is a pair of pure functions over {@link #columns} (in order):
 *
 * <ul>
 *   <li>{@link #decompose} — {@code ACC → the per-cell column values}, used by the writer to store
 *       the accumulator as numeric columns;
 *   <li>{@link #recompose} — {@code the store-aggregated column values → OUT}, used on read to turn
 *       the {@code SUM}/{@code MIN}/{@code MAX} results back into the meter's read-facing result.
 * </ul>
 *
 * <p>Because the store's aggregate mirrors the accumulator's {@code merge} column by column, {@code
 * recompose(aggregate(decompose(a), decompose(b)))} equals {@code getResult(merge(a, b))} — the
 * pushdown is exact for additive meters.
 *
 * @param <ACC> the accumulator type
 * @param <OUT> the read-facing result type
 */
public record PushdownSpec<ACC, OUT>(
    List<PushdownColumn> columns,
    Function<ACC, List<Object>> decompose,
    Function<List<Object>, OUT> recompose) {

  public PushdownSpec {
    columns = List.copyOf(columns);
  }
}
