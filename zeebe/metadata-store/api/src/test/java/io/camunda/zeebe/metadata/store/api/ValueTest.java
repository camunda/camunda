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

/**
 * The store compares values by their bytes, so every type that carries a value must do the same and
 * must not change when the caller reuses its array.
 */
final class ValueTest {

  @Test
  void shouldCompareByBytes() {
    // given
    final var value = Value.of(new byte[] {1, 2});

    // when - then
    assertThat(value)
        .isEqualTo(Value.of(new byte[] {1, 2}))
        .hasSameHashCodeAs(Value.of(new byte[] {1, 2}))
        .isNotEqualTo(Value.of(new byte[] {1}));
  }

  @Test
  void shouldNotChangeWhenCallerChangesTheirArray() {
    // given
    final var bytes = new byte[] {1};
    final var value = Value.of(bytes);

    // when
    bytes[0] = 2;
    value.toByteArray()[0] = 3;

    // then
    assertThat(value).isEqualTo(Value.of(new byte[] {1}));
  }
}
