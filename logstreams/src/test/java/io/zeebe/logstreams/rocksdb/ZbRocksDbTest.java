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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;

public class ZbRocksDbTest {
  private static final DirectBuffer NULL = new UnsafeBuffer(new byte[] {0});
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Options options;
  private ZbRocksDb db;

  @Before
  public void setup() throws IOException, RocksDBException {
    final String dbDirectory = temporaryFolder.newFolder().getAbsolutePath();
    options = new Options().setCreateIfMissing(true);
    db = ZbRocksDb.open(options, dbDirectory);
  }

  @After
  public void teardown() {
    if (db != null) {
      db.close();
    }

    if (options != null) {
      options.close();
    }
  }

  @Test
  public void shouldExistAndReadValueBack() {
    // given
    final DirectBuffer key = new UnsafeBuffer(getBytes("key"));
    final DirectBuffer value = new UnsafeBuffer(getBytes("value"));
    final MutableDirectBuffer reader = new UnsafeBuffer(new byte[value.capacity()]);

    // when
    db.put(db.getDefaultColumnFamily(), key, value);

    // then
    assertThat(db.exists(db.getDefaultColumnFamily(), key, reader)).isTrue();
    assertThat(bufferAsString(reader)).isEqualTo("value");
  }

  @Test
  public void shouldIterateOverPrefixOnly() throws RocksDBException {
    // given
    final DirectBuffer prefix = new UnsafeBuffer(getBytes("1"));
    final byte[] firstValue = new byte[] {1};
    final byte[] secondValue = new byte[] {2};
    final Map<DirectBuffer, DirectBuffer> recorder = new HashMap<>();

    // when
    db.put(getBytes("0-test"), NULL.byteArray());
    db.put(getBytes("1-first"), firstValue);
    db.put(getBytes("1-second"), secondValue);
    db.put(getBytes("2-other"), NULL.byteArray());
    db.put(getBytes("random"), NULL.byteArray());
    db.forEachPrefixed(
        db.getDefaultColumnFamily(),
        prefix,
        (entry, c) -> recorder.put(entry.getKey(), entry.getValue()));

    // then
    assertThat(recorder.size()).isEqualTo(2);
    assertThat(recorder.get(wrapString("1-first")).byteArray()).isEqualTo(firstValue);
    assertThat(recorder.get(wrapString("1-second")).byteArray()).isEqualTo(secondValue);
  }

  @Test
  public void shouldStopIteratingWhenControlStopIsTrue() throws RocksDBException {
    // given
    final byte[] firstValue = new byte[] {1};
    final byte[] secondValue = new byte[] {2};
    final Map<DirectBuffer, DirectBuffer> recorder = new HashMap<>();
    final AtomicInteger i = new AtomicInteger(0);

    // when
    db.put(getBytes("0-test"), firstValue);
    db.put(getBytes("1-first"), secondValue);
    db.put(getBytes("1-second"), NULL.byteArray());
    db.put(getBytes("2-other"), NULL.byteArray());
    db.forEach(
        db.getDefaultColumnFamily(),
        (entry, c) -> {
          if (i.get() == 2) {
            c.stop();
            return;
          }

          recorder.put(entry.getKey(), entry.getValue());
          i.set(i.intValue() + 1);
        });

    // then
    assertThat(recorder.size()).isEqualTo(2);
    assertThat(recorder.get(wrapString("0-test")).byteArray()).isEqualTo(firstValue);
    assertThat(recorder.get(wrapString("1-first")).byteArray()).isEqualTo(secondValue);
  }
}
