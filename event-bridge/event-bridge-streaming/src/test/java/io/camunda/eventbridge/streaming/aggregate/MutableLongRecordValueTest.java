/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.Test;

final class MutableLongRecordValueTest {

  private final MutableLongRecordValue codec = new MutableLongRecordValue();

  @Test
  void shouldRoundTripAnAccumulatorAsOwnedCopies() {
    // given
    final MutableLong acc = new MutableLong(1_234_567_890_123L);

    // when
    final byte[] bytes = codec.toBytes(acc);
    final MutableLong restored = codec.fromBytes(bytes);

    // then: the value round-trips into a fresh holder the caller owns (it will mutate it)
    assertThat(restored.value).isEqualTo(1_234_567_890_123L);
    assertThat(restored).isNotSameAs(acc);
    assertThat(codec.fromBytes(bytes)).isNotSameAs(restored);
  }

  @Test
  void shouldStayWireCompatibleWithTheBoxedLongCodec() {
    // given state persisted by the previous boxed-Long sum accumulator codec
    final byte[] legacy = new LongRecordValue().toBytes(42L);

    // then it decodes unchanged, and re-encoding produces identical bytes — a checkpoint written
    // under either codec restores under the other
    assertThat(codec.fromBytes(legacy).value).isEqualTo(42L);
    assertThat(codec.toBytes(new MutableLong(42L))).isEqualTo(legacy);
  }

  @Test
  void shouldReuseTheMergeViewAcrossMergeDecodes() {
    // given two serialized deltas
    final byte[] first = codec.toBytes(new MutableLong(5L));
    final byte[] second = codec.toBytes(new MutableLong(7L));

    // when: decoding for merge-only consumption
    final MutableLong firstView = codec.fromBytesForMerge(first);
    final long firstValue = firstView.value;
    final MutableLong secondView = codec.fromBytesForMerge(second);

    // then: the view is reused (read-and-discard per the merge contract), values read correctly
    assertThat(firstValue).isEqualTo(5L);
    assertThat(secondView).isSameAs(firstView);
    assertThat(secondView.value).isEqualTo(7L);
  }
}
