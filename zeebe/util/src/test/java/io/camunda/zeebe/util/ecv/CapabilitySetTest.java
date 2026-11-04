/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.ecv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class CapabilitySetTest {

  @Test
  void shouldKeepUnknownCapabilitiesThroughEncoding() {
    // given - bit 70 is not in this binary's catalog, as when 8.11 reads a set written by 8.12
    final var set = CapabilitySet.ofIds(0, 2, 70);

    // when
    final var decoded = CapabilitySet.decode(set.encode());

    // then
    assertThat(decoded).isEqualTo(set);
    assertThat(decoded.unknown()).isEqualTo(CapabilitySet.ofIds(70));
    assertThat(decoded.names())
        .containsExactly("ECV_FRAMEWORK", "POC_812_DEPENDENT_BEHAVIOR", "UNKNOWN_70");
  }

  @Test
  void shouldDecodeEmptyString() {
    assertThat(CapabilitySet.decode("")).isEqualTo(CapabilitySet.EMPTY);
    assertThat(CapabilitySet.EMPTY.encode()).isEqualTo("0");
    assertThat(CapabilitySet.decode("0")).isEqualTo(CapabilitySet.EMPTY);
  }

  @Test
  void shouldCompareAsSuperset() {
    // given
    final var enabled = CapabilitySet.ofIds(0, 1);

    // then
    assertThat(CapabilitySet.ofIds(0, 1, 2).containsAll(enabled)).isTrue();
    assertThat(Capability.base().containsAll(enabled)).isFalse();
    assertThat(enabled.minus(Capability.base()))
        .isEqualTo(CapabilitySet.of(Capability.POC_812_FORMAT_CHANGE));
  }

  @Test
  void shouldFindMissingPrerequisites() {
    assertThat(CapabilitySet.ofIds(0, 2).missingPrerequisites())
        .containsExactly("POC_812_DEPENDENT_BEHAVIOR requires POC_812_FORMAT_CHANGE");
    assertThat(Capability.all().missingPrerequisites()).isEmpty();
  }

  @Test
  void shouldHaveBaseSetOf811Capabilities() {
    assertThat(Capability.base()).isEqualTo(CapabilitySet.of(Capability.ECV_FRAMEWORK));
  }
}
