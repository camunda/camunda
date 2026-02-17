/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Wraps a key from a given column family. When serialized via {@link
 * io.camunda.zeebe.util.buffer.BufferWriter}, this behaves exactly as the inner key.
 *
 * @param inner
 * @param columnFamily
 * @param <K>
 */
public record DbForeignKey<K extends DbKey>(
    K inner, Enum<? extends EnumValue> columnFamily, MatchType match, Predicate<K> skip)
    implements DbKey, DbValue, ContainsForeignKeys {

  public DbForeignKey(final K inner, final Enum<? extends EnumValue> columnFamily) {
    this(inner, columnFamily, MatchType.Full);
  }

  public DbForeignKey(
      final K inner, final Enum<? extends EnumValue> columnFamily, final MatchType match) {
    this(inner, columnFamily, match, (k) -> false);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    inner.wrap(buffer, offset, length);
  }

  @Override
  public int getLength() {
    return inner.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return inner.write(buffer, offset);
  }

  @Override
  public Collection<DbForeignKey<DbKey>> containedForeignKeys() {
    return Collections.singletonList((DbForeignKey<DbKey>) this);
  }

  public boolean shouldSkipCheck() {
    return skip.test(inner);
  }

  public enum MatchType {
    Full,
    Prefix,
  }
}
