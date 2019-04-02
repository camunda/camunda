/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.DbString;
import io.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZeebeRocksDbTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldCreateSnapshot() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class);

    final File pathName = temporaryFolder.newFolder();
    final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    final ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.put(key, value);

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
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class);
    final File pathName = temporaryFolder.newFolder();
    ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.put(key, value);
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
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class);
    final File pathName = temporaryFolder.newFolder();
    ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    final DbString key = new DbString();
    key.wrapString("foo");
    final DbString value = new DbString();
    value.wrapString("bar");
    ColumnFamily<DbString, DbString> columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
    columnFamily.put(key, value);

    final File snapshotDir = new File(temporaryFolder.newFolder(), "snapshot");
    db.createSnapshot(snapshotDir);
    value.wrapString("otherString");
    columnFamily.put(key, value);

    // when
    assertThat(pathName.listFiles()).isNotEmpty();
    db.close();
    db = dbFactory.createDb(snapshotDir);
    columnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);

    // then
    final DbString dbString = columnFamily.get(key);

    assertThat(dbString).isNotNull();
    assertThat(dbString.toString()).isEqualTo("bar");
  }
}
