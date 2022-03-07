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

public final class DbCompositeKeyTest {

  @Test
  public void shouldWriteLongLongCompositeKey() {
    // given
    final DbLong firstLong = new DbLong();
    final DbLong secondLong = new DbLong();
    final DbCompositeKey<DbLong, DbLong> compositeKey = new DbCompositeKey<>(firstLong, secondLong);

    firstLong.wrapLong(23);
    secondLong.wrapLong(121);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    compositeKey.write(buffer, 0);

    // then
    assertThat(compositeKey.getLength()).isEqualTo(Long.BYTES * 2);
    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(23);
    assertThat(buffer.getLong(Long.BYTES, ZB_DB_BYTE_ORDER)).isEqualTo(121);
  }

  @Test
  public void shouldWrapLongLongCompositeKey() {
    // given
    final DbLong firstLong = new DbLong();
    final DbLong secondLong = new DbLong();
    final DbCompositeKey<DbLong, DbLong> compositeKey = new DbCompositeKey<>(firstLong, secondLong);

    firstLong.wrapLong(23);
    secondLong.wrapLong(121);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = compositeKey.getLength();
    compositeKey.write(buffer, 0);
    compositeKey.wrap(buffer, 0, length);

    // then
    assertThat(compositeKey.getLength()).isEqualTo(Long.BYTES * 2);
    assertThat(compositeKey.first().getValue()).isEqualTo(23);
    assertThat(firstLong.getValue()).isEqualTo(23);
    assertThat(compositeKey.second().getValue()).isEqualTo(121);
    assertThat(secondLong.getValue()).isEqualTo(121);
  }

  @Test
  public void shouldWriteStringLongCompositeKey() {
    // given
    final DbString firstString = new DbString();
    final DbLong secondLong = new DbLong();
    final DbCompositeKey<DbString, DbLong> compositeKey =
        new DbCompositeKey<>(firstString, secondLong);

    firstString.wrapString("foo");
    secondLong.wrapLong(121);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    compositeKey.write(buffer, 0);

    // then
    assertThat(compositeKey.getLength()).isEqualTo(Long.BYTES + 3 + Integer.BYTES);

    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    final byte[] bytes = new byte[3];
    buffer.getBytes(Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo("foo".getBytes());

    assertThat(buffer.getLong(Integer.BYTES + 3, ZB_DB_BYTE_ORDER)).isEqualTo(121);
  }

  @Test
  public void shouldWrapStringLongCompositeKey() {
    // given
    final DbString firstString = new DbString();
    final DbLong secondLong = new DbLong();
    final DbCompositeKey<DbString, DbLong> compositeKey =
        new DbCompositeKey<>(firstString, secondLong);

    firstString.wrapString("foo");
    secondLong.wrapLong(121);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = compositeKey.getLength();
    compositeKey.write(buffer, 0);
    compositeKey.wrap(buffer, 0, length);

    // then
    assertThat(compositeKey.getLength()).isEqualTo(Long.BYTES + 3 + Integer.BYTES);
    assertThat(compositeKey.first().toString()).isEqualTo("foo");
    assertThat(firstString.toString()).isEqualTo("foo");
    assertThat(compositeKey.second().getValue()).isEqualTo(121);
    assertThat(secondLong.getValue()).isEqualTo(121);
  }

  @Test
  public void shouldWriteNestedCompositeKey() {
    // given
    final DbString firstString = new DbString();
    final DbLong secondLong = new DbLong();
    final DbLong thirdLong = new DbLong();

    final DbCompositeKey<DbString, DbLong> compositeKey =
        new DbCompositeKey<>(firstString, secondLong);
    final DbCompositeKey<DbCompositeKey<DbString, DbLong>, DbLong> nestedCompositeKey =
        new DbCompositeKey<>(compositeKey, thirdLong);

    firstString.wrapString("foo");
    secondLong.wrapLong(121);
    thirdLong.wrapLong(100_234L);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    nestedCompositeKey.write(buffer, 0);

    // then
    assertThat(nestedCompositeKey.getLength())
        .isEqualTo(Long.BYTES + 3 + Integer.BYTES + Long.BYTES);

    int offset = 0;
    assertThat(buffer.getInt(offset, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    offset += Integer.BYTES;

    final byte[] bytes = new byte[3];
    buffer.getBytes(offset, bytes);
    assertThat(bytes).isEqualTo("foo".getBytes());
    offset += 3;

    assertThat(buffer.getLong(offset, ZB_DB_BYTE_ORDER)).isEqualTo(121);
    offset += Long.BYTES;

    assertThat(buffer.getLong(offset, ZB_DB_BYTE_ORDER)).isEqualTo(100_234L);
  }

  @Test
  public void shouldWrapNestedCompositeKey() {
    // given

    final DbString firstString = new DbString();
    final DbLong secondLong = new DbLong();
    final DbLong thirdLong = new DbLong();

    final DbCompositeKey<DbString, DbLong> compositeKey =
        new DbCompositeKey<>(firstString, secondLong);
    final DbCompositeKey<DbCompositeKey<DbString, DbLong>, DbLong> nestedCompositeKey =
        new DbCompositeKey<>(compositeKey, thirdLong);

    firstString.wrapString("foo");
    secondLong.wrapLong(121);
    thirdLong.wrapLong(100_234L);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = compositeKey.getLength();
    compositeKey.write(buffer, 0);
    compositeKey.wrap(buffer, 0, length);

    // then
    assertThat(nestedCompositeKey.getLength())
        .isEqualTo(Long.BYTES + 3 + Integer.BYTES + Long.BYTES);

    final DbCompositeKey<DbString, DbLong> composite = nestedCompositeKey.first();
    assertThat(composite.first().toString()).isEqualTo("foo");
    assertThat(firstString.toString()).isEqualTo("foo");

    assertThat(composite.second().getValue()).isEqualTo(121);
    assertThat(secondLong.getValue()).isEqualTo(121);

    assertThat(nestedCompositeKey.second().getValue()).isEqualTo(100_234L);
    assertThat(thirdLong.getValue()).isEqualTo(100_234L);
  }
}
