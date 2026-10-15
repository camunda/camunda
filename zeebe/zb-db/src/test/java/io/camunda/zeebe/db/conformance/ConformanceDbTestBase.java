/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared fixture for the ordering and iteration conformance tests: opens a fresh database with
 * several column families per test and offers small helpers to build keys.
 *
 * <p>A backend runs the suite by extending a concrete subclass and supplying {@link #factory()}.
 */
public abstract class ConformanceDbTestBase {

  protected ZeebeDb<Cf> db;
  protected TransactionContext context;

  @TempDir Path tempDir;

  protected abstract <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory();

  @BeforeEach
  void openDb() {
    db = this.<Cf>factory().createDb(tempDir.toFile());
    context = db.createContext();
  }

  @AfterEach
  void closeDb() throws Exception {
    db.close();
  }

  protected final <K extends DbKey, V extends DbValue> ColumnFamily<K, V> columnFamily(
      final Cf cf, final K key, final V value) {
    return db.createColumnFamily(cf, context, key, value);
  }

  protected final ColumnFamily<DbLong, DbLong> longColumnFamily(final Cf cf) {
    return columnFamily(cf, new DbLong(), new DbLong());
  }

  protected static DbLong dbLong(final long value) {
    final var key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  protected static DbInt dbInt(final int value) {
    final var key = new DbInt();
    key.wrapInt(value);
    return key;
  }

  protected static DbString dbString(final String value) {
    final var key = new DbString();
    key.wrapString(value);
    return key;
  }

  protected static DbCompositeKey<DbLong, DbLong> longPair(final long first, final long second) {
    return new DbCompositeKey<>(dbLong(first), dbLong(second));
  }

  protected static DbCompositeKey<DbString, DbLong> stringLong(
      final String first, final long second) {
    return new DbCompositeKey<>(dbString(first), dbLong(second));
  }

  /** Returns the serialized form of the key, which is what a bytewise comparator sees. */
  protected static byte[] serialize(final DbKey key) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = key.write(buffer, 0);
    final byte[] bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  protected static DirectBuffer wrapBytes(final byte[] bytes) {
    return new UnsafeBuffer(bytes);
  }

  /** Column families available to every conformance test. */
  public enum Cf implements EnumValue, ScopedColumnFamily {
    DEFAULT, // some backends need a default column family
    ONE,
    TWO,
    THREE;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
