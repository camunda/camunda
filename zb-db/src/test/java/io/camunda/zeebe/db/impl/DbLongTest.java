/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

/** */
public final class DbLongTest {

  private final DbLong zbLong = new DbLong();

  @Test
  public void shouldWrapLong() {
    // given
    zbLong.wrapLong(234L);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbLong.write(buffer, 0);

    // then
    assertThat(zbLong.getLength()).isEqualTo(Long.BYTES);
    assertThat(zbLong.getValue()).isEqualTo(234L);
    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(234L);
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer longBuffer = new ExpandableArrayBuffer();
    longBuffer.putLong(0, 234, ZB_DB_BYTE_ORDER);
    zbLong.wrap(longBuffer, 0, Long.BYTES);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbLong.write(buffer, 0);

    // then
    assertThat(zbLong.getLength()).isEqualTo(Long.BYTES);
    assertThat(zbLong.getValue()).isEqualTo(234L);
    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(234L);
  }
}
