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

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.map.iterator.ZbMapEntry;
import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class ZbMapIteratorTest {
  private ZbMap<LongKeyHandler, LongValueHandler> map;

  @Rule public ExpectedException thrown = ExpectedException.none();

  private ZbMapIterator<LongKeyHandler, LongValueHandler, Long2LongMapEntry> iterator;

  @Before
  public void init() {
    map = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG) {};

    iterator = new ZbMapIterator<>(map, new Long2LongMapEntry());
  }

  @After
  public void tearDown() {
    map.close();
  }

  @Test
  public void shouldNotHasNextIfEmpty() {
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldGetFirstEntry() {
    // given
    putValue(map, 0L, 1L);

    // when
    iterator.reset();
    assertThat(iterator.hasNext()).isTrue();

    final Long2LongMapEntry entry = iterator.next();

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.key).isEqualTo(0L);
    assertThat(entry.value).isEqualTo(1L);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowNoSuchElementException() {
    // expect
    thrown.expect(NoSuchElementException.class);

    // when
    iterator.next();
  }

  @Test
  public void shouldIteratorOverMoreThenOneFilledBucketBuffers() {
    // given
    final List<Long> keys = LongStream.range(0, 4096).boxed().collect(Collectors.toList());

    keys.forEach(k -> putValue(map, k, k + 1));
    assertThat(map.bucketBufferArray.getBucketBufferCount()).isEqualTo(64);

    // if then
    final List<Long> foundKeys = new ArrayList<>();

    iterator.reset();
    while (iterator.hasNext()) {
      final Long2LongMapEntry entry = iterator.next();

      assertThat(entry.value).isEqualTo(entry.key + 1);

      foundKeys.add(entry.key);
    }

    assertThat(foundKeys).hasSameSizeAs(keys).hasSameElementsAs(keys);
  }

  @Test
  public void shouldGetEntriesWithUnfilledBuckets() {
    // given
    putValue(map, 0L, 0L);

    putValue(map, 1L, 1L);
    putValue(map, 5L, 1L);

    // if then
    final List<Long> foundKeys = new ArrayList<>();

    iterator.reset();
    while (iterator.hasNext()) {
      final Long2LongMapEntry entry = iterator.next();

      foundKeys.add(entry.key);
    }

    assertThat(foundKeys).hasSize(3).contains(0L, 1L, 5L);
  }

  @Test
  public void shouldGetEntriesWithEmptyBuckets() {
    // given
    putValue(map, 0L, 0L);
    putValue(map, 2L, 0L);
    putValue(map, 4L, 0L);

    // if then
    final List<Long> foundKeys = new ArrayList<>();

    iterator.reset();
    while (iterator.hasNext()) {
      final Long2LongMapEntry entry = iterator.next();

      foundKeys.add(entry.key);
    }

    assertThat(foundKeys).hasSize(3).contains(0L, 2L, 4L);
  }

  @Test
  public void shouldGetEntriesWithOverflowBuckets() {
    // given
    putValue(map, 0L, 0L);
    putValue(map, 4L, 4L);
    putValue(map, 8L, 8L);
    putValue(map, 16L, 16L);

    // if then
    final Map<Long, Long> foundValues = new HashMap<>();

    iterator.reset();
    while (iterator.hasNext()) {
      final Long2LongMapEntry entry = iterator.next();
      foundValues.put(entry.key, entry.value);
    }

    assertThat(foundValues)
        .hasSize(4)
        .containsEntry(0L, 0L)
        .containsEntry(4L, 4L)
        .containsEntry(8L, 8L)
        .containsEntry(16L, 16L);
  }

  @Test
  public void shouldResetIterator() {
    // given
    putValue(map, 0L, 1L);

    iterator.reset();
    while (iterator.hasNext()) {
      iterator.next();
    }

    // when
    iterator.reset();
    assertThat(iterator.hasNext()).isTrue();

    final Long2LongMapEntry entry = iterator.next();

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.key).isEqualTo(0L);
    assertThat(entry.value).isEqualTo(1L);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowExceptionWhileConcurrentModification() {
    // given
    putValue(map, 0L, 0L);

    iterator.reset();
    while (iterator.hasNext()) {
      iterator.next();
    }

    putValue(map, 1L, 1L);

    // then
    thrown.expect(ConcurrentModificationException.class);

    // when
    iterator.hasNext();
  }

  @Test
  public void shouldNotSupportRemove() {
    // given
    putValue(map, 0L, 1L);

    iterator.reset();

    // then
    thrown.expect(UnsupportedOperationException.class);

    // when
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }

  public static void putValue(
      ZbMap<? extends LongKeyHandler, LongValueHandler> zbMap, long key, long value) {
    zbMap.keyHandler.theKey = key;
    zbMap.valueHandler.theValue = value;
    zbMap.put();
  }

  private final class Long2LongMapEntry implements ZbMapEntry<LongKeyHandler, LongValueHandler> {
    long key;
    long value;

    @Override
    public void read(LongKeyHandler keyHander, LongValueHandler valueHandler) {
      key = keyHander.theKey;
      value = valueHandler.theValue;
    }
  }
}
