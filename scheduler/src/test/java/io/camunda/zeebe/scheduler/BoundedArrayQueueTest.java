/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

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
}
