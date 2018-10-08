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

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

public class ZbRocksDbTest {
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
  public void shouldExistAndReadValueBack() {
    // given
    final DirectBuffer key = new UnsafeBuffer(getBytes("key"));
    final DirectBuffer value = new UnsafeBuffer(getBytes("value"));
    final MutableDirectBuffer reader = new UnsafeBuffer(new byte[value.capacity()]);

    // when
    db.put(handle, key, value);

    // then
    assertThat(db.exists(handle, key, reader)).isTrue();
    assertThat(bufferAsString(reader)).isEqualTo("value");
  }

  @Test
  public void shouldIterateOverPrefixOnly() throws RocksDBException {
    // given
    final DirectBuffer prefix = new UnsafeBuffer(getBytes("1"));
    final ZbRocksEntry[] data =
        new ZbRocksEntry[] {
          new ZbRocksEntry(wrapString("0-test"), wrapString("1")),
          new ZbRocksEntry(wrapString("1-first"), wrapString("2")),
          new ZbRocksEntry(wrapString("1-second"), wrapString("3")),
          new ZbRocksEntry(wrapString("2-other"), wrapString("4")),
          new ZbRocksEntry(wrapString("random"), wrapString("5"))
        };
    final List<ZbRocksEntry> entries = new ArrayList<>();

    // when
    dbRule.put(handle, data);
    db.forEachPrefixed(handle, prefix, (e) -> entries.add(new ZbRocksEntry(e)));

    // then
    assertThat(entries.size()).isEqualTo(2);
    assertThat(entries.get(0)).isEqualTo(data[1]);
    assertThat(entries.get(1)).isEqualTo(data[2]);
  }
}
