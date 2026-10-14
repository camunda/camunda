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
import io.camunda.zeebe.db.KeyStructure;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

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
    this(inner, columnFamily, match, neverSkip());
  }

  /**
   * Foreign keys reference keys produced by the key generator, which {@code
   * Protocol.encodePartitionId} guarantees to be non-negative. So unlike other keys, a foreign key
   * declares its numeric components as non-negative by default.
   *
   * <p>The exception is a foreign key with a custom {@code skip} predicate: that predicate exists
   * to skip a sentinel such as {@code -1}, so the component may be negative and is described
   * unchanged.
   */
  @Override
  public void describe(final KeyStructure structure) {
    if (skip == NEVER_SKIP) {
      inner.describe(new NonNegativeKeyStructure(structure));
    } else {
      inner.describe(structure);
    }
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

  private static final Predicate<?> NEVER_SKIP = k -> false;

  @SuppressWarnings("unchecked")
  private static <T> Predicate<T> neverSkip() {
    return (Predicate<T>) NEVER_SKIP;
  }

  public boolean shouldSkipCheck() {
    return skip.test(inner);
  }

  public enum MatchType {
    Full,
    Prefix,
  }

  /** Forwards everything, but declares all numeric components as non-negative. */
  private record NonNegativeKeyStructure(KeyStructure delegate) implements KeyStructure {

    @Override
    public void int64(final @Nullable String name, final boolean nonNegative) {
      delegate.int64(name, true);
    }

    @Override
    public void int32(final @Nullable String name, final boolean nonNegative) {
      delegate.int32(name, true);
    }

    @Override
    public void int16(final @Nullable String name, final boolean nonNegative) {
      delegate.int16(name, true);
    }

    @Override
    public void int8(final @Nullable String name, final boolean nonNegative) {
      delegate.int8(name, true);
    }

    @Override
    public void string(final @Nullable String name) {
      delegate.string(name);
    }

    @Override
    public void omittableString(final @Nullable String name) {
      delegate.omittableString(name);
    }

    @Override
    public void bytes(final @Nullable String name) {
      delegate.bytes(name);
    }

    @Override
    public void enumOrdinal(final @Nullable String name) {
      delegate.enumOrdinal(name);
    }
  }
}
