/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

public final class DbByteTest {

  private final DbByte zbByte = new DbByte();

  @Test
  public void shouldWrapByte() {
    // given
    zbByte.wrapByte((byte) 255);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbByte.write(buffer, 0);

    // then
    assertThat(zbByte.getLength()).isEqualTo(Byte.BYTES);
    assertThat(zbByte.getValue()).isEqualTo((byte) 255);
    assertThat(buffer.getByte(0)).isEqualTo((byte) 255);
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
    valueBuffer.putByte(0, (byte) 255);
    zbByte.wrap(valueBuffer, 0, 1);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbByte.write(buffer, 0);

    // then
    assertThat(zbByte.getLength()).isEqualTo(Byte.BYTES);
    assertThat(zbByte.getValue()).isEqualTo((byte) 255);
    assertThat(buffer.getByte(0)).isEqualTo((byte) 255);
  }
}
