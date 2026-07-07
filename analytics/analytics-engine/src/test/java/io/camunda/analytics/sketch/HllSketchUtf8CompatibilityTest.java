/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.Union;
import org.junit.jupiter.api.Test;

/**
 * Sketch-hash compatibility (ADR 0008, invariant 4): the byte-backed refactor will feed
 * distinct-count meters UTF-8 value slices via {@code HllSketch.update(byte[])} instead of {@code
 * update(String)}. That is only safe if DataSketches hashes a {@code String} exactly as its UTF-8
 * bytes — then sketches built before and after the change describe the same registers, and
 * mixed-era merges cannot double-count. These tests prove that for the pinned DataSketches version;
 * if any of them fails after a library bump, the ADR's plan for distinct-count is invalid.
 */
final class HllSketchUtf8CompatibilityTest {

  /** Matches the production lgK of {@link DistinctCountAggregateFunction}. */
  private static final int LG_K = 12;

  private static final List<String> VALUES =
      List.of(
          "order-1",
          "order-2",
          "",
          " ",
          "Ω-café-日本-🚀",
          "ẞ-straße",
          "русский",
          "-42",
          String.valueOf(Long.MIN_VALUE));

  @Test
  void shouldHashStringsIdenticallyToTheirUtf8Bytes() {
    // given the same logical values fed as Strings and as their UTF-8 bytes
    final HllSketch fromStrings = new HllSketch(LG_K);
    final HllSketch fromBytes = new HllSketch(LG_K);
    for (final String value : VALUES) {
      fromStrings.update(value);
      fromBytes.update(value.getBytes(UTF_8));
    }

    // then the sketches are bit-identical — same coupons, not merely close estimates
    assertThat(fromBytes.toCompactByteArray()).isEqualTo(fromStrings.toCompactByteArray());
    assertThat(fromBytes.getEstimate()).isEqualTo(fromStrings.getEstimate());
  }

  @Test
  void shouldHashALargeStreamIdenticallyThroughBothUpdatePaths() {
    // given enough distinct values to leave the exact (list/set) promotion modes behind
    final HllSketch fromStrings = new HllSketch(LG_K);
    final HllSketch fromBytes = new HllSketch(LG_K);
    for (int i = 0; i < 10_000; i++) {
      final String value = "instance-" + i + "-日本-" + (i % 7);
      fromStrings.update(value);
      fromBytes.update(value.getBytes(UTF_8));
    }

    // then still bit-identical in dense HLL mode
    assertThat(fromBytes.toCompactByteArray()).isEqualTo(fromStrings.toCompactByteArray());
  }

  @Test
  void shouldIgnoreEmptyInputConsistentlyOnBothUpdatePaths() {
    // given only the empty value on each path (update(String) skips empty, update(byte[]) skips
    // length 0 — pinned so the byte-fed refactor keeps the same skip semantics)
    final HllSketch fromString = new HllSketch(LG_K);
    final HllSketch fromBytes = new HllSketch(LG_K);
    fromString.update("");
    fromBytes.update(new byte[0]);

    // then both sketches saw nothing
    assertThat(fromString.getEstimate()).isEqualTo(0.0);
    assertThat(fromBytes.getEstimate()).isEqualTo(0.0);
    assertThat(fromBytes.toCompactByteArray()).isEqualTo(fromString.toCompactByteArray());
  }

  @Test
  void shouldMergeMixedOldAndNewSketchesWithoutDoubleCounting() {
    // given an "old era" String-fed sketch and a "new era" bytes-fed sketch over overlapping sets
    final HllSketch oldEra = new HllSketch(LG_K);
    final HllSketch newEra = new HllSketch(LG_K);
    for (int i = 0; i < 500; i++) {
      oldEra.update("value-" + i);
    }
    for (int i = 250; i < 750; i++) {
      newEra.update(("value-" + i).getBytes(UTF_8));
    }
    // and the same union built entirely String-fed (the pre-refactor world)
    final HllSketch allStrings = new HllSketch(LG_K);
    for (int i = 250; i < 750; i++) {
      allStrings.update("value-" + i);
    }

    // when both pairs are unioned
    final Union mixed = new Union(LG_K);
    mixed.update(oldEra);
    mixed.update(newEra);
    final Union pure = new Union(LG_K);
    pure.update(oldEra);
    pure.update(allStrings);

    // then the mixed-era union is identical to the pure-String union: overlap deduplicates
    assertThat(mixed.getResult().toCompactByteArray())
        .isEqualTo(pure.getResult().toCompactByteArray());
  }

  @Test
  void shouldMergeAByteFedSketchIntoTheDistinctCountMeterUnchanged() {
    // given a production accumulator fed through DistinctCountAggregateFunction (update(String))
    final DistinctCountAggregateFunction<String> distinct =
        new DistinctCountAggregateFunction<>(Function.identity());
    HllSketch viaFunction = distinct.createAccumulator();
    HllSketch viaBytes = distinct.createAccumulator();
    for (final String value : VALUES) {
      if (!value.isEmpty()) {
        viaFunction = distinct.add(value, viaFunction);
        viaBytes.update(value.getBytes(UTF_8));
      }
    }

    // when a String-fed and a bytes-fed partial of a disjoint set are merged into it
    final HllSketch other = distinct.createAccumulator();
    for (int i = 0; i < 100; i++) {
      other.update("other-" + i);
    }
    final long viaFunctionEstimate =
        distinct.getResult(distinct.merge(viaFunction, other)).estimate();
    final long viaBytesEstimate = distinct.getResult(distinct.merge(viaBytes, other)).estimate();

    // then the meter cannot tell which path fed the accumulator
    assertThat(viaBytesEstimate).isEqualTo(viaFunctionEstimate);
    assertThat(viaBytes.toCompactByteArray()).isEqualTo(viaFunction.toCompactByteArray());
  }
}
