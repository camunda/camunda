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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ClusterBehaviorTest {

  private static final SemanticVersion V1 = new SemanticVersion(8, 11, 0, null, null);
  private static final SemanticVersion V2 = new SemanticVersion(8, 12, 0, null, null);

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
  void shouldBuildTheEmptySetWithTheGivenVersion() {
    // when
    final var behavior = ClusterBehavior.of(V1);

    // then
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
    assertThat(behavior.capabilityBitmask()).isEmpty();
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
    assertThat(IntStream.range(0, 300).filter(behavior::isCapabilityEnabled).toArray())
        .containsExactly(ordinals);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 128}) // the first stored word is 0, then 2
  void shouldCheckCapabilitiesAcrossWordsAndGaps(final int offset) {
    // given every ordinal below the offset, then offset + 0, 1, 2, 63 (the last bit of a word), 64,
    // 65, 127, 200 and 1000
    final int[] above =
        IntStream.of(0, 1, 2, 63, 64, 65, 127, 200, 1000).map(o -> offset + o).toArray();
    final int[] ordinals = concat(range(0, offset), above);

    // when
    final var behavior = ClusterBehavior.ofOrdinals(V1, ordinals);

    // then
    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(offset + 3);
    assertThat(behavior.firstWord()).isEqualTo(offset / 64);
    assertThat(behavior.lastWord()).isEqualTo((offset + 1000) / 64);
    assertThat(IntStream.range(0, offset + 1100).filter(behavior::isCapabilityEnabled).toArray())
        .containsExactly(ordinals);
    assertThat(behavior.isCapabilityEnabled(100_000)).isFalse();
  }

  @Test
  void shouldBuildTheSameSetWhateverTheOrderAndDuplicates() {
    // given
    final var sorted = ClusterBehavior.ofOrdinals(V1, concat(range(0, 146), 148, 150));

    // when descending, with duplicates
    final int[] descending = IntStream.range(0, 146).map(i -> 145 - i).toArray();
    final var shuffled =
        ClusterBehavior.ofOrdinals(V1, concat(new int[] {150, 148, 150}, descending));

    // then
    assertThat(shuffled).isEqualTo(sorted).hasSameHashCodeAs(sorted);
  }

  @Test
  void shouldNotBeEqualToASetWithADifferentContent() {
    // when, then a different first unsupported ordinal with the same (empty) words
    assertThat(ClusterBehavior.empty()).isNotEqualTo(ClusterBehavior.ofOrdinals(V1, range(0, 64)));
    // the same first unsupported ordinal with different words
    assertThat(ClusterBehavior.ofOrdinals(V1, 0, 2))
        .isNotEqualTo(ClusterBehavior.ofOrdinals(V1, 0, 2, 3));
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
  void shouldIgnoreTheVersionInEqualsAndHashCode() {
    // when
    final var v1 = ClusterBehavior.ofOrdinals(V1, 0, 2);
    final var v2 = ClusterBehavior.ofOrdinals(V2, 0, 2);

    // then
    assertThat(v1).isEqualTo(v2).hasSameHashCodeAs(v2);
  }

  @Test
  void shouldOnlyKeepTheReleaseOfTheVersion() {
    // when
    final var behavior =
        ClusterBehavior.ofOrdinals(new SemanticVersion(8, 11, 0, "alpha1", "build.5"), 0);

    // then
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
  }

  @Test
  void shouldRejectANegativeOrdinalInTheFactory() {
    // when, then
    assertThatThrownBy(() -> ClusterBehavior.ofOrdinals(V1, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectANegativeOrdinalInTheCapabilityCheck() {
    // when, then
    assertThatThrownBy(() -> ClusterBehavior.empty().isCapabilityEnabled(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
