/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.projection.derive.VariantSignature.Variant;
import org.junit.jupiter.api.Test;

/**
 * The variant signature's identity rules: order-insensitive over distinct elements, loop counts
 * collapsed to buckets (1 / 2–3 / 4+), deterministic under re-folding, and a bounded canonical
 * element list.
 */
final class VariantSignatureTest {

  @Test
  void shouldFoldTheSameElementsToTheSameHashRegardlessOfOrder() {
    // given the same element set added in two different arrival orders
    final Variant ab =
        new VariantSignature("claim-process")
            .add("register", 1)
            .add("payout", 1)
            .add("assess-auto", 1)
            .build();
    final Variant ba =
        new VariantSignature("claim-process")
            .add("assess-auto", 1)
            .add("payout", 1)
            .add("register", 1)
            .build();

    // then hash AND canonical list agree — parallel-branch interleaving cannot split a variant
    assertThat(ab.hash()).isEqualTo(ba.hash());
    assertThat(ab.elements()).isEqualTo(ba.elements());
  }

  @Test
  void shouldBucketLoopCounts() {
    // given the same loop element executed 2, 3, 1 and 4 times
    final Variant twice = new VariantSignature("claim-process").add("assess-manual", 2).build();
    final Variant thrice = new VariantSignature("claim-process").add("assess-manual", 3).build();
    final Variant once = new VariantSignature("claim-process").add("assess-manual", 1).build();
    final Variant many = new VariantSignature("claim-process").add("assess-manual", 4).build();

    // then 2 and 3 passes are the same variant (bucket 2–3) ...
    assertThat(twice.hash()).isEqualTo(thrice.hash());
    assertThat(twice.elements()).isEqualTo("assess-manual×2-3");
    // ... while 1 vs 2 and 3 vs 4 cross bucket edges and differ
    assertThat(once.hash()).isNotEqualTo(twice.hash());
    assertThat(many.hash()).isNotEqualTo(thrice.hash());
    assertThat(many.elements()).isEqualTo("assess-manual×4+");
  }

  @Test
  void shouldDistinguishAPartialElementSet() {
    // given a full path and its prefix (a termination mid-way executes fewer elements)
    final Variant full =
        new VariantSignature("claim-process").add("register", 1).add("payout", 1).build();
    final Variant partial = new VariantSignature("claim-process").add("register", 1).build();

    // then the partial set is its own variant
    assertThat(partial.hash()).isNotEqualTo(full.hash());
  }

  @Test
  void shouldBeDeterministicUnderRefolding() {
    // given the identical inputs folded twice (a replay)
    final Variant first =
        new VariantSignature("claim-process").add("a", 1).add("b", 2).add("c", 5).build();
    final Variant second =
        new VariantSignature("claim-process").add("a", 1).add("b", 2).add("c", 5).build();

    // then both runs agree exactly
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldSortAndAnnotateTheCanonicalElementList() {
    // given elements added out of order with a loop
    final Variant variant =
        new VariantSignature("claim-process").add("zeta", 1).add("alpha", 3).add("mid", 1).build();

    // then the list is lexicographic with the loop bucket annotated
    assertThat(variant.elements()).isEqualTo("alpha×2-3, mid, zeta");
  }

  @Test
  void shouldCapTheCanonicalElementListWithATruncationMarker() {
    // given far more elements than the cap can hold
    final VariantSignature signature = new VariantSignature("claim-process");
    for (int i = 0; i < 100; i++) {
      signature.add("element-with-a-fairly-long-id-" + String.format("%03d", i), 1);
    }
    final Variant variant = signature.build();

    // then the list stays bounded and names how many elements were omitted
    assertThat(variant.elements().length())
        .isLessThanOrEqualTo(VariantSignature.MAX_ELEMENTS_LENGTH);
    assertThat(variant.elements()).matches(".*… \\(\\+\\d+ more\\)$");
  }

  @Test
  void shouldReturnNullForAnEmptyElementSet() {
    // given / when / then — no executed elements means no variant (fields stay unset)
    assertThat(new VariantSignature("claim-process").build()).isNull();
  }

  @Test
  void shouldSeparateIdenticalElementSetsOfDifferentProcesses() {
    // given two processes whose instances executed the exact same element ids (copied models,
    // default Modeler ids) — the variant dictionary is keyed by hash alone, so a shared hash
    // would let the processes clobber each other's catalog row
    final Variant claim =
        new VariantSignature("claim-process").add("StartEvent_1", 1).add("Activity_1", 1).build();
    final Variant order =
        new VariantSignature("order-process").add("StartEvent_1", 1).add("Activity_1", 1).build();

    // then the process id participates in the signature
    assertThat(claim.hash()).isNotEqualTo(order.hash());
    assertThat(claim.elements()).isEqualTo(order.elements());
  }
}
