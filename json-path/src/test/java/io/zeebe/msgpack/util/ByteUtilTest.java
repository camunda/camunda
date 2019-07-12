/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class ByteUtilTest {

  @Test
  public void testIsNumeric() {
    // given
    final DirectBuffer buffer =
        new UnsafeBuffer("foo0123456789bar".getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(ByteUtil.isNumeric(buffer, 0, buffer.capacity())).isFalse();
    assertThat(ByteUtil.isNumeric(buffer, 3, 10)).isTrue();
    assertThat(ByteUtil.isNumeric(buffer, 2, 10)).isFalse();
    assertThat(ByteUtil.isNumeric(buffer, 3, 11)).isFalse();
  }

  @Test
  public void testParseInteger() {
    // given
    final DirectBuffer buffer = new UnsafeBuffer("foo56781bar".getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(ByteUtil.parseInteger(buffer, 3, 5)).isEqualTo(56781);
  }

  @Test
  public void testBytesToBinary() {
    // given
    final byte[] bytes = new byte[] {-0x01, 0x1a};

    // when
    final String binary = ByteUtil.bytesToBinary(bytes);

    // then
    assertThat(binary).isEqualTo("11111111, 00011010, ");
  }
}
