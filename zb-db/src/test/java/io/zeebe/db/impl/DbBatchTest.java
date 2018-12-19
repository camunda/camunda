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
package io.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DbBatchTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ZeebeDbFactory<ColumnFamilies> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory(ColumnFamilies.class);

  private ZeebeDb<ColumnFamilies> zeebeDb;

  private ColumnFamily<DbLong, DbLong> oneColumnFamily;
  private ColumnFamily<DbLong, DbLong> twoColumnFamily;
  private ColumnFamily<DbLong, DbLong> threeColumnFamily;

  private DbLong oneKey;
  private DbLong oneValue;
  private DbLong twoValue;
  private DbLong twoKey;
  private DbLong threeKey;
  private DbLong threeValue;

  private enum ColumnFamilies {
    DEFAULT, // rocksDB needs a default column family
    ONE,
    TWO,
    THREE
  }

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    oneKey = new DbLong();
    oneValue = new DbLong();
    oneColumnFamily = zeebeDb.createColumnFamily(ColumnFamilies.ONE, oneKey, oneValue);

    twoKey = new DbLong();
    twoValue = new DbLong();
    twoColumnFamily = zeebeDb.createColumnFamily(ColumnFamilies.TWO, twoKey, twoValue);

    threeKey = new DbLong();
    threeValue = new DbLong();
    threeColumnFamily = zeebeDb.createColumnFamily(ColumnFamilies.THREE, threeKey, threeValue);
  }

  @Test
  public void shouldWriteBatch() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    // when
    zeebeDb.batch(
        () -> {
          oneColumnFamily.put(oneKey, oneValue);
          twoColumnFamily.put(twoKey, twoValue);
          threeColumnFamily.put(threeKey, threeValue);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
  }

  @Test
  public void shouldWriteAndDeleteInBatch() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);
    twoColumnFamily.put(twoKey, twoValue);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    // when
    zeebeDb.batch(
        () -> {
          oneColumnFamily.put(oneKey, oneValue);
          twoColumnFamily.delete(twoKey);
          threeColumnFamily.put(threeKey, threeValue);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(twoColumnFamily.exists(twoKey)).isFalse();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
  }

  @Test
  public void shouldNotWriteOnError() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);
    twoColumnFamily.put(twoKey, twoValue);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    // when
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    try {
      zeebeDb.batch(
          () -> {
            oneColumnFamily.put(oneKey, oneValue);
            twoColumnFamily.delete(twoKey);
            threeColumnFamily.put(threeKey, threeValue);
            throw new RuntimeException();
          });
    } catch (Exception e) {
      // ignore
    }

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isFalse();
  }
}
