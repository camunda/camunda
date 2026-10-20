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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrokerPartitionStateTest {

  private static PartitionState partition(final int priority) {
    return PartitionState.active(priority, DynamicPartitionConfig.init());
  }

  @Nested
  class Merge {

    @Test
    void shouldReturnThisWhenOtherIsNull() {
      // given
      final var state = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when
      final var merged = state.merge(null);

      // then
      assertThat(merged).isSameAs(state);
    }

    @Test
    void shouldPickHigherVersion() {
      // given
      final var lower =
          new BrokerPartitionState(1, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);
      final var higher =
          new BrokerPartitionState(2, Instant.EPOCH, Map.of(1, partition(5)), Mode.PROCESSING);

      // when / then — higher version wins regardless of merge direction
      assertThat(lower.merge(higher)).isEqualTo(higher);
      assertThat(higher.merge(lower)).isEqualTo(higher);
    }

    @Test
    void shouldReturnEqualStateWhenSameVersionAndIdenticalContent() {
      // given
      final var a =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);
      final var b =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);

      // when
      final var merged = a.merge(b);

      // then
      assertThat(merged).isEqualTo(a);
    }

    @Test
    void shouldThrowWhenSameVersionButDifferentContent() {
      // given
      final var a =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);
      final var b =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(9)), Mode.PROCESSING);

      // when / then
      assertThatThrownBy(() -> a.merge(b)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenSameVersionButDifferentMode() {
      // given — the mode is part of the state, so it participates in the conflict check
      final var processing =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);
      final var recovering =
          new BrokerPartitionState(3, Instant.EPOCH, Map.of(1, partition(1)), Mode.RECOVERING);

      // when / then
      assertThatThrownBy(() -> processing.merge(recovering))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCarryModeFromHigherVersionOnEnterRecovery() {
      // given — the broker entered recovery in its newer state
      final var before =
          new BrokerPartitionState(1, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);
      final var recovering =
          new BrokerPartitionState(2, Instant.EPOCH, Map.of(1, partition(1)), Mode.RECOVERING);

      // when
      final var merged = before.merge(recovering);

      // then
      assertThat(merged.mode()).isEqualTo(Mode.RECOVERING);
    }

    @Test
    void shouldCarryModeFromHigherVersionOnExitRecovery() {
      // given — the broker exited recovery in its newer state; merge must not latch recovery on
      final var recovering =
          new BrokerPartitionState(1, Instant.EPOCH, Map.of(1, partition(1)), Mode.RECOVERING);
      final var recovered =
          new BrokerPartitionState(2, Instant.EPOCH, Map.of(1, partition(1)), Mode.PROCESSING);

      // when
      final var merged = recovering.merge(recovered);

      // then
      assertThat(merged.mode()).isEqualTo(Mode.PROCESSING);
    }
  }

  @Nested
  class Updates {

    @Test
    void shouldSetModeAndIncrementVersion() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when
      final var updated = initial.setMode(Mode.RECOVERING);

      // then
      assertThat(updated.mode()).isEqualTo(Mode.RECOVERING);
      assertThat(updated.version()).isEqualTo(initial.version() + 1);
      assertThat(updated.lastUpdated()).isAfter(initial.lastUpdated());
    }

    @Test
    void shouldReturnSameInstanceWhenModeUnchanged() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when / then
      assertThat(initial.setMode(Mode.PROCESSING)).isSameAs(initial);
    }

    @Test
    void shouldAddPartitionAndIncrementVersion() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when
      final var updated = initial.addPartition(2, partition(3));

      // then
      assertThat(updated.hasPartition(2)).isTrue();
      assertThat(updated.version()).isEqualTo(initial.version() + 1);
    }

    @Test
    void shouldThrowWhenAddingExistingPartition() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when / then
      assertThatThrownBy(() -> initial.addPartition(1, partition(2)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUpdatePartitionAndIncrementVersion() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when
      final var updated = initial.updatePartition(1, PartitionState::toLeaving);

      // then
      assertThat(updated.getPartition(1).state()).isEqualTo(PartitionState.State.LEAVING);
      assertThat(updated.version()).isEqualTo(initial.version() + 1);
    }

    @Test
    void shouldThrowWhenUpdatingUnknownPartition() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when / then
      assertThatThrownBy(() -> initial.updatePartition(99, UnaryOperator.identity()))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRemovePartitionAndIncrementVersion() {
      // given
      final var initial = BrokerPartitionState.initialize(Map.of(1, partition(1), 2, partition(2)));

      // when
      final var updated = initial.removePartition(1);

      // then
      assertThat(updated.hasPartition(1)).isFalse();
      assertThat(updated.partitions()).containsOnlyKeys(2);
      assertThat(updated.version()).isEqualTo(initial.version() + 1);
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenModeIsNull() {
      // when / then
      assertThatThrownBy(() -> new BrokerPartitionState(0, Instant.EPOCH, Map.of(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class Accessors {

    @Test
    void shouldReturnPartitionForKnownId() {
      // given
      final var state = BrokerPartitionState.initialize(Map.of(1, partition(4)));

      // when / then
      assertThat(state.getPartition(1)).isEqualTo(partition(4));
      assertThat(state.hasPartition(1)).isTrue();
    }

    @Test
    void shouldReturnNullForUnknownPartition() {
      // given
      final var state = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when / then
      assertThat(state.getPartition(2)).isNull();
      assertThat(state.hasPartition(2)).isFalse();
    }
  }

  @Nested
  class ImmutableCollections {

    @Test
    void shouldDefensivelyCopyPartitionsInCanonicalConstructor() {
      // given — a mutable sorted map passed to the canonical constructor
      final var mutable = new java.util.TreeMap<Integer, PartitionState>();
      mutable.put(1, partition(1));
      final var state = new BrokerPartitionState(0, Instant.EPOCH, mutable, Mode.PROCESSING);

      // when — the source map is mutated after construction
      mutable.put(2, partition(2));

      // then — the record's view is unaffected
      assertThat(state.partitions()).containsOnlyKeys(1);
    }

    @Test
    void shouldDefensivelyCopyPartitionsInMapConstructor() {
      // given — a mutable (unsorted) map passed to the Map constructor
      final var mutable = new HashMap<Integer, PartitionState>();
      mutable.put(1, partition(1));
      final var state = new BrokerPartitionState(0, Instant.EPOCH, mutable, Mode.PROCESSING);

      // when
      mutable.put(2, partition(2));

      // then
      assertThat(state.partitions()).containsOnlyKeys(1);
    }

    @Test
    void shouldReturnImmutablePartitionsMap() {
      // given
      final var state = BrokerPartitionState.initialize(Map.of(1, partition(1)));

      // when / then
      assertThatThrownBy(() -> state.partitions().put(2, partition(2)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
