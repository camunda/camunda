/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.clusterbehavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.util.SemanticVersion;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ClusterBehaviorTest {

  private static final SemanticVersion V1 = new SemanticVersion(8, 11, 0, null, null);

  private static int[] range(final int fromInclusive, final int toExclusive) {
    return IntStream.range(fromInclusive, toExclusive).toArray();
  }

  private static int[] concat(final int[] a, final int... b) {
    return IntStream.concat(IntStream.of(a), IntStream.of(b)).toArray();
  }

  @Test
  void shouldStoreTheSetFromTheWordOfTheFirstUnsupportedCapability() {
    // given every ordinal up to 145, then 148 and 150: 146 is the first unsupported one, in word 2
    // (ordinals 128 to 191)
    final int[] ordinals = concat(range(0, 146), 148, 150);

    // when
    final var behavior = ClusterBehavior.ofOrdinals(V1, ordinals);

    // then
    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(146);
    assertThat(behavior.lastSupportedCapability()).isEqualTo(150);
    assertThat(behavior.firstWord()).isEqualTo(2);
    assertThat(behavior.lastWord()).isEqualTo(2);
    // word 2: bits 0 to 17 (ordinals 128 to 145) are the padding ones, then 20 (148) and 22 (150)
    assertThat(behavior.capabilityBitmask()).containsExactly(0b101_0011_1111_1111_1111_1111L);
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
  }

  @Test
  void shouldBeEmptyWithAnUnknownVersion() {
    // when
    final var behavior = ClusterBehavior.empty();

    // then
    assertThat(behavior.firstUnsupportedCapability()).isZero();
    assertThat(behavior.lastSupportedCapability()).isEqualTo(-1);
    assertThat(behavior.firstWord()).isZero();
    assertThat(behavior.lastWord()).isEqualTo(-1);
    assertThat(behavior.capabilityBitmask()).isEmpty();
    assertThat(behavior.behaviorVersion()).isEqualTo(ClusterBehavior.UNKNOWN_VERSION);
  }

  @Test
  void shouldStoreNoWordWhenTheSetIsContiguousUpToAWordBoundary() {
    // when every ordinal of words 0 and 1 is in the set
    final var behavior = ClusterBehavior.ofOrdinals(V1, range(0, 128));

    // then
    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(128);
    assertThat(behavior.lastSupportedCapability()).isEqualTo(127);
    assertThat(behavior.firstWord()).isEqualTo(2);
    assertThat(behavior.lastWord()).isEqualTo(1);
    assertThat(behavior.capabilityBitmask()).isEmpty();
  }

  @Test
  void shouldStoreTheWordsAfterAFirstWordThatStartsOnAWordBoundary() {
    // given
    final int[] ordinals = concat(range(0, 64), 66, 192);

    // when
    final var behavior = ClusterBehavior.ofOrdinals(V1, ordinals);

    // then
    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(64);
    assertThat(behavior.capabilityBitmask()).containsExactly(0b100L, 0L, 1L);
  }

  @Test
  void shouldCopyTheBitmask() {
    // given
    final var behavior = ClusterBehavior.ofOrdinals(V1, 0, 2);

    // when
    behavior.capabilityBitmask()[0] = 0;

    // then
    assertThat(behavior.capabilityBitmask()).containsExactly(0b101L);
  }

  @Test
  void shouldRejectANegativeOrdinalInTheFactory() {
    // when, then
    assertThatThrownBy(() -> ClusterBehavior.ofOrdinals(V1, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
