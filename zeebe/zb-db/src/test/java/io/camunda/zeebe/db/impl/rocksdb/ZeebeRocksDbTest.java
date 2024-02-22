/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ZeebeRocksDbTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldCreateSnapshot() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();

    final File pathName = temporaryFolder.newFolder();
    final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    final ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.insert(key, value);

    // when
    final File snapshotDir = new File(temporaryFolder.newFolder(), "snapshot");
    db.createSnapshot(snapshotDir);

    // then
    assertThat(pathName.listFiles()).isNotEmpty();
    db.close();
  }

  @Test
  public void shouldReopenDb() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    final File pathName = temporaryFolder.newFolder();
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
    assertThat(zbString).isNotNull();
    assertThat(zbString.toString()).isEqualTo("bar");

    db.close();
  }

  @Test
  public void shouldRecoverFromSnapshot() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    final File pathName = temporaryFolder.newFolder();
    ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.insert(key, value);

    final File snapshotDir = new File(temporaryFolder.newFolder(), "snapshot");
    db.createSnapshot(snapshotDir);
    value.wrapString("otherString");
    columnFamily.update(key, value);

    // when
    assertThat(pathName).isNotEmptyDirectory();
    db.close();
    db = dbFactory.createDb(snapshotDir);
    columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);

    // then
    final DbString dbString = columnFamily.get(key);

    assertThat(dbString).hasToString("bar");
  }
}
