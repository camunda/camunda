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

import java.io.IOException;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

public class ZbWriteBatchTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private String dbDirectory;

  @Before
  public void setup() throws IOException {
    dbDirectory = temporaryFolder.newFolder().getAbsolutePath();
  }

  @Test
  public void shouldAllowReusingTheSameBuffersAcrossBatchOperations() throws RocksDBException {
    // given
    final ZbRocksDb db = ZbRocksDb.open(new Options().setCreateIfMissing(true), dbDirectory);
    final ZbWriteBatch batch = new ZbWriteBatch();
    final MutableDirectBuffer key = new ExpandableArrayBuffer();
    final MutableDirectBuffer value = new ExpandableArrayBuffer();

    // when
    key.putInt(0, 1);
    value.putInt(0, 3);
    batch.put(db.getDefaultColumnFamily(), key, value);

    key.putInt(0, 2);
    value.putInt(0, 1);
    batch.put(db.getDefaultColumnFamily(), key, value);

    key.putInt(0, 1);
    batch.delete(db.getDefaultColumnFamily(), key);

    db.write(new WriteOptions(), batch);

    // then
    key.putInt(0, 2);
    db.get(db.getDefaultColumnFamily(), key, value);
    assertThat(value.getInt(0)).isEqualTo(1);

    key.putInt(0, 1);
    assertThat(db.exists(db.getDefaultColumnFamily(), key)).isFalse();
  }
}
