/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DbTenantAwareKeyTest {

  private static final DbString TENANT_KEY = new DbString();
  private static final String TENANT_ID = "tenantId";

  @BeforeEach
  void beforeEach() {
    TENANT_KEY.wrapString(TENANT_ID);
  }

  @AfterEach
  void afterEach() {
    TENANT_KEY.wrapString("");
  }

  @Test
  void shouldWriteDbStringPrefixed() {
    // given
    final var wrappedKey = new DbString();
    final var value = "foo";
    wrappedKey.wrapString(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(value.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    byte[] bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());

    assertThat(buffer.getInt(TENANT_ID.length() + Integer.BYTES, ZB_DB_BYTE_ORDER))
        .isEqualTo(value.length());
    bytes = new byte[value.length()];
    buffer.getBytes(TENANT_ID.length() + (Integer.BYTES * 2), bytes);
    assertThat(bytes).isEqualTo(value.getBytes());
  }

  @Test
  void shouldWrapDbStringPrefixed() {
    // given
    final var wrappedKey = new DbString();
    final var value = "foo";
    wrappedKey.wrapString(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(value.length() + TENANT_ID.length() + (Integer.BYTES * 2));
    assertThat(tenantAwareKey.wrappedKey().toString()).isEqualTo(value);
    assertThat(wrappedKey.toString()).isEqualTo(value);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbStringSuffixed() {
    // given
    final var wrappedKey = new DbString();
    final var value = "foo";
    wrappedKey.wrapString(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    // The total length is the amount of bytes in each string (= amount of characters) + 2 times the
    // amount of bytes in an Integer. This is what's used in a buffer to determine the length of the
    // String that's coming.
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(value.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    // The first integer is the length of the first part of the key (foo)
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(value.length());
    byte[] bytes = new byte[value.length()];
    // We get the 3 bytes from the buffer and verify it matches foo. We start at index Integer.BYTES
    // to skip the length.
    buffer.getBytes(Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(value.getBytes());

    // The second integer is the length of the second part of the key (tenantId)
    assertThat(buffer.getInt(value.length() + Integer.BYTES, ZB_DB_BYTE_ORDER))
        .isEqualTo(TENANT_ID.length());
    bytes = new byte[TENANT_ID.length()];
    // We get the 8 bytes from the buffer and verify it matches tenantId. We start at an index that
    // skips foo and its length, as well as the length of tenantId.
    buffer.getBytes(value.length() + (Integer.BYTES * 2), bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());
  }

  @Test
  void shouldWrapDbStringSuffixed() {
    // given
    final var wrappedKey = new DbString();
    final var value = "foo";
    wrappedKey.wrapString(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(value.length() + TENANT_ID.length() + (Integer.BYTES * 2));
    assertThat(tenantAwareKey.wrappedKey().toString()).isEqualTo(value);
    assertThat(wrappedKey.toString()).isEqualTo(value);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbLongPrefixed() {
    // given
    final var wrappedKey = new DbLong();
    final var value = 123L;
    wrappedKey.wrapLong(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);

    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    final var bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());

    assertThat(buffer.getLong(Integer.BYTES + TENANT_ID.length(), ZB_DB_BYTE_ORDER))
        .isEqualTo(value);
  }

  @Test
  void shouldWrapDbLongPrefixed() {
    // given
    final var wrappedKey = new DbLong();
    final var value = 123L;
    wrappedKey.wrapLong(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);
    assertThat(tenantAwareKey.wrappedKey().getValue()).isEqualTo(value);
    assertThat(wrappedKey.getValue()).isEqualTo(value);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbLongSuffixed() {
    // given
    final var wrappedKey = new DbLong();
    final var value = 123L;
    wrappedKey.wrapLong(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);

    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(value);

    assertThat(buffer.getInt(Long.BYTES, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    final var bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(Long.BYTES + Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());
  }

  @Test
  void shouldWrapDbLongSuffixed() {
    // given
    final var wrappedKey = new DbLong();
    final var value = 123L;
    wrappedKey.wrapLong(value);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);
    assertThat(tenantAwareKey.wrappedKey().getValue()).isEqualTo(value);
    assertThat(wrappedKey.getValue()).isEqualTo(value);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbCompositeKeyPrefixed() {
    // given
    final var stringKey = new DbString();
    final var longKey = new DbLong();
    final var wrappedKey = new DbCompositeKey<>(stringKey, longKey);
    final var stringValue = "foo";
    final var longValue = 123L;
    stringKey.wrapString(stringValue);
    longKey.wrapLong(longValue);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + stringValue.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    var offset = 0;
    assertThat(buffer.getInt(offset, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    offset += Integer.BYTES;
    byte[] bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(offset, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());
    offset += TENANT_ID.length();

    assertThat(buffer.getInt(offset, ZB_DB_BYTE_ORDER)).isEqualTo(stringValue.length());
    bytes = new byte[stringValue.length()];
    offset += Integer.BYTES;
    buffer.getBytes(offset, bytes);
    assertThat(bytes).isEqualTo(stringValue.getBytes());
    offset += stringValue.length();

    assertThat(buffer.getLong(offset, ZB_DB_BYTE_ORDER)).isEqualTo(longValue);
  }

  @Test
  void shouldWrapDbCompositeKeyPrefixed() {
    // given
    final var stringKey = new DbString();
    final var longKey = new DbLong();
    final var wrappedKey = new DbCompositeKey<>(stringKey, longKey);
    final var stringValue = "foo";
    final var longValue = 123L;
    stringKey.wrapString(stringValue);
    longKey.wrapLong(longValue);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.PREFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    final var length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + stringValue.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    final var firstKey = tenantAwareKey.wrappedKey().first();
    final var secondKey = tenantAwareKey.wrappedKey().second();
    final var tenantKey = tenantAwareKey.tenantKey();
    assertThat(firstKey.toString()).isEqualTo(stringValue);
    assertThat(secondKey.getValue()).isEqualTo(longValue);
    assertThat(tenantKey.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbCompositeKeySuffixed() {
    // given
    final var stringKey = new DbString();
    final var longKey = new DbLong();
    final var wrappedKey = new DbCompositeKey<>(stringKey, longKey);
    final var stringValue = "foo";
    final var longValue = 123L;
    stringKey.wrapString(stringValue);
    longKey.wrapLong(longValue);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + stringValue.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    var offset = 0;
    assertThat(buffer.getInt(offset, ZB_DB_BYTE_ORDER)).isEqualTo(stringValue.length());
    byte[] bytes = new byte[stringValue.length()];
    offset += Integer.BYTES;
    buffer.getBytes(offset, bytes);
    assertThat(bytes).isEqualTo(stringValue.getBytes());
    offset += stringValue.length();

    assertThat(buffer.getLong(offset, ZB_DB_BYTE_ORDER)).isEqualTo(longValue);
    offset += Long.BYTES;

    assertThat(buffer.getInt(offset, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    offset += Integer.BYTES;
    bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(offset, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());
  }

  @Test
  void shouldWrapDbCompositeKeySuffixed() {
    // given
    final var stringKey = new DbString();
    final var longKey = new DbLong();
    final var wrappedKey = new DbCompositeKey<>(stringKey, longKey);
    final var stringValue = "foo";
    final var longValue = 123L;
    stringKey.wrapString(stringValue);
    longKey.wrapLong(longValue);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, wrappedKey, PlacementType.SUFFIX);

    // when
    final var buffer = new ExpandableArrayBuffer();
    final var length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + stringValue.length() + TENANT_ID.length() + (Integer.BYTES * 2));

    final var firstKey = tenantAwareKey.wrappedKey().first();
    final var secondKey = tenantAwareKey.wrappedKey().second();
    final var tenantKey = tenantAwareKey.tenantKey();
    assertThat(firstKey.toString()).isEqualTo(stringValue);
    assertThat(secondKey.getValue()).isEqualTo(longValue);
    assertThat(tenantKey.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbForeignKeyPrefixed() {
    // given
    final var longKey = new DbLong();
    final var foreignKey = new DbForeignKey<>(longKey, DefaultColumnFamily.DEFAULT);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, foreignKey, PlacementType.PREFIX);
    final var longValue = 123L;
    longKey.wrapLong(longValue);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);

    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    final var bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());

    assertThat(buffer.getLong(Integer.BYTES + TENANT_ID.length(), ZB_DB_BYTE_ORDER))
        .isEqualTo(longValue);
  }

  @Test
  void shouldWrapDbForeignKeyPrefixed() {
    // given
    final var longKey = new DbLong();
    final var foreignKey = new DbForeignKey<>(longKey, DefaultColumnFamily.DEFAULT);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, foreignKey, PlacementType.PREFIX);
    final var longValue = 123L;
    longKey.wrapLong(longValue);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);
    assertThat(tenantAwareKey.wrappedKey().inner().getValue()).isEqualTo(longValue);
    assertThat(longKey.getValue()).isEqualTo(longValue);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }

  @Test
  void shouldWriteDbForeignKeySuffixed() {
    // given
    final var longKey = new DbLong();
    final var foreignKey = new DbForeignKey<>(longKey, DefaultColumnFamily.DEFAULT);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, foreignKey, PlacementType.SUFFIX);
    final var longValue = 123L;
    longKey.wrapLong(longValue);

    // when
    final var buffer = new ExpandableArrayBuffer();
    tenantAwareKey.write(buffer, 0);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);

    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(longValue);

    assertThat(buffer.getInt(Long.BYTES, ZB_DB_BYTE_ORDER)).isEqualTo(TENANT_ID.length());
    final var bytes = new byte[TENANT_ID.length()];
    buffer.getBytes(Long.BYTES + Integer.BYTES, bytes);
    assertThat(bytes).isEqualTo(TENANT_ID.getBytes());
  }

  @Test
  void shouldWrapDbForeignKeySuffixed() {
    // given
    final var longKey = new DbLong();
    final var foreignKey = new DbForeignKey<>(longKey, DefaultColumnFamily.DEFAULT);
    final var tenantAwareKey = new DbTenantAwareKey<>(TENANT_KEY, foreignKey, PlacementType.SUFFIX);
    final var longValue = 123L;
    longKey.wrapLong(longValue);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = tenantAwareKey.getLength();
    tenantAwareKey.write(buffer, 0);
    tenantAwareKey.wrap(buffer, 0, length);

    // then
    assertThat(tenantAwareKey.getLength())
        .isEqualTo(Long.BYTES + TENANT_ID.length() + Integer.BYTES);
    assertThat(tenantAwareKey.wrappedKey().inner().getValue()).isEqualTo(longValue);
    assertThat(longKey.getValue()).isEqualTo(longValue);
    assertThat(tenantAwareKey.tenantKey().toString()).isEqualTo(TENANT_ID);
    assertThat(TENANT_KEY.toString()).isEqualTo(TENANT_ID);
  }
}
