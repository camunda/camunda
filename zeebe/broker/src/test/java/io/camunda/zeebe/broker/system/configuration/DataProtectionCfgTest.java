/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class DataProtectionCfgTest {

  @Test
  void shouldApplyDefaultsWhenPatternsAndModesAreNull() {
    // when
    final var cfg = new DataProtectionCfg(null, null);

    // then
    assertThat(cfg.patterns()).isEqualTo(DataProtectionCfg.DEFAULT_PATTERNS);
    assertThat(cfg.modes()).isEqualTo(DataProtectionCfg.DEFAULT_MODES);
  }

  @Test
  void shouldAcceptMultiplePatterns() {
    // when
    final var cfg =
        new DataProtectionCfg(List.of("sensitive_.*", "pii_.*"), Set.of(ProtectionMode.REDACT));

    // then
    assertThat(cfg.patterns()).containsExactlyInAnyOrder("sensitive_.*", "pii_.*");
  }

  @ParameterizedTest
  @MethodSource("validModeCombinations")
  void shouldAcceptValidModeCombinations(final Set<ProtectionMode> modes) {
    // when / then -- must not throw
    new DataProtectionCfg(List.of("sensitive_.*"), modes);
  }

  @ParameterizedTest
  @MethodSource("invalidModeCombinations")
  void shouldRejectRedactCombinedWithOtherModes(final Set<ProtectionMode> modes) {
    // when / then
    assertThatThrownBy(() -> new DataProtectionCfg(List.of("sensitive_.*"), modes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("REDACT");
  }

  private static Stream<Set<ProtectionMode>> validModeCombinations() {
    return Stream.of(
        Set.of(ProtectionMode.REDACT),
        Set.of(ProtectionMode.MASK),
        Set.of(ProtectionMode.ENCRYPT),
        Set.of(ProtectionMode.MASK, ProtectionMode.ENCRYPT));
  }

  private static Stream<Set<ProtectionMode>> invalidModeCombinations() {
    return Stream.of(
        Set.of(ProtectionMode.REDACT, ProtectionMode.MASK),
        Set.of(ProtectionMode.REDACT, ProtectionMode.ENCRYPT),
        Set.of(ProtectionMode.REDACT, ProtectionMode.MASK, ProtectionMode.ENCRYPT));
  }
}
