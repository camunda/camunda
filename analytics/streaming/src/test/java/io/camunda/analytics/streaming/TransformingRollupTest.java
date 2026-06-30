/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;

/**
 * One derived fact feeds two datasets: a base rollup, and — via {@link TransformingRollup} — a
 * second rollup over an enriched fact (extra data from a lookup), without re-deriving the base
 * fact.
 */
final class TransformingRollupTest {

  private record Exec(String region, long durationMs, String customerId) {}

  private record EnrichedExec(String region, long durationMs, String tier) {}

  // a "join table" the enricher reads — in the pipeline this is a ReadOnlyKeyValueStore
  private static final Map<String, String> TIER_OF = Map.of("c1", "gold", "c2", "silver");

  @Test
  void shouldEnrichAFactForAnotherDatasetWithoutReDeriving() {
    // given — derivations counted so we can prove the base fact is produced once
    final int[] derivations = {0};
    final InMemoryRollupStore<String, Long> byRegion =
        new InMemoryRollupStore<>(sumOf(Exec::durationMs));
    final InMemoryRollupStore<String, Long> byTier =
        new InMemoryRollupStore<>(sumOf(EnrichedExec::durationMs));

    // the enricher: expand Exec -> EnrichedExec by looking up the customer's tier
    final Projector<Exec, EnrichedExec> enrichWithTier =
        (exec, out) ->
            out.collect(
                new EnrichedExec(
                    exec.region(),
                    exec.durationMs(),
                    TIER_OF.getOrDefault(exec.customerId(), "?")));

    // base fact derived once, fanned to: the by-region rollup AND the enriching by-tier rollup
    final Projector<Exec, Exec> derive =
        (exec, out) -> {
          derivations[0]++;
          out.collect(exec);
        };

    final StreamProcessor<Exec> processor =
        new StreamProcessor<Exec>()
            .register(
                derive,
                List.of(
                    new PreAggregatingRollup<>(
                        sumOf(Exec::durationMs), Exec::region, byRegion, 1_000),
                    new TransformingRollup<>(
                        enrichWithTier,
                        new PreAggregatingRollup<>(
                            sumOf(EnrichedExec::durationMs), EnrichedExec::tier, byTier, 1_000))));

    // when
    processor.init();
    processor.process(new Exec("EU", 100, "c1"));
    processor.process(new Exec("EU", 50, "c2"));
    processor.process(new Exec("US", 70, "c1"));
    processor.close();

    // then — by region (base fact) and by tier (enriched fact), from one derivation each
    assertThat(byRegion.get("EU")).contains(150L);
    assertThat(byRegion.get("US")).contains(70L);
    assertThat(byTier.get("gold")).contains(170L); // c1: 100 + 70
    assertThat(byTier.get("silver")).contains(50L); // c2
    assertThat(derivations[0]).isEqualTo(3); // one per record — the fact was not re-derived
  }

  private static <T> AggregateFunction<T, Long, Long> sumOf(final ToLongFunction<T> value) {
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
