/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static dev.hegel.Generators.longs;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.HegelTest;
import dev.hegel.Invariant;
import dev.hegel.OptBoolean;
import dev.hegel.Rule;
import dev.hegel.Stateful;
import dev.hegel.TestCase;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class ColumnFamilyRandomizedPropertyTest {

  private ColumnFamily<DbLong, DbLong> columnFamily;
  private ZeebeDb<DefaultColumnFamily> zeebeDb;

  @BeforeEach
  void setup() {
    final var pathName = Files.newTemporaryFolder();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    zeebeDb = dbFactory.createDb(pathName);

    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), new DbLong(), new DbLong());
  }

  @AfterEach
  void teardown() throws Exception {
    zeebeDb.close();
  }

  @HegelTest(derandomize = OptBoolean.FALSE)
  void columnFamilyHasSameEntriesAsMapAfterModifying(final TestCase tc) {
    try {
      Stateful.run(new ColumnFamilyModel(), tc);
    } finally {
      columnFamily.forEach((k, v) -> columnFamily.deleteExisting(k));
    }
  }

  /** Drives the column family and a plain map with the same operations, expecting them to agree. */
  private final class ColumnFamilyModel {
    private final Map<Long, Long> map = new HashMap<>();

    @Rule
    void insert(final TestCase tc) {
      apply(tc, columnFamily::insert, map::putIfAbsent);
    }

    @Rule
    void update(final TestCase tc) {
      apply(tc, columnFamily::update, (k, v) -> map.computeIfPresent(k, (k1, oldValue) -> v));
    }

    @Rule
    void upsert(final TestCase tc) {
      apply(tc, columnFamily::upsert, map::put);
    }

    @Rule
    void deleteExisting(final TestCase tc) {
      apply(tc, (k, v) -> columnFamily.deleteExisting(k), (k, v) -> map.remove(k));
    }

    @Rule
    void deleteIfExists(final TestCase tc) {
      apply(tc, (k, v) -> columnFamily.deleteIfExists(k), (k, v) -> map.remove(k));
    }

    @Invariant
    void hasSameEntriesAsMap(final TestCase tc) {
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

    private void apply(
        final TestCase tc,
        final BiConsumer<DbLong, DbLong> columnFamilyOperation,
        final BiConsumer<Long, Long> mapOperation) {
      final long key = tc.draw(longs().min(0), "key");
      final long value = tc.draw(longs(), "value");
      mapOperation.accept(key, value);

      final var dbKey = new DbLong();
      final var dbValue = new DbLong();
      dbKey.wrapLong(key);
      dbValue.wrapLong(value);
      try {
        columnFamilyOperation.accept(dbKey, dbValue);
      } catch (final RuntimeException e) {
        assertThat(e).isInstanceOf(ZeebeDbInconsistentException.class);
      }
    }
  }
}
