/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public final class BoundedArrayQueueTest {

  @Test
  public void shouldRetrieveAddedElements() {
    // given
    final int numElements = 4;
    final BoundedArrayQueue<Integer> queue = new BoundedArrayQueue<>(numElements);

    for (int i = 0; i < numElements; i++) {
      queue.add(i);
    }

    for (int i = 0; i < numElements; i++) {
      // when
      final Integer queueHead = queue.poll();
      // then
      assertThat(queueHead).isEqualTo(i);
    }
  }

  @Test
  public void shouldRetrieveLessElementsThanAdded() {
    // given
    final int numElements = 3;
    final BoundedArrayQueue<Integer> queue =
        new BoundedArrayQueue<>(numElements); // => queueCapacity becomes next power of two == 4

    for (int i = 0; i < numElements; i++) {
      queue.add(i);
    }

    for (int i = 0; i < numElements; i++) {
      // when
      final Integer queueHead = queue.poll();
      // then
      assertThat(queueHead).isEqualTo(i);
    }
  }

  @ParameterizedTest
  @CsvSource({"1, 1", "3, 4", "5, 8", "8, 8"})
  void shouldSupportRoundedCapacityAndWrapAround(
      final int requestedCapacity, final int expectedCapacity) {
    // given
    final var queue = new BoundedArrayQueue<Integer>(requestedCapacity);
    assertThat(queue.getCapacity()).isEqualTo(expectedCapacity);

    // when
    for (int i = 0; i < expectedCapacity; i++) {
      assertThat(queue.offer(i)).isTrue();
    }

    // then
    assertThat(queue.offer(-1)).isFalse();
    assertThat(queue.poll()).isZero();
    assertThat(queue.offer(expectedCapacity)).isTrue();
    for (int i = 1; i < expectedCapacity; i++) {
      assertThat(queue.poll()).isEqualTo(i);
    }
    assertThat(queue.poll()).isEqualTo(expectedCapacity);
    assertThat(queue.poll()).isNull();
  }
}
