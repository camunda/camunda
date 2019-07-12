/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Test;

public class DbBufferViewTest {

  private final DbBufferView zbBufferView = new DbBufferView();

  @Test
  public void shouldWrapBuffer() {
    // given
    final DirectBuffer value = wrapString("a");
    zbBufferView.wrapBuffer(value);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbBufferView.write(buffer, 0);

    // then
    assertThat(zbBufferView.getLength()).isEqualTo(value.capacity());
    assertThat(zbBufferView.getValue()).isEqualTo(value);
    assertThat(buffer.getByte(0)).isEqualTo(value.getByte(0));
  }

  @Test
  public void shouldWrap() {
    // given
    final DirectBuffer value = wrapString("a");
    zbBufferView.wrap(value, 0, value.capacity());

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbBufferView.write(buffer, 0);

    // then
    assertThat(zbBufferView.getLength()).isEqualTo(value.capacity());
    assertThat(zbBufferView.getValue()).isEqualTo(value);
    assertThat(buffer.getByte(0)).isEqualTo(value.getByte(0));
  }

  @Test
  public void shouldNotCopyOnWrap() {
    final DirectBuffer value1 = wrapString("a");
    final DirectBuffer value2 = wrapString("b");

    // given
    final MutableDirectBuffer readBuffer = new ExpandableArrayBuffer();
    readBuffer.putBytes(0, value1, 0, value1.capacity());
    zbBufferView.wrap(readBuffer, 0, value1.capacity());

    final ExpandableArrayBuffer writeBuffer = new ExpandableArrayBuffer();
    zbBufferView.write(writeBuffer, 0);

    // when
    readBuffer.putBytes(0, value2, 0, value2.capacity());

    // then
    assertThat(zbBufferView.getValue()).isEqualTo(value2);
    assertThat(zbBufferView.getLength()).isEqualTo(value2.capacity());
    assertThat(writeBuffer.getByte(0)).isEqualTo(value1.getByte(0));
  }
}
