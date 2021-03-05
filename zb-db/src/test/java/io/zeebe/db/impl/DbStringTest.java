/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class DbStringTest {

  private final DbString zbString = new DbString();

  @Test
  public void shouldWrapString() {
    // given
    zbString.wrapString("foo");

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrapBuffer() {
    // given
    zbString.wrapBuffer(wrapString("foo"));

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrapBufferView() {
    // given
    final String value = "foobar";
    final DirectBuffer view = new UnsafeBuffer(0, 0);
    view.wrap(value.getBytes(), 0, 3);

    zbString.wrapBuffer(view);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
    valueBuffer.putInt(0, 3, ZB_DB_BYTE_ORDER);
    valueBuffer.putBytes(Integer.BYTES, "bar".getBytes());
    zbString.wrap(valueBuffer, 0, 3 + Integer.BYTES);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("bar");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("bar");
  }
}
