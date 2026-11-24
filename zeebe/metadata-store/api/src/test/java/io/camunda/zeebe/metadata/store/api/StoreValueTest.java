/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A value is immutable and compares by its bytes, as the store does. */
final class StoreValueTest {

  @Test
  void shouldCompareByBytes() {
    // given
    final var value = StoreValue.of(new byte[] {1, 2});

    // when - then
    assertThat(value)
        .isEqualTo(StoreValue.of(new byte[] {1, 2}))
        .hasSameHashCodeAs(StoreValue.of(new byte[] {1, 2}))
        .isNotEqualTo(StoreValue.of(new byte[] {1}));
  }

  @Test
  void shouldNotChangeWhenCallerChangesItsArrays() {
    // given
    final var bytes = new byte[] {1};
    final var value = StoreValue.of(bytes);

    // when
    bytes[0] = 9;
    value.toByteArray()[0] = 9;

    // then
    assertThat(value.toByteArray()).containsExactly(1);
  }
}
