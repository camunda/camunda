/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

/** */
public final class DbIntTest {

  private final DbInt zbInt = new DbInt();

  @Test
  public void shouldWrapInt() {
    // given
    zbInt.wrapInt(234);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbInt.write(buffer, 0);

    // then
    assertThat(zbInt.getLength()).isEqualTo(Integer.BYTES);
    assertThat(zbInt.getValue()).isEqualTo(234);
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(234);
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer intBuffer = new ExpandableArrayBuffer();
    intBuffer.putInt(0, 234, ZB_DB_BYTE_ORDER);
    zbInt.wrap(intBuffer, 0, Integer.BYTES);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbInt.write(buffer, 0);

    // then
    assertThat(zbInt.getLength()).isEqualTo(Integer.BYTES);
    assertThat(zbInt.getValue()).isEqualTo(234);
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(234);
  }
}
