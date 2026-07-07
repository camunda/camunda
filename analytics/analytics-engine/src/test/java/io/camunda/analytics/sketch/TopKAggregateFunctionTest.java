/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.junit.jupiter.api.Test;

final class TopKAggregateFunctionTest {

  private final TopKAggregateFunction<String> topK =
      new TopKAggregateFunction<>(Function.identity());
  private final ItemsSketchValue codec = new ItemsSketchValue();

  private static ItemsSketch<String> skewed(final TopKAggregateFunction<String> agg) {
    ItemsSketch<String> acc = agg.createAccumulator();
    acc = repeat(agg, acc, "a", 1000);
    acc = repeat(agg, acc, "b", 500);
    acc = repeat(agg, acc, "c", 100);
    // a long tail of singletons, none of which should surface as a heavy hitter
    for (int i = 0; i < 200; i++) {
      acc = agg.add("tail-" + i, acc);
    }
    return acc;
  }

  private static ItemsSketch<String> repeat(
      final TopKAggregateFunction<String> agg,
      ItemsSketch<String> acc,
      final String item,
      final int times) {
    for (int i = 0; i < times; i++) {
      acc = agg.add(item, acc);
    }
    return acc;
  }

  @Test
  void shouldRankHeavyHittersInDescendingFrequency() {
    // given / when
    final TopKResult result = topK.getResult(skewed(topK));

    // then — the heavy hitters lead the ranking in descending frequency (tail singletons, all tied
    // at 1, fill the remaining slots up to k in arbitrary order)
    assertThat(result.items()).extracting(TopKResult.Item::item).startsWith("a", "b", "c");
    assertThat(result.items().get(0).estimate()).isGreaterThanOrEqualTo(1000L);
    assertThat(result.items().get(1).estimate()).isGreaterThanOrEqualTo(500L);
    assertThat(result.items().get(2).estimate()).isGreaterThanOrEqualTo(100L);
  }

  @Test
  void shouldLimitToK() {
    // given — only the two busiest requested
    final TopKAggregateFunction<String> top2 =
        new TopKAggregateFunction<>(
            Function.identity(), 2, TopKAggregateFunction.DEFAULT_MAX_MAP_SIZE);

    // when
    final TopKResult result = top2.getResult(skewed(top2));

    // then
    assertThat(result.items()).extracting(TopKResult.Item::item).containsExactly("a", "b");
  }

  @Test
  void shouldMergePartials() {
    // given — a and c on one side, b on the other
    ItemsSketch<String> left = topK.createAccumulator();
    left = repeat(topK, left, "a", 1000);
    left = repeat(topK, left, "c", 100);
    ItemsSketch<String> right = topK.createAccumulator();
    right = repeat(topK, right, "b", 500);

    // when
    final TopKResult result = topK.getResult(topK.merge(left, right));

    // then
    assertThat(result.items()).extracting(TopKResult.Item::item).containsExactly("a", "b", "c");
  }

  @Test
  void shouldRoundTripThroughRecordValue() {
    // given
    final ItemsSketch<String> acc = skewed(topK);

    // when
    final ItemsSketch<String> restored = codec.fromBytes(codec.toBytes(acc));

    // then the ranking is preserved
    assertThat(topK.getResult(restored).items())
        .extracting(TopKResult.Item::item)
        .startsWith("a", "b", "c");
  }

  @Test
  void shouldMergeIntoTheTargetInPlaceMatchingThePureMerge() {
    // given two skewed partials, and the pure merge of equal partials as the reference
    ItemsSketch<String> target = topK.createAccumulator();
    target = repeat(topK, target, "a", 1000);
    target = repeat(topK, target, "c", 100);
    ItemsSketch<String> delta = topK.createAccumulator();
    delta = repeat(topK, delta, "b", 500);
    final TopKResult pure = topK.getResult(topK.merge(target, delta));

    // when the delta is folded in place
    final ItemsSketch<String> merged = topK.mergeInto(target, delta);

    // then the target itself carries the merged ranking, identical to the pure merge
    assertThat(merged).isSameAs(target);
    assertThat(topK.getResult(merged).items())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyElementsOf(pure.items());
  }
}
