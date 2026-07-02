/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.streaming.aggregate.AggregateFunction;
import io.camunda.analytics.streaming.aggregate.InMemoryRollupStore;
import io.camunda.analytics.streaming.aggregate.PreAggregatingRollup;
import io.camunda.analytics.streaming.fold.Projector;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Shows the two compositions the library supports: one fact fanned to several aggregations, and
 * several facts derived from one record by several projectors.
 */
final class StreamProcessorTest {

  // a raw source record
  private record Order(String region, String product, long amount, boolean completed) {}

  // facts derived from it
  private record Sale(String region, String product, long amount) {}

  private record Touch(String region) {}

  private static final AggregateFunction<Sale, Long, Long> SUM_AMOUNT = sumOf(Sale::amount);
  private static final AggregateFunction<Touch, Long, Long> COUNT = sumOf(t -> 1L);

  @Test
  void shouldFanOneFactToMultipleAggregations() {
    // given — completed orders become Sale facts, aggregated by region AND by product
    final InMemoryRollupStore<String, Long> byRegion = new InMemoryRollupStore<>(SUM_AMOUNT);
    final InMemoryRollupStore<String, Long> byProduct = new InMemoryRollupStore<>(SUM_AMOUNT);

    final Projector<Order, Sale> toSale =
        (order, out) -> {
          if (order.completed()) {
            out.collect(new Sale(order.region(), order.product(), order.amount()));
          }
        };

    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>()
            .register(
                toSale,
                List.of(
                    new PreAggregatingRollup<>(SUM_AMOUNT, Sale::region, byRegion, 1000),
                    new PreAggregatingRollup<>(SUM_AMOUNT, Sale::product, byProduct, 1000)));

    // when
    processor.init();
    processor.process(new Order("EU", "widget", 100, true));
    processor.process(new Order("EU", "gadget", 30, true));
    processor.process(new Order("US", "widget", 70, true));
    processor.process(new Order("EU", "widget", 999, false)); // dropped by the fold
    processor.close();

    // then — one fact, two independent groupings
    assertThat(byRegion.get("EU")).contains(130L); // 100 + 30
    assertThat(byRegion.get("US")).contains(70L);
    assertThat(byProduct.get("widget")).contains(170L); // 100 + 70
    assertThat(byProduct.get("gadget")).contains(30L);
  }

  @Test
  void shouldDeriveDifferentFactTypesFromOneRecordViaMultipleProjectors() {
    // given — two projectors over the same record: one emits Sales, one emits Touches
    final InMemoryRollupStore<String, Long> salesByRegion = new InMemoryRollupStore<>(SUM_AMOUNT);
    final InMemoryRollupStore<String, Long> touchesByRegion = new InMemoryRollupStore<>(COUNT);

    final Projector<Order, Sale> toSale =
        (order, out) -> {
          if (order.completed()) {
            out.collect(new Sale(order.region(), order.product(), order.amount()));
          }
        };
    final Projector<Order, Touch> toTouch = (order, out) -> out.collect(new Touch(order.region()));

    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>()
            .register(
                toSale, new PreAggregatingRollup<>(SUM_AMOUNT, Sale::region, salesByRegion, 1000))
            .register(
                toTouch, new PreAggregatingRollup<>(COUNT, Touch::region, touchesByRegion, 1000));

    // when
    processor.init();
    processor.process(new Order("EU", "widget", 100, true));
    processor.process(new Order("EU", "gadget", 30, false)); // no sale, but still a touch
    processor.close();

    // then — Sales only count completed; Touches count every record
    assertThat(salesByRegion.get("EU")).contains(100L);
    assertThat(touchesByRegion.get("EU")).contains(2L);
  }

  @Test
  void shouldExposeResultsAfterWallClockFlushWithoutClose() {
    // given
    final InMemoryRollupStore<String, Long> byRegion = new InMemoryRollupStore<>(SUM_AMOUNT);
    final Projector<Order, Sale> toSale =
        (order, out) -> out.collect(new Sale(order.region(), order.product(), order.amount()));
    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>()
            .register(toSale, new PreAggregatingRollup<>(SUM_AMOUNT, Sale::region, byRegion, 1000));

    // when — a wall-clock tick flushes buffered partials mid-stream (no close)
    processor.process(new Order("EU", "widget", 100, true));
    assertThat(byRegion.get("EU")).isEmpty(); // still buffered
    processor.flush();

    // then
    assertThat(byRegion.get("EU")).contains(100L);
  }

  @Test
  void shouldDriveAnyCustomStageAgnostically() {
    // given — a stage that is not a projection/rollup at all, just counting records and lifecycle
    final long[] processed = {0};
    final boolean[] initialized = {false};
    final boolean[] closed = {false};
    final Stage<Order> counting =
        new Stage<>() {
          @Override
          public void init() {
            initialized[0] = true;
          }

          @Override
          public void process(final Order record) {
            processed[0]++;
          }

          @Override
          public void close() {
            closed[0] = true;
          }
        };

    final StreamProcessor<Order> processor = new StreamProcessor<Order>().add(counting);

    // when
    processor.init();
    processor.process(new Order("EU", "widget", 1, true));
    processor.process(new Order("US", "gadget", 2, true));
    processor.close();

    // then — the runtime drove the stage's lifecycle without knowing what it does
    assertThat(initialized[0]).isTrue();
    assertThat(processed[0]).isEqualTo(2L);
    assertThat(closed[0]).isTrue();
  }

  private static <T> AggregateFunction<T, Long, Long> sumOf(
      final java.util.function.ToLongFunction<T> value) {
    return new AggregateFunction<>() {
      @Override
      public Long createAccumulator() {
        return 0L;
      }

      @Override
      public Long add(final T item, final Long acc) {
        return acc + value.applyAsLong(item);
      }

      @Override
      public Long merge(final Long a, final Long b) {
        return a + b;
      }

      @Override
      public Long getResult(final Long acc) {
        return acc;
      }
    };
  }
}
