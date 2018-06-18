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
package io.zeebe.map;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.test.util.io.RepeatedlyFailingInputStream;
import io.zeebe.test.util.io.ShortReadInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ZbMapSerializerTest {
  private static final int DATASET_SIZE = 100_000;
  private static final int NO_SUCH_KEY = -1;

  private Long2LongZbMap map;
  private ZbMapSerializer mapSerializer;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void createmap() throws Exception {
    map = new Long2LongZbMap();
    mapSerializer = new ZbMapSerializer();
  }

  @After
  public void close() {
    map.close();
  }

  @Test
  public void shouldRestoreEmptyMap() throws IOException {
    writeAndReadmap(map);

    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(NO_SUCH_KEY);
    }
  }

  @Test
  public void shouldRestoreEmptyMapInNewMapObject() throws IOException {
    final InputStream inputStream = writeMap(map);

    // when
    final Long2LongZbMap newMap = new Long2LongZbMap();
    readMap(newMap, inputStream);

    // then
    assertThat(newMap.size()).isEqualTo(map.size());
    assertThat(newMap.getHashTable().getCapacity()).isEqualTo(map.getHashTable().getCapacity());
    assertThat(newMap.getHashTable().getLength()).isEqualTo(map.getHashTable().getLength());

    assertThat(newMap.getBucketBufferArray().size()).isEqualTo(map.getBucketBufferArray().size());
    assertThat(newMap.getBucketBufferArray().getBucketBufferCount())
        .isEqualTo(map.getBucketBufferArray().getBucketBufferCount());
    assertThat(newMap.getBucketBufferArray().getBucketCount())
        .isEqualTo(map.getBucketBufferArray().getBucketCount());
    assertThat(newMap.getBucketBufferArray().getBlockCount())
        .isEqualTo(map.getBucketBufferArray().getBlockCount());

    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(newMap.get(i, NO_SUCH_KEY)).isEqualTo(NO_SUCH_KEY);
    }
    newMap.close();
  }

  @Test
  public void shouldRestoreFullmap() throws IOException {
    fillMap(map);

    writeAndReadmap(map);

    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(i);
    }
  }

  @Test
  public void shouldRestoreInNewMapObject() throws IOException {
    // given
    fillMap(map);

    final InputStream inputStream = writeMap(map);

    // when
    final Long2LongZbMap newMap = new Long2LongZbMap();
    readMap(newMap, inputStream);

    // then
    assertThat(newMap.size()).isEqualTo(map.size());
    assertThat(newMap.getHashTable().getCapacity()).isEqualTo(map.getHashTable().getCapacity());
    assertThat(newMap.getHashTable().getLength()).isEqualTo(map.getHashTable().getLength());

    assertThat(newMap.getBucketBufferArray().size()).isEqualTo(map.getBucketBufferArray().size());
    assertThat(newMap.getBucketBufferArray().getBucketBufferCount())
        .isEqualTo(map.getBucketBufferArray().getBucketBufferCount());
    assertThat(newMap.getBucketBufferArray().getBucketCount())
        .isEqualTo(map.getBucketBufferArray().getBucketCount());
    assertThat(newMap.getBucketBufferArray().getBlockCount())
        .isEqualTo(map.getBucketBufferArray().getBlockCount());

    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(newMap.get(i, NO_SUCH_KEY)).isEqualTo(i);
    }
    newMap.close();
  }

  @Test
  public void shouldRestoreFullMapWithTemporaryReadErrors() throws IOException {
    // given
    fillMap(map);
    final InputStream inputStream = writeMap(map);
    map.clear();

    // when
    readMap(map, new RepeatedlyFailingInputStream(inputStream));

    // then
    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(i);
    }
  }

  @Test
  public void shouldRestoreFullMapWithTemporaryReadErrorsInNewMapObject() throws IOException {
    // given
    fillMap(map);

    final InputStream inputStream = writeMap(map);

    // when
    final Long2LongZbMap newMap = new Long2LongZbMap();
    readMap(newMap, new RepeatedlyFailingInputStream(inputStream));

    // then
    assertThat(newMap.size()).isEqualTo(map.size());
    assertThat(newMap.getHashTable().getCapacity()).isEqualTo(map.getHashTable().getCapacity());
    assertThat(newMap.getHashTable().getLength()).isEqualTo(map.getHashTable().getLength());

    assertThat(newMap.getBucketBufferArray().size()).isEqualTo(map.getBucketBufferArray().size());
    assertThat(newMap.getBucketBufferArray().getBucketBufferCount())
        .isEqualTo(map.getBucketBufferArray().getBucketBufferCount());
    assertThat(newMap.getBucketBufferArray().getBucketCount())
        .isEqualTo(map.getBucketBufferArray().getBucketCount());
    assertThat(newMap.getBucketBufferArray().getBlockCount())
        .isEqualTo(map.getBucketBufferArray().getBlockCount());

    for (int i = 0; i < DATASET_SIZE; i++) {
      assertThat(newMap.get(i, NO_SUCH_KEY)).isEqualTo(i);
    }
    newMap.close();
  }

  @Test
  public void shouldThrowOnShortReadOfmapBuffer() throws IOException {
    // given
    fillMap(map);
    final InputStream inputStream =
        new ShortReadInputStream(writeMap(map), 1024 + SIZE_OF_INT, false);

    expectedException.expect(IOException.class);
    expectedException.expectMessage(
        "Unable to read full map buffer from input stream. "
            + "Only read 1020 bytes but expected 65536 bytes.");

    // when
    readMap(map, inputStream);
  }

  @Test
  public void shouldThrowOnFailedReadOfmapBuffer() throws IOException {
    // given
    fillMap(map);
    final InputStream inputStream = new ShortReadInputStream(writeMap(map), 1025, true);

    expectedException.expect(IOException.class);
    expectedException.expectMessage("Read failure");

    // when
    readMap(map, inputStream);
  }

  @Test
  public void shouldThrowOnIllegalVersion() throws IOException {
    final ZbMapSerializer mapSerializer = new ZbMapSerializer();
    mapSerializer.wrap(map);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    mapSerializer.writeToStream(out);

    map.clear();

    final byte[] byteArray = out.toByteArray();

    // set illegal version
    new UnsafeBuffer(byteArray).putInt(0, 1001);
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

    expectedException.expect(RuntimeException.class);
    mapSerializer.readFromStream(inputStream);
  }

  @Test
  public void shouldThrowOnShortReadOfVersion() throws IOException {
    // given
    fillMap(map);
    final InputStream inputStream = new ShortReadInputStream(writeMap(map), 3, false);

    expectedException.expect(IOException.class);
    expectedException.expectMessage("Unable to read map snapshot version");

    // when
    readMap(map, inputStream);
  }

  private static void fillMap(final Long2LongZbMap map) {
    for (int i = 0; i < DATASET_SIZE; i++) {
      map.put(i, i);
    }
  }

  private void writeAndReadmap(final ZbMap map) throws IOException {
    final long sizeBefore = map.size();
    final InputStream inputStream = writeMap(map);

    map.clear();

    readMap(map, inputStream);
    final long sizeAfter = map.size();
    assertThat(sizeBefore).isEqualTo(sizeAfter);
  }

  private InputStream writeMap(final ZbMap map) throws IOException {
    mapSerializer.wrap(map);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    mapSerializer.writeToStream(out);
    assertThat((long) out.toByteArray().length).isEqualTo(mapSerializer.serializationSize());

    return new ByteArrayInputStream(out.toByteArray());
  }

  private void readMap(final ZbMap map, final InputStream inputStream) throws IOException {
    mapSerializer.wrap(map);
    mapSerializer.readFromStream(inputStream);
  }
}
