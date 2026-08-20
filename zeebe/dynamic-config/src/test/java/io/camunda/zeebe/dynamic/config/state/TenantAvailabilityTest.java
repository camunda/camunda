/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantAvailabilityTest {

  @Nested
  class Toggling {

    @Test
    void shouldBeEnabledInitially() {
      assertThat(TenantAvailability.enabled().disabled()).isFalse();
    }

    @Test
    void shouldIncrementVersionOnDisable() {
      // given
      final var enabled = TenantAvailability.enabled();

      // when
      final var disabled = enabled.disable();

      // then
      assertThat(disabled.disabled()).isTrue();
      assertThat(disabled.version()).isEqualTo(enabled.version() + 1);
    }

    @Test
    void shouldIncrementVersionOnEnable() {
      // given
      final var disabled = TenantAvailability.enabled().disable();

      // when
      final var enabled = disabled.enable();

      // then
      assertThat(enabled.disabled()).isFalse();
      assertThat(enabled.version()).isEqualTo(disabled.version() + 1);
    }

    @Test
    void shouldNotChangeVersionWhenDisablingAnAlreadyDisabledInstance() {
      // given
      final var disabled = TenantAvailability.enabled().disable();

      // when
      final var stillDisabled = disabled.disable();

      // then
      assertThat(stillDisabled).isEqualTo(disabled);
    }

    @Test
    void shouldNotChangeVersionWhenEnablingAnAlreadyEnabledInstance() {
      // given
      final var enabled = TenantAvailability.enabled();

      // when
      final var stillEnabled = enabled.enable();

      // then
      assertThat(stillEnabled).isEqualTo(enabled);
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldKeepHigherVersionRegardlessOfSide() {
      // given
      final var lower = TenantAvailability.enabled(); // version 0
      final var higher = lower.disable(); // version 1

      // when / then
      assertThat(lower.merge(higher)).isEqualTo(higher);
      assertThat(higher.merge(lower)).isEqualTo(higher);
    }

    @Test
    void shouldPreferThisOnEqualVersion() {
      // given — two independently constructed instances at the same version and value
      final var a = TenantAvailability.enabled();
      final var b = TenantAvailability.enabled();

      // when / then
      assertThat(a.merge(b)).isEqualTo(a);
    }
  }
}
