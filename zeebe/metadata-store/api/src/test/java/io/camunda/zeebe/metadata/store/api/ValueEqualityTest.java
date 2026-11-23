/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import io.camunda.zeebe.metadata.store.api.Write.Put;
import org.junit.jupiter.api.Test;

/**
 * The store compares values by their bytes, so the models that carry values must too; records
 * compare arrays by identity unless told otherwise.
 */
final class ValueEqualityTest {

  private static final StoreKey KEY = StoreKey.of(Namespace.cluster("config"), "a.b");

  @Test
  void shouldCompareKeyValueByBytes() {
    // given
    final var value = new KeyValue(KEY, new byte[] {1}, new byte[] {2});
    final var sameBytes = new KeyValue(KEY, new byte[] {1}, new byte[] {2});
    final var otherPrevious = new KeyValue(KEY, new byte[] {1}, null);

    // when - then
    assertThat(value).isEqualTo(sameBytes).hasSameHashCodeAs(sameBytes).isNotEqualTo(otherPrevious);
  }

  @Test
  void shouldCompareKeyEventByBytes() {
    // given
    final var event = new KeyEvent(ChangeType.REPLACE, KEY, new byte[] {1}, new byte[] {2});
    final var sameBytes = new KeyEvent(ChangeType.REPLACE, KEY, new byte[] {1}, new byte[] {2});
    final var otherCurrent = new KeyEvent(ChangeType.REPLACE, KEY, new byte[] {3}, new byte[] {2});

    // when - then
    assertThat(event).isEqualTo(sameBytes).hasSameHashCodeAs(sameBytes).isNotEqualTo(otherCurrent);
  }

  @Test
  void shouldComparePutByBytes() {
    // given
    final var put = new Put(KEY, new byte[] {1}, Condition.valueEquals(new byte[] {2}));
    final var sameBytes = new Put(KEY, new byte[] {1}, Condition.valueEquals(new byte[] {2}));
    final var unconditional = new Put(KEY, new byte[] {1});

    // when - then
    assertThat(put).isEqualTo(sameBytes).hasSameHashCodeAs(sameBytes).isNotEqualTo(unconditional);
  }

  @Test
  void shouldCompareValueEqualsConditionByBytes() {
    // given
    final var condition = Condition.valueEquals(new byte[] {1, 2});

    // when - then
    assertThat(condition)
        .isEqualTo(Condition.valueEquals(new byte[] {1, 2}))
        .hasSameHashCodeAs(Condition.valueEquals(new byte[] {1, 2}))
        .isNotEqualTo(Condition.valueEquals(new byte[] {1}));
  }
}
