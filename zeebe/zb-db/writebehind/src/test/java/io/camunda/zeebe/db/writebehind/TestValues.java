/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.nio.ByteBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.jspecify.annotations.Nullable;

/** Keys and values for tests that use the store directly, without column families. */
final class TestValues {

  static final long COLUMN_FAMILY = 7;

  private TestValues() {}

  /** The raw key of {@code id} in {@link #COLUMN_FAMILY}. */
  static RawKey key(final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(COLUMN_FAMILY).putLong(id).array());
  }

  static HeldValue held(final long value) {
    return hold(longValue(value));
  }

  static HeldValue hold(final DbValue value) {
    return Values.hold(value, new ExpandableArrayBuffer());
  }

  static DbLong longValue(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }

  /** The long that a held value holds, copied out like a column family does. */
  static @Nullable Long longOf(final @Nullable HeldValue held) {
    if (held == null) {
      return null;
    }
    final var target = new DbLong();
    Values.copyInto(held, target);
    return target.getValue();
  }

  /** A commit of one key; a null value deletes it. */
  static void commit(
      final WriteBehindStore<RawKey, HeldValue, DbValue> store,
      final RawKey key,
      final @Nullable HeldValue value) {
    store.commit(new RawKey[] {key}, new HeldValue[] {value});
  }
}
