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
package io.zeebe.logstreams.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class ZbWriteBatchTest {
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZbRocksDbRule dbRule = new ZbRocksDbRule(temporaryFolder);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(dbRule);

  private ZbRocksDb db;
  private ColumnFamilyHandle handle;

  @Before
  public void setup() {
    db = dbRule.getDb();
    handle = db.getDefaultColumnFamily();
  }

  @Test
  public void shouldAllowReusingTheSameBuffersAcrossBatchOperations() throws RocksDBException {
    // given
    final ZbWriteBatch batch = new ZbWriteBatch();
    final MutableDirectBuffer key = new ExpandableArrayBuffer();
    final MutableDirectBuffer value = new ExpandableArrayBuffer();

    // when
    key.putInt(0, 1);
    value.putInt(0, 3);
    batch.put(handle, key, value);

    key.putInt(0, 2);
    value.putInt(0, 1);
    batch.put(handle, key, value);

    key.putInt(0, 1);
    batch.delete(handle, key);

    db.write(new WriteOptions(), batch);

    // then
    key.putInt(0, 2);
    db.get(handle, key, value);
    assertThat(value.getInt(0)).isEqualTo(1);

    key.putInt(0, 1);
    assertThat(db.exists(handle, key)).isFalse();
  }
}
