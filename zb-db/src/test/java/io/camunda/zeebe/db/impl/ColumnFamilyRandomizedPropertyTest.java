/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.ListArbitrary;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.assertj.core.util.Files;

public class ColumnFamilyRandomizedPropertyTest {

  private Map<Long, Long> map;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private ZeebeDb<DefaultColumnFamily> zeebeDb;

  @BeforeProperty
  private void setup() {
    final var pathName = Files.newTemporaryFolder();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    zeebeDb = dbFactory.createDb(pathName);

    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), new DbLong(), new DbLong());
    map = new HashMap<>();
  }

  @Property
  void columnFamilyHasSameEntriesAsMapAfterModifying(
      @ForAll("operations") final Iterable<TestableOperation> operations) {
    operations.forEach(op -> op.apply(map));
    operations.forEach(op -> op.apply(columnFamily));
    assertEqualEntries(columnFamily, map);
  }

  @AfterTry
  private void cleanup() {
    columnFamily.forEach((k, v) -> columnFamily.deleteExisting(k));
    map.clear();
  }

  @AfterProperty
  private void teardown() throws Exception {
    zeebeDb.close();
  }

  @Provide
  ListArbitrary<TestableOperation> operations() {
    final var op =
        Arbitraries.of(
            InsertOp.class,
            UpdateOp.class,
            UpsertOp.class,
            DeleteExisting.class,
            DeleteIfExists.class);
    final var k = Arbitraries.longs().greaterOrEqual(0);
    final var v = Arbitraries.longs();
    return Combinators.combine(op, k, v)
        .as(
            (arbitraryOp, arbitraryK, arbitraryV) -> {
              try {
                return (TestableOperation)
                    arbitraryOp
                        .getDeclaredConstructor(long.class, long.class)
                        .newInstance(arbitraryK, arbitraryV);
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            })
        .list();
  }

  private void assertEqualEntries(
      final ColumnFamily<DbLong, DbLong> columnFamily, final Map<Long, Long> map) {
    map.forEach(
        (key, value) -> {
          final var dbKey = new DbLong();
          dbKey.wrapLong(key);
          assertThat(columnFamily.get(dbKey))
              .as("Key " + dbKey.getValue() + " should exist ")
              .isNotNull()
              .as("Key " + dbKey.getValue() + " should have value " + value)
              .extracting(DbLong::getValue)
              .isEqualTo(value);
        });
    columnFamily.forEach(
        (key, value) -> assertThat(map).containsEntry(key.getValue(), value.getValue()));
  }

  record InsertOp(long key, long value) implements TestableOperation {

    @Override
    public BiConsumer<DbLong, DbLong> modify(final ColumnFamily<DbLong, DbLong> columnFamily) {
      return columnFamily::insert;
    }

    @Override
    public BiConsumer<Long, Long> modify(final Map<Long, Long> map) {
      return map::putIfAbsent;
    }
  }

  record UpdateOp(long key, long value) implements TestableOperation {

    @Override
    public BiConsumer<DbLong, DbLong> modify(final ColumnFamily<DbLong, DbLong> columnFamily) {
      return columnFamily::update;
    }

    @Override
    public BiConsumer<Long, Long> modify(final Map<Long, Long> map) {
      return (k, newValue) -> map.computeIfPresent(k, (k1, oldValue) -> newValue);
    }
  }

  record UpsertOp(long key, long value) implements TestableOperation {

    @Override
    public BiConsumer<DbLong, DbLong> modify(final ColumnFamily<DbLong, DbLong> columnFamily) {
      return columnFamily::upsert;
    }

    @Override
    public BiConsumer<Long, Long> modify(final Map<Long, Long> map) {
      return map::put;
    }
  }

  record DeleteIfExists(long key, long value) implements TestableOperation {

    @Override
    public BiConsumer<DbLong, DbLong> modify(final ColumnFamily<DbLong, DbLong> columnFamily) {
      return (k, v) -> columnFamily.deleteIfExists(k);
    }

    @Override
    public BiConsumer<Long, Long> modify(final Map<Long, Long> map) {
      return (k, v) -> map.remove(k);
    }
  }

  record DeleteExisting(long key, long value) implements TestableOperation {

    @Override
    public BiConsumer<DbLong, DbLong> modify(final ColumnFamily<DbLong, DbLong> columnFamily) {
      return (k, v) -> columnFamily.deleteExisting(k);
    }

    @Override
    public BiConsumer<Long, Long> modify(final Map<Long, Long> map) {
      return (k, v) -> map.remove(k);
    }
  }

  interface TestableOperation {
    long key();

    long value();

    BiConsumer<DbLong, DbLong> modify(ColumnFamily<DbLong, DbLong> columnFamily);

    BiConsumer<Long, Long> modify(Map<Long, Long> map);

    default void apply(final ColumnFamily<DbLong, DbLong> columnFamily) {
      final var dbKey = new DbLong();
      final var dbValue = new DbLong();
      dbKey.wrapLong(key());
      dbValue.wrapLong(value());
      try {
        modify(columnFamily).accept(dbKey, dbValue);
      } catch (final RuntimeException e) {
        assertThat(e).hasRootCauseInstanceOf(ZeebeDbInconsistentException.class);
      }
    }

    default void apply(final Map<Long, Long> map) {
      modify(map).accept(key(), value());
    }
  }
}
