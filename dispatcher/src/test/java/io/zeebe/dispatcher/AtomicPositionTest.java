/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class AtomicPositionTest {
  private final AtomicPosition atomicPosition = new AtomicPosition();

  @Test
  public void shouldGetDefaultPosition() {
    // given

    // when
    final long defaultValue = atomicPosition.get();

    // then
    assertThat(defaultValue).isEqualTo(0);
  }

  @Test
  public void shouldSetAndGetPosition() {
    // given

    // when
    atomicPosition.set(1);

    // then
    assertThat(atomicPosition.get()).isEqualTo(1);
  }

  @Test
  public void shouldProposeMaxOrderedPositionIfNoPositionWasSet() {
    // given

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(1);

    // then
    assertThat(success).isTrue();
    assertThat(atomicPosition.get()).isEqualTo(1);
  }

  @Test
  public void shouldProposeMaxOrderedPosition() {
    // given
    atomicPosition.set(1);

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(2);

    // then
    assertThat(success).isTrue();
    assertThat(atomicPosition.get()).isEqualTo(2);
  }

  @Test
  public void shouldNotProposeMaxOrderedPosition() {
    // given
    atomicPosition.set(2);

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(1);

    // then
    assertThat(success).isFalse();
    assertThat(atomicPosition.get()).isEqualTo(2);
  }

  @Test
  public void shouldResetPosition() {
    // given
    atomicPosition.set(2);

    // when
    atomicPosition.reset();

    // then
    assertThat(atomicPosition.get()).isEqualTo(-1);
  }
}
