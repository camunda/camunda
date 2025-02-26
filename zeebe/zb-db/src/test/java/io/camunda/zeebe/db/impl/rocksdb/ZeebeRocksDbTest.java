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
    ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

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
}
