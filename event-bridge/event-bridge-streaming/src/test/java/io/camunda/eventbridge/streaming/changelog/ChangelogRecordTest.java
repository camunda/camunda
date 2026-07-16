/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class ChangelogRecordTest {

  private static final byte[] KEY = {1, 2, 3};
  private static final byte[] VALUE = {9, 9};

  @Test
  void shouldBuildAPutWithItsValue() {
    final ChangelogRecord record = ChangelogRecord.put(KEY, VALUE);

    assertThat(record.key()).isEqualTo(KEY);
    assertThat(record.value()).isEqualTo(VALUE);
    assertThat(record.isTombstone()).isFalse();
  }

  @Test
  void shouldBuildATombstoneWithAnEmptyValue() {
    final ChangelogRecord record = ChangelogRecord.tombstone(KEY);

    assertThat(record.key()).isEqualTo(KEY);
    assertThat(record.value()).isEmpty();
    assertThat(record.isTombstone()).isTrue();
  }

  @Test
  void shouldRejectAPutWithAnEmptyValue() {
    assertThatThrownBy(() -> ChangelogRecord.put(KEY, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectAnEmptyOrNullKey() {
    assertThatThrownBy(() -> new ChangelogRecord(new byte[0], VALUE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ChangelogRecord(null, VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectANullValue() {
    assertThatThrownBy(() -> new ChangelogRecord(KEY, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
