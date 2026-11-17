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
import org.junit.jupiter.api.Test;

final class ClusterBehaviorTest {

  private static final int WORD_SIZE = ClusterBehavior.WORD_SIZE;
  private static final SemanticVersion V1 = new SemanticVersion(8, 11, 0, null, null);
  private static final SemanticVersion V2 = new SemanticVersion(8, 12, 0, null, null);

  // The spec example (0 to 17, 20 and 22 with 8-bit words) scaled to the word size: shifted by two
  // words so that it lives in word 2, with first unsupported = 2 * WORD_SIZE + 18.
  private static final int SHIFT = 2 * WORD_SIZE;

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
}
