/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrokerStateTest {

  @Nested
  class Factories {

    @Test
    void shouldInitializeAsActive() {
      // when
      final var state = BrokerState.initializeAsActive();

      // then
      assertThat(state.version()).isZero();
      assertThat(state.state()).isEqualTo(State.ACTIVE);
    }

    @Test
    void shouldInitializeAsUninitialized() {
      // when
      final var state = BrokerState.uninitialized();

      // then
      assertThat(state.version()).isZero();
      assertThat(state.state()).isEqualTo(State.UNINITIALIZED);
    }
  }

  @Nested
  class Updates {

    @Test
    void shouldSetStateAndIncrementVersion() {
      // given
      final var initial = BrokerState.uninitialized();

      // when
      final var updated = initial.setState(State.JOINING);

      // then
      assertThat(updated.state()).isEqualTo(State.JOINING);
      assertThat(updated.version()).isEqualTo(initial.version() + 1);
      assertThat(updated.lastUpdated()).isAfter(initial.lastUpdated());
    }

    @Test
    void shouldReturnSameInstanceWhenStateUnchanged() {
      // given
      final var initial = BrokerState.initializeAsActive();

      // when / then
      assertThat(initial.setState(State.ACTIVE)).isSameAs(initial);
    }
  }

  @Nested
  class Merge {

    @Test
    void shouldReturnThisWhenOtherIsNull() {
      // given
      final var state = BrokerState.initializeAsActive();

      // when
      final var merged = state.merge(null);

      // then
      assertThat(merged).isSameAs(state);
    }

    @Test
    void shouldPickHigherVersion() {
      // given
      final var lower = new BrokerState(1, Instant.EPOCH, State.JOINING);
      final var higher = new BrokerState(2, Instant.EPOCH, State.ACTIVE);

      // when / then — higher version wins regardless of merge direction
      assertThat(lower.merge(higher)).isEqualTo(higher);
      assertThat(higher.merge(lower)).isEqualTo(higher);
    }

    @Test
    void shouldReturnEqualStateWhenSameVersionAndIdenticalContent() {
      // given
      final var a = new BrokerState(3, Instant.EPOCH, State.ACTIVE);
      final var b = new BrokerState(3, Instant.EPOCH, State.ACTIVE);

      // when / then
      assertThat(a.merge(b)).isEqualTo(a);
    }

    @Test
    void shouldThrowWhenSameVersionButDifferentContent() {
      // given
      final var a = new BrokerState(3, Instant.EPOCH, State.ACTIVE);
      final var b = new BrokerState(3, Instant.EPOCH, State.LEAVING);

      // when / then
      assertThatThrownBy(() -> a.merge(b)).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenStateIsNull() {
      // when / then
      assertThatThrownBy(() -> new BrokerState(0, Instant.EPOCH, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenLastUpdatedIsNull() {
      // when / then
      assertThatThrownBy(() -> new BrokerState(0, null, State.ACTIVE))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
