/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.FactRow;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.List;

/**
 * The cube-level aggregate (ADR 0009): one accumulator slot per declared meter, in declaration
 * order, each delegating to that meter's bound {@link AggregateFunction}. Every admitted fact folds
 * into all slots at once, so all of a cube's meters travel the pipeline as <em>one</em> value — one
 * fold, one sealed delta, one shuffle stream, one merged cell, one serving row.
 *
 * <p>A {@code null} slot is the identity: {@link #merge}/{@link #mergeInto} skip it, so a delta
 * decoded from an older, shorter layout (fewer slots than declared) merges as "no contribution" —
 * the tolerant-decode seam the parked meter-evolution work builds on. {@link #add} never sees a
 * {@code null} slot because {@link #createAccumulator()} fills every slot.
 *
 * <p>Like every {@link AggregateFunction}, slot merges must never mutate the delta argument: a
 * delta slot may be a read-only decoded view. When a target slot is {@code null}, the delta is
 * folded into a fresh slot accumulator rather than adopted.
 */
public final class CompositeAggregateFunction
    implements AggregateFunction<FactRow, Object[], Object[]> {

  private final AggregateFunction<FactRow, Object, Object>[] slots;

  @SuppressWarnings("unchecked")
  public CompositeAggregateFunction(final List<BoundMeter<?, ?>> meters) {
    slots =
        meters.stream()
            .map(meter -> (AggregateFunction<FactRow, Object, Object>) meter.aggregate())
            .toArray(AggregateFunction[]::new);
  }

  @Override
  public Object[] createAccumulator() {
    final Object[] accumulator = new Object[slots.length];
    for (int i = 0; i < slots.length; i++) {
      accumulator[i] = slots[i].createAccumulator();
    }
    return accumulator;
  }

  @Override
  public Object[] add(final FactRow fact, final Object[] accumulator) {
    for (int i = 0; i < slots.length; i++) {
      accumulator[i] = slots[i].add(fact, accumulator[i]);
    }
    return accumulator;
  }

  @Override
  public Object[] merge(final Object[] a, final Object[] b) {
    final Object[] merged = new Object[slots.length];
    for (int i = 0; i < slots.length; i++) {
      final Object left = i < a.length ? a[i] : null;
      final Object right = i < b.length ? b[i] : null;
      if (left == null && right == null) {
        merged[i] = null;
      } else if (left == null || right == null) {
        // Never adopt an input slot reference: it may be a read-only decoded view. Fold the
        // present side into a fresh slot accumulator instead (mergeInto's delta contract).
        merged[i] = slots[i].mergeInto(slots[i].createAccumulator(), left == null ? right : left);
      } else {
        merged[i] = slots[i].merge(left, right);
      }
    }
    return merged;
  }

  @Override
  public Object[] mergeInto(final Object[] target, final Object[] delta) {
    for (int i = 0; i < slots.length; i++) {
      final Object deltaSlot = i < delta.length ? delta[i] : null;
      if (deltaSlot == null) {
        continue;
      }
      // A null target slot gets a fresh accumulator: the delta slot may be a read-only view and
      // must never be adopted as the running total.
      final Object targetSlot = target[i] == null ? slots[i].createAccumulator() : target[i];
      target[i] = slots[i].mergeInto(targetSlot, deltaSlot);
    }
    return target;
  }

  @Override
  public Object[] getResult(final Object[] accumulator) {
    final Object[] results = new Object[slots.length];
    for (int i = 0; i < slots.length; i++) {
      results[i] = accumulator[i] == null ? null : slots[i].getResult(accumulator[i]);
    }
    return results;
  }
}
