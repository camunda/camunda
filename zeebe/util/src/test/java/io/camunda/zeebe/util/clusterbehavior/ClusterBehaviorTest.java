/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.clusterbehavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.util.SemanticVersion;
import java.util.BitSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ClusterBehaviorTest {

  private static final int WORD_SIZE = ClusterBehavior.WORD_SIZE;
  private static final SemanticVersion V1 = new SemanticVersion(8, 11, 0, null, null);
  private static final SemanticVersion V2 = new SemanticVersion(8, 12, 0, null, null);

  // The spec example (0 to 17, 20 and 22 with 8-bit words) scaled to the word size: shifted by two
  // words so that it lives in word 2, with first unsupported = 2 * WORD_SIZE + 18.
  private static final int SHIFT = 2 * WORD_SIZE;

  private static BitSet bits(final int... ordinals) {
    final var bits = new BitSet();
    for (final int ordinal : ordinals) {
      bits.set(ordinal);
    }
    return bits;
  }

  private static int[] range(final int fromInclusive, final int toExclusive) {
    return IntStream.range(fromInclusive, toExclusive).toArray();
  }

  private static int[] concat(final int[] a, final int... b) {
    return IntStream.concat(IntStream.of(a), IntStream.of(b)).toArray();
  }

  private static ClusterBehavior specExample(final SemanticVersion version) {
    return new ClusterBehavior(SHIFT + 18, new long[] {0b1010011_1111_1111_1111_1111L}, version);
  }

  @Test
  void shouldComputeFieldsOfSpecExample() {
    final var behavior = specExample(V1);

    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(SHIFT + 18);
    assertThat(behavior.lastSupportedCapability()).isEqualTo(SHIFT + 22);
    assertThat(behavior.firstWord()).isEqualTo(2);
    assertThat(behavior.lastWord()).isEqualTo(2);
    // bits 0 to 17 are the padding ones, then 18 and 19 off, 20 on, 21 off, 22 on
    assertThat(behavior.capabilityBitmask()).containsExactly(0b1010011_1111_1111_1111_1111L);
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
  }

  @Test
  void shouldBeEmptyWithAnUnknownVersion() {
    final var behavior = ClusterBehavior.empty();

    assertThat(behavior.firstUnsupportedCapability()).isZero();
    assertThat(behavior.lastSupportedCapability()).isEqualTo(-1);
    assertThat(behavior.firstWord()).isZero();
    assertThat(behavior.lastWord()).isZero();
    assertThat(behavior.capabilityBitmask()).isEmpty();
    assertThat(behavior.behaviorVersion()).isEqualTo(ClusterBehavior.UNKNOWN_VERSION);
  }

  @Test
  void shouldStoreNothingForAContiguousAlignedSet() {
    final var behavior = new ClusterBehavior(2 * WORD_SIZE, new long[0], V1);

    assertThat(behavior.lastSupportedCapability()).isEqualTo(2 * WORD_SIZE - 1);
    assertThat(behavior.firstWord()).isEqualTo(2);
    assertThat(behavior.capabilityBitmask()).isEmpty();
  }

  @Test
  void shouldCopyTheBitmask() {
    final var behavior = specExample(V1);

    behavior.capabilityBitmask()[0] = 0;

    assertThat(behavior.capabilityBitmask()).containsExactly(0b1010011_1111_1111_1111_1111L);
  }

  @Test
  void shouldIgnoreTheVersionInEqualsAndHashCode() {
    final var a = specExample(V1);
    final var otherVersion = specExample(V2);

    assertThat(a).isEqualTo(otherVersion).hasSameHashCodeAs(otherVersion);
    assertThat(a.toString()).isNotEqualTo(otherVersion.toString());
  }

  @Test
  void shouldNotKeepTheBuildMetadataOfTheVersion() {
    final var behavior = specExample(new SemanticVersion(8, 11, 0, "alpha1", "build.5"));

    assertThat(behavior.behaviorVersion()).isEqualTo(new SemanticVersion(8, 11, 0, "alpha1", null));
  }

  @Test
  void shouldRejectANullVersion() {
    assertThatThrownBy(() -> new ClusterBehavior(0, new long[0], null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldPrintTheVersionAndTheTwoBoundaries() {
    assertThat(specExample(V1))
        .hasToString(
            "ClusterBehavior{behaviorVersion=8.11.0, firstUnsupportedCapability="
                + (SHIFT + 18)
                + ", lastSupportedCapability="
                + (SHIFT + 22)
                + "}");
  }

  @Test
  void shouldBuildTheSpecExampleFromBits() {
    final var behavior =
        ClusterBehavior.of(V1, bits(concat(range(0, SHIFT + 18), SHIFT + 20, SHIFT + 22)));

    assertThat(behavior).isEqualTo(specExample(V1));
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
  }

  @Test
  void shouldBuildTheEmptySetWithTheGivenVersion() {
    final var behavior = ClusterBehavior.of(V1);

    assertThat(behavior).isEqualTo(ClusterBehavior.of(V1, new BitSet()));
    assertThat(behavior.capabilityBitmask()).isEmpty();
    assertThat(behavior.firstUnsupportedCapability()).isZero();
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
    // the version is not part of equality
    assertThat(behavior).isEqualTo(ClusterBehavior.empty());
  }

  @Test
  void shouldStoreNothingForAContiguousAlignedSetFromBits() {
    final var behavior = ClusterBehavior.of(V1, bits(range(0, 2 * WORD_SIZE)));

    assertThat(behavior).isEqualTo(new ClusterBehavior(2 * WORD_SIZE, new long[0], V1));
  }

  @Test
  void shouldBuildTheSameSetWhateverTheOrderAndDuplicates() {
    final var behavior =
        ClusterBehavior.of(
            V1,
            bits(
                SHIFT + 22,
                SHIFT + 20,
                5,
                SHIFT + 22,
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                17,
                17));
    final var sorted =
        ClusterBehavior.of(
            V1,
            bits(
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                17,
                SHIFT + 20,
                SHIFT + 22));

    assertThat(behavior).isEqualTo(sorted).hasSameHashCodeAs(sorted);
    assertThat(behavior.capabilityBitmask()).containsExactly(sorted.capabilityBitmask());
  }

  @Test
  void shouldCheckCapabilitiesAcrossWordsAndGaps() {
    final var behavior = ClusterBehavior.of(V1, bits(0, 1, 2, WORD_SIZE, WORD_SIZE + 1, 200, 1000));

    assertThat(behavior.firstUnsupportedCapability()).isEqualTo(3);
    assertThat(behavior.firstWord()).isZero();
    assertThat(behavior.lastWord()).isEqualTo(1000 / WORD_SIZE);
    assertThat(behavior.capabilityBitmask()).hasSize(1000 / WORD_SIZE + 1);
    for (final int enabled : new int[] {0, 1, 2, WORD_SIZE, WORD_SIZE + 1, 200, 1000}) {
      assertThat(behavior.isCapabilityEnabled(enabled)).as("ordinal %d", enabled).isTrue();
    }
    for (final int disabled :
        new int[] {3, WORD_SIZE - 1, WORD_SIZE + 2, 199, 201, 999, 1001, 100_000}) {
      assertThat(behavior.isCapabilityEnabled(disabled)).as("ordinal %d", disabled).isFalse();
    }
  }

  @Test
  void shouldRejectANegativeOrdinalInTheCapabilityCheck() {
    assertThatThrownBy(() -> ClusterBehavior.empty().isCapabilityEnabled(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldBuildFromCatalogEntries() {
    final var behavior = ClusterBehavior.of(V1, EngineCapabilityCatalog.values());

    for (final var capability : EngineCapabilityCatalog.values()) {
      assertThat(behavior.isCapabilityEnabled(capability)).isTrue();
    }
    assertThat(behavior.behaviorVersion()).isEqualTo(V1);
  }
}
