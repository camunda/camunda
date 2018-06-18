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

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Long2BytesZbMapTest {
  protected static final int TABLE_SIZE = 16;
  protected static final int VALUE_LENGTH = 3 * SIZE_OF_BYTE;

  private static final DirectBuffer VALUE = new UnsafeBuffer("bar".getBytes());
  private static final DirectBuffer ANOTHER_VALUE = new UnsafeBuffer("plo".getBytes());

  private Long2BytesZbMap map;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void createmap() {
    map = new Long2BytesZbMap(TABLE_SIZE, 1, VALUE_LENGTH);
  }

  @After
  public void close() {
    map.close();
  }

  @Test
  public void shouldReturnFalseForEmptyMap() {
    // given that the map is empty
    assertThat(map.get(0)).isNull();
  }

  @Test
  public void shouldReturnFalseForNonExistingKey() {
    // given
    map.put(1, VALUE);

    // then
    assertThat(map.get(0)).isNull();
  }

  @Test
  public void shouldReturnValueForKey() {
    // given
    map.put(1, VALUE);

    // if then
    final DirectBuffer value = map.get(1);
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(VALUE);
  }

  @Test
  public void shouldRemoveValueForKey() {
    // given
    map.put(1, VALUE);

    // if then
    final DirectBuffer value = map.remove(1);
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(VALUE);

    assertThat(map.get(1)).isNull();
  }

  @Test
  public void shouldNotRemoveValueForDifferentKey() {
    // given
    map.put(1, VALUE);
    map.put(2, ANOTHER_VALUE);

    // if
    final DirectBuffer value = map.remove(1);
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(VALUE);

    // then
    assertThat(map.get(1)).isNull();

    final DirectBuffer value2 = map.remove(2);
    assertThat(value2).isNotNull();
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldNotRemoveValueForNonExistingKey() {
    // given
    map.put(1, VALUE);

    // if
    final DirectBuffer value = map.remove(0);
    assertThat(value).isNull();

    // then
    final DirectBuffer value2 = map.get(1);
    assertThat(value2).isNotNull();
    assertThat(value2).isEqualTo(VALUE);
  }

  @Test
  public void shouldSplit() {
    // given
    map.put(0, VALUE);

    // if
    map.put(1, ANOTHER_VALUE);

    // then
    assertThat(map.bucketCount()).isEqualTo(2);

    final DirectBuffer value = map.get(0);
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(VALUE);

    final DirectBuffer value2 = map.get(1);
    assertThat(value2).isNotNull();
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldSplitTwoTimes() {
    // given
    map.put(1, VALUE);
    assertThat(map.bucketCount()).isEqualTo(1);

    // if
    map.put(3, ANOTHER_VALUE);

    // then
    assertThat(map.bucketCount()).isEqualTo(3);

    final DirectBuffer value = map.get(1);
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(VALUE);

    final DirectBuffer value2 = map.get(3);
    assertThat(value2).isNotNull();
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldPutMultipleValues() {
    for (int i = 0; i < 16; i += 2) {
      map.put(i, VALUE);
    }

    for (int i = 1; i < 16; i += 2) {
      map.put(i, ANOTHER_VALUE);
    }

    for (int i = 0; i < 16; i++) {
      final DirectBuffer value = map.get(i);
      assertThat(value).isNotNull();
      assertThat(value).isEqualTo(i % 2 == 0 ? VALUE : ANOTHER_VALUE);
    }

    assertThat(map.bucketCount()).isEqualTo(16);
  }

  @Test
  public void shouldPutMultipleValuesInOrder() {
    for (int i = 0; i < 16; i++) {
      map.put(i, i < 8 ? VALUE : ANOTHER_VALUE);
    }

    for (int i = 0; i < 16; i++) {
      final DirectBuffer value = map.get(i);
      assertThat(value).isNotNull();
      assertThat(value).isEqualTo(i < 8 ? VALUE : ANOTHER_VALUE);
    }

    assertThat(map.bucketCount()).isEqualTo(16);
  }

  @Test
  public void shouldReplaceMultipleValuesInOrder() {
    for (int i = 0; i < 16; i++) {
      map.put(i, VALUE);
    }

    for (int i = 0; i < 16; i++) {
      assertThat(map.put(i, ANOTHER_VALUE)).isTrue();
    }

    assertThat(map.bucketCount()).isEqualTo(16);
  }

  @Test
  public void shouldUseOverflowToPutValue() {
    // given
    map = new Long2BytesZbMap(2, 1, VALUE_LENGTH);
    map.setMaxTableSize(2);
    map.put(0, VALUE);

    // when
    map.put(2, ANOTHER_VALUE);

    // then overflow happens

    // first bucket contains overflow pointer
    final int firstBucketAddress = map.getBucketBufferArray().getFirstBucketOffset();
    assertThat(map.getBucketBufferArray().getBucketOverflowPointer(firstBucketAddress))
        .isGreaterThan(0);

    // get is possible
    final DirectBuffer value = map.get(0);
    assertThat(value).isEqualTo(VALUE);

    final DirectBuffer value2 = map.get(2);
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldUseOverflowOnCollisionIfMapSizeIsReached() {
    // given
    map.setMaxTableSize(16);
    map.put(0L, VALUE);

    // when
    map.put(16L, ANOTHER_VALUE);

    // then resize
    assertThat(map.hashTable.getCapacity()).isEqualTo(16);

    final DirectBuffer value = map.get(0L);
    assertThat(value).isEqualTo(VALUE);
    final DirectBuffer value2 = map.get(16L);
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldResizeOnCollision() {
    // given
    for (int i = 0; i < TABLE_SIZE; i++) {
      map.put(i, VALUE);
    }

    // when
    map.put(16L, ANOTHER_VALUE);

    // then resize
    assertThat(map.hashTable.getCapacity()).isEqualTo(32);

    final DirectBuffer value = map.get(0);
    assertThat(value).isEqualTo(VALUE);
    final DirectBuffer value2 = map.get(16);
    assertThat(value2).isEqualTo(ANOTHER_VALUE);
  }

  @Test
  public void shouldRejectPutIfValueTooLong() {
    // then
    thrown.expect(IllegalArgumentException.class);

    // when
    map.put(0, new UnsafeBuffer(new byte[65]));
  }

  @Test
  public void shouldClear() {
    // given
    map.put(0, VALUE);

    // when
    map.clear();

    // then
    assertThat(map.get(0)).isNull();
  }

  @Test
  public void shouldNotOverwriteValue() {
    // given
    final UnsafeBuffer originalAnotherValue = new UnsafeBuffer(new byte[ANOTHER_VALUE.capacity()]);
    originalAnotherValue.putBytes(0, ANOTHER_VALUE, 0, ANOTHER_VALUE.capacity());
    map.put(0, VALUE);
    map.put(1, ANOTHER_VALUE);

    // when
    map.get(0);

    // then
    assertThat(ANOTHER_VALUE).isEqualTo(originalAnotherValue);
  }

  @Test
  public void shouldIterateOverMap() {
    // given
    final List<Long> keys = LongStream.range(0, 16).boxed().collect(Collectors.toList());

    keys.forEach(k -> map.put(k, VALUE));

    // if then
    final List<Long> foundKeys = new ArrayList<>();

    final Iterator<Long2BytesZbMapEntry> iterator = map.iterator();
    while (iterator.hasNext()) {
      final Long2BytesZbMapEntry entry = iterator.next();

      assertThat(entry.getValue()).isEqualTo(VALUE);

      foundKeys.add(entry.getKey());
    }

    assertThat(foundKeys).hasSameSizeAs(keys).hasSameElementsAs(keys);
  }

  @Test
  public void shouldReturnShorterValue() {
    // given
    map.put(0, VALUE);

    final UnsafeBuffer value = new UnsafeBuffer(new byte[VALUE_LENGTH - 1]);
    value.setMemory(0, value.capacity(), (byte) 1);

    map.put(0, value);

    // when
    final DirectBuffer result = map.get(0);

    // then
    assertThat(result.capacity())
        .isEqualTo(VALUE_LENGTH); // the map does not store the actual value length
    assertThatBuffer(result).hasBytes(value, 0, value.capacity());
    assertThat(result.getByte(VALUE_LENGTH - 1)).isEqualTo((byte) 0);
  }
}
