/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RawKeyValueTest {

  @Test
  void shouldWriteKeyBytesUnchanged() {
    // given
    final var key = new RawKey();
    key.set(new byte[] {1, 2, 3});
    final var buffer = new UnsafeBuffer(new byte[5]);

    // when
    final int written = key.write(buffer, 1);

    // then
    assertThat(written).isEqualTo(3).isEqualTo(key.getLength());
    assertThat(buffer.byteArray()).containsExactly(0, 1, 2, 3, 0);
  }

  @Test
  void shouldCopyKeyBytesOnWrap() {
    // given
    final var source = new byte[] {0, 1, 2, 3, 0};
    final var key = new RawKey();

    // when
    key.wrap(new UnsafeBuffer(source), 1, 3);
    source[1] = 9;

    // then
    assertThat(key.bytes()).containsExactly(1, 2, 3);
  }

  @Test
  void shouldWriteValueBytesUnchanged() {
    // given
    final var value = new RawValue();
    value.set(new byte[] {1, 2, 3});
    final var buffer = new UnsafeBuffer(new byte[5]);

    // when
    final int written = value.write(buffer, 1);

    // then
    assertThat(written).isEqualTo(3).isEqualTo(value.getLength());
    assertThat(buffer.byteArray()).containsExactly(0, 1, 2, 3, 0);
  }

  @Test
  void shouldCopyValueBytesOnWrap() {
    // given
    final var source = new byte[] {0, 1, 2, 3, 0};
    final var value = new RawValue();

    // when
    value.wrap(new UnsafeBuffer(source), 1, 3);
    source[1] = 9;

    // then
    assertThat(value.bytes()).containsExactly(1, 2, 3);
  }
}
