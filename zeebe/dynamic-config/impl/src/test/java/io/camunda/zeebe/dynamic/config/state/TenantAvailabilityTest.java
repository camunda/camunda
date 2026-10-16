/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.config.state.TenantAvailability.State;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantAvailabilityTest {

  @Nested
  class Toggling {

    @Test
    void shouldBeEnabledInitially() {
      assertThat(TenantAvailability.enabled().state()).isEqualTo(State.ENABLED);
    }

    @Test
    void shouldIncrementVersionOnDisable() {
      // given
      final var enabled = TenantAvailability.enabled();

      // when
      final var disabled = enabled.disable();

      // then
      assertThat(disabled.state()).isEqualTo(State.DISABLED);
      assertThat(disabled.version()).isEqualTo(enabled.version() + 1);
    }

    @Test
    void shouldIncrementVersionOnEnable() {
      // given
      final var disabled = TenantAvailability.enabled().disable();

      // when
      final var enabled = disabled.enable();

      // then
      assertThat(enabled.state()).isEqualTo(State.ENABLED);
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
  class Removal {

    @Test
    void shouldRemoveADisabledTenantAndBumpTheVersion() {
      // given
      final var disabled = TenantAvailability.enabled().disable();

      // when
      final var removed = disabled.remove();

      // then
      assertThat(removed.state()).isEqualTo(State.REMOVED);
      assertThat(removed.version())
          .describedAs("bumped, so the removal wins over a peer that has not seen it")
          .isEqualTo(disabled.version() + 1);
    }

    /** {@link State#REMOVED} is terminal; see its javadoc for why that matters. */
    @Test
    void shouldNotBeReEnabled() {
      // given
      final var removed = TenantAvailability.enabled().disable().remove();

      // when / then
      assertThat(removed.enable()).isSameAs(removed);
      assertThat(removed.enable().state()).isEqualTo(State.REMOVED);
    }

    @Test
    void shouldStayRemovedWhenDisabledAgain() {
      // given
      final var removed = TenantAvailability.enabled().disable().remove();

      // when / then — the availability initializer re-derives this on every initialization
      assertThat(removed.disable()).isSameAs(removed);
    }

    @Test
    void shouldBeUnchangedWhenAlreadyRemoved() {
      // given
      final var removed = TenantAvailability.enabled().disable().remove();

      // when / then
      assertThat(removed.remove()).isSameAs(removed);
    }

    /**
     * A removal must survive a merge with a peer that has not seen it yet; see the class javadoc.
     */
    @Test
    void shouldWinOverAStaleCopyOfTheSameTenant() {
      // given
      final var stale = TenantAvailability.enabled().disable();
      final var removed = stale.remove();

      // when / then
      assertThat(removed.merge(stale).state()).isEqualTo(State.REMOVED);
      assertThat(stale.merge(removed).state()).isEqualTo(State.REMOVED);
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
