/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.PathAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZeebeRocksDbTest {

  @Test
  void shouldCreateSnapshot(final @TempDir Path tempDir) throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    final ZeebeDb<DefaultColumnFamily> db =
        dbFactory.createDb(Files.createDirectory(tempDir.resolve("db")).toFile());

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    final ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.insert(key, value);

    // when
    final var snapshotDir = tempDir.resolve("snapshot").toFile();
    db.createSnapshot(snapshotDir);

    // then
    PathAssert.assertThatPath(tempDir.resolve("db")).isNotEmptyDirectory();
    db.close();
  }

  @Test
  void shouldReopenDb(final @TempDir File pathName) throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName, false);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.insert(key, value);
    db.close();

    // when
    db = dbFactory.createDb(pathName);

    // then
    columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    final DbString zbString = columnFamily.get(key);
    assertThat(zbString).hasToString("bar");
    db.close();
  }

  @Test
  void shouldRecoverFromSnapshot(final @TempDir Path tempDir) throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    ZeebeDb<DefaultColumnFamily> db =
        dbFactory.createDb(Files.createDirectory(tempDir.resolve("db")).toFile());

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.insert(key, value);

    final var snapshotDir = tempDir.resolve("snapshot").toFile();
    db.createSnapshot(snapshotDir);
    value.wrapString("otherString");
    columnFamily.update(key, value);

    // when
    assertThat(tempDir.resolve("db")).isNotEmptyDirectory();
    db.close();
    db = dbFactory.createDb(snapshotDir);
    columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);

    // then
    final DbString dbString = columnFamily.get(key);
    assertThat(dbString).hasToString("bar");
  }

  @Test
  void shouldRemoveMetricsOnClose(final @TempDir Path tempDir) throws Exception {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        DefaultZeebeDbFactory.getDefaultFactory(() -> meterRegistry);
    final Counter counter;

    // when
    try (final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(tempDir.toFile())) {
      counter = Counter.builder(TestDoc.FOO.getName()).register(db.getMeterRegistry());
      counter.increment();
    }

    // then, the counter is still accessible directly, but not registered to the underlying
    // registries
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isOne();
    assertThat(meterRegistry.find(TestDoc.FOO.getName()).meter()).isNull();
  }

  @Test
  void shouldCloseRegistryOnClose(final @TempDir Path tempDir) throws Exception {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        DefaultZeebeDbFactory.getDefaultFactory(() -> meterRegistry);
    final MeterRegistry dbRegistry;

    // when
    try (final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(tempDir.toFile())) {
      dbRegistry = db.getMeterRegistry();
    }

    // then
    assertThat(dbRegistry).returns(true, MeterRegistry::isClosed);
  }

  @SuppressWarnings("NullableProblems")
  private enum TestDoc implements ExtendedMeterDocumentation {
    FOO {
      @Override
      public String getDescription() {
        return "foo description";
      }

      @Override
      public String getName() {
        return "foo";
      }

      @Override
      public Type getType() {
        return Type.GAUGE;
      }
    }
  }
}
