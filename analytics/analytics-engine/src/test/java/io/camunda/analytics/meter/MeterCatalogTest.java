/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import java.util.List;
import java.util.Map;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.junit.jupiter.api.Test;

final class MeterCatalogTest {

  private final MeterCatalog catalog = MeterCatalog.withDefaults();

  private static FactRow row(final Map<String, Object> fields) {
    return fields::get;
  }

  /** Fold facts through the bound meter, then round-trip the accumulator through its codec. */
  private static <ACC, OUT> ACC foldAndRoundTrip(
      final BoundMeter<ACC, OUT> bound, final List<FactRow> facts) {
    final AggregateFunction<FactRow, ACC, OUT> aggregate = bound.aggregate();
    ACC acc = aggregate.createAccumulator();
    for (final FactRow fact : facts) {
      acc = aggregate.add(fact, acc);
    }
    final RecordValue<ACC> codec = bound.accumulatorCodec();
    return codec.fromBytes(codec.toBytes(acc));
  }

  @Test
  void shouldCountFacts() {
    // given three facts of any shape
    final List<FactRow> facts = List.of(row(Map.of()), row(Map.of()), row(Map.of()));

    // when
    final Object acc = foldAndRoundTrip(catalog.bind(Meter.of("n", MeterCatalog.COUNT)), facts);

    // then
    assertThat(acc).isEqualTo(3L);
  }

  @Test
  void shouldSumAndLevelSignedMeasure() {
    // given
    final List<FactRow> amounts =
        List.of(row(Map.of("amount", 10L)), row(Map.of("amount", 20L)), row(Map.of("amount", 30L)));
    final List<FactRow> deltas =
        List.of(row(Map.of("delta", 1L)), row(Map.of("delta", 1L)), row(Map.of("delta", -1L)));

    // when
    final Object sum =
        foldAndRoundTrip(catalog.bind(Meter.of("total", MeterCatalog.SUM, "amount")), amounts);
    final Object level =
        foldAndRoundTrip(catalog.bind(Meter.of("open", MeterCatalog.LEVEL, "delta")), deltas);

    // then
    assertThat(sum).isEqualTo(60L);
    assertThat(level).isEqualTo(1L);
  }

  @Test
  void shouldComputeExecutionTimeStats() {
    // given
    final List<FactRow> facts =
        List.of(
            row(Map.of("durationMs", 10L)),
            row(Map.of("durationMs", 20L)),
            row(Map.of("durationMs", 30L)));

    // when
    final Object acc =
        foldAndRoundTrip(
            catalog.bind(Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs")), facts);

    // then count/total/min/max survive the round-trip (average is derived on read)
    assertThat(acc).isEqualTo(new ExecutionTimeAccumulator(3L, 60L, 10L, 30L));
  }

  @Test
  void shouldBucketMeasureIntoHistogram() {
    // given thresholds [100, 1000): buckets [<100, 100..1000, >=1000]
    final Meter meter =
        new Meter(
            "durationHistogram",
            MeterCatalog.HISTOGRAM,
            "durationMs",
            Map.of("thresholds", "100,1000"));
    final List<FactRow> facts =
        List.of(
            row(Map.of("durationMs", 50L)),
            row(Map.of("durationMs", 500L)),
            row(Map.of("durationMs", 5000L)));

    // when
    final Object acc = foldAndRoundTrip(catalog.bind(meter), facts);

    // then one fact lands in each bucket
    assertThat((long[]) acc).containsExactly(1L, 1L, 1L);
  }

  @Test
  void shouldApproximatePercentilesAndSurviveCodec() {
    // given values 1..100 over the median-ranked KLL sketch
    final List<FactRow> facts =
        java.util.stream.LongStream.rangeClosed(1, 100)
            .mapToObj(v -> row(Map.of("durationMs", v)))
            .toList();

    // when
    final KllDoublesSketch sketch =
        (KllDoublesSketch)
            foldAndRoundTrip(
                catalog.bind(Meter.of("p", MeterCatalog.PERCENTILE, "durationMs")), facts);

    // then the median estimate survives the round-trip and is close to 50
    assertThat(sketch.getQuantile(0.5)).isBetween(40.0, 60.0);
  }

  @Test
  void shouldApproximateDistinctCount() {
    // given four facts with three distinct ids
    final List<FactRow> facts =
        List.of(
            row(Map.of("id", "a")),
            row(Map.of("id", "a")),
            row(Map.of("id", "b")),
            row(Map.of("id", "c")));

    // when
    final HllSketch sketch =
        (HllSketch)
            foldAndRoundTrip(catalog.bind(Meter.of("d", MeterCatalog.DISTINCT, "id")), facts);

    // then
    assertThat(sketch.getEstimate()).isBetween(2.5, 3.5);
  }

  @Test
  void shouldRankHeavyHitters() {
    // given "a" dominates
    final List<FactRow> facts =
        List.of(
            row(Map.of("id", "a")),
            row(Map.of("id", "a")),
            row(Map.of("id", "a")),
            row(Map.of("id", "b")));

    // when
    @SuppressWarnings("unchecked")
    final ItemsSketch<String> sketch =
        (ItemsSketch<String>)
            foldAndRoundTrip(catalog.bind(Meter.of("t", MeterCatalog.TOP_K, "id")), facts);

    // then
    assertThat(sketch.getEstimate("a")).isGreaterThanOrEqualTo(3L);
  }

  @Test
  void shouldRejectUnknownMeterType() {
    assertThatThrownBy(() -> catalog.bind(Meter.of("x", "nope")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown meter type");
  }

  @Test
  void shouldRejectMeasureMeterWithoutMeasureField() {
    assertThatThrownBy(() -> catalog.bind(Meter.of("s", MeterCatalog.SUM)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires a measure field");
  }

  @Test
  void shouldExposeAllBuiltInKinds() {
    assertThat(catalog.ids())
        .contains(
            MeterCatalog.COUNT,
            MeterCatalog.SUM,
            MeterCatalog.LEVEL,
            MeterCatalog.EXECUTION_TIME,
            MeterCatalog.HISTOGRAM,
            MeterCatalog.PERCENTILE,
            MeterCatalog.DISTINCT,
            MeterCatalog.TOP_K);
  }
}
