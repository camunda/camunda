/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class DbBytesTest {

  private final DbBytes zbBytes = new DbBytes();

  @Test
  public void shouldWrapBytes() {
    // given
    final byte[] bytes = {1, 2, 3, 4, 5, 6, 7};

    // when
    zbBytes.wrapBytes(bytes);

    // then
    assertThat(zbBytes.getLength()).isEqualTo(bytes.length);
    assertThat(zbBytes.getBytes()).isEqualTo(bytes);
  }

  @Test
  public void shouldWrapBytesWithOffsetAndLength() {
    // given
    final var bytes = new byte[] {1, 2, 3, 4, 5, 6, 7};
    final var buffer = new UnsafeBuffer(bytes);

    // when -- wrapping over part of the buffer
    zbBytes.wrap(buffer, 2, 3);

    // then
    assertThat(zbBytes.getLength()).isEqualTo(3);
    assertThat(zbBytes.getBytes()).isEqualTo(new byte[] {3, 4, 5});
  }
}
