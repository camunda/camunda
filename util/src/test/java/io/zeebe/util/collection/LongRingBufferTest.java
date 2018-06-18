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
package io.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LongRingBufferTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldAddElement() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    // when
    final boolean elementAdded = buffer.addElementToHead(1L);

    // then
    assertThat(elementAdded).isTrue();
    assertThat(buffer.isSaturated()).isFalse();
  }

  @Test
  public void shouldAddElementsUntilBufferFull() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    // when
    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // then
    assertThat(buffer.isSaturated()).isTrue();
  }

  @Test
  public void shouldConsumeElements() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // when
    buffer.consumeAscendingUntilInclusive(2L);

    // then
    assertThat(buffer.isSaturated()).isFalse();
  }

  @Test
  public void shouldConsumeAllElements() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // when
    buffer.consumeAscendingUntilInclusive(4L);

    // then
    assertThat(buffer.isSaturated()).isFalse();
  }

  @Test
  public void shouldConsumeAllLowerElements() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(5L);
    buffer.addElementToHead(6L);

    // when
    buffer.consumeAscendingUntilInclusive(4L);

    // then
    assertThat(buffer.size()).isEqualTo(2);
  }

  @Test
  public void shouldConsumeAllElementsIfMax() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(0L);
    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);

    // when
    buffer.consumeAscendingUntilInclusive(4L);

    // then
    assertThat(buffer.size()).isEqualTo(0);
  }

  @Test
  public void shouldAddMoreElementsThanCapacity() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);
    buffer.consumeAscendingUntilInclusive(2L);

    // when
    buffer.addElementToHead(5L);
    buffer.addElementToHead(6L);

    // then
    assertThat(buffer.isSaturated()).isTrue();
  }

  /**
   * Note: this test case assumes internal behavior of the class under test (mainly head and tail
   * counter being long values and overflowing). It may become useless with major refactorings.
   */
  @Test
  public void shouldAddValuesOverflowingHeadPointer() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    // currently two elements (head is two positions further than tail)
    buffer.head = Long.MAX_VALUE;
    buffer.tail = Long.MAX_VALUE;

    // when adding two elements, making head flow over
    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);

    // then
    assertThat(buffer.size()).isEqualTo(2);
    assertThat(buffer.isSaturated()).isFalse();

    final List<Long> longList = new ArrayList<>();
    buffer.consume(longList::add);

    assertThat(longList).containsExactly(1L, 2L);
  }

  /**
   * Note: this test case assumes internal behavior of the class under test (mainly head and tail
   * counter being long values and overflowing). It may become useless with major refactorings.
   */
  @Test
  public void shouldFillBufferOverflowingHeadPointer() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    // currently two elements (head is two positions further than tail)
    buffer.head = Long.MAX_VALUE;
    buffer.tail = Long.MAX_VALUE;

    // when adding two elements, making head flow over
    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // then
    assertThat(buffer.size()).isEqualTo(4);
    assertThat(buffer.isSaturated()).isTrue();
  }

  /**
   * Note: this test case assumes internal behavior of the class under test (mainly head and tail
   * counter being long values and overflowing). It may become useless with major refactorings.
   */
  @Test
  public void shouldConsumeUntilOverflowingHeadPointer() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    // currently two elements (head is two positions further than tail)
    buffer.head = Long.MAX_VALUE;
    buffer.tail = Long.MAX_VALUE;

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // when
    buffer.consumeAscendingUntilInclusive(3L);

    // then
    assertThat(buffer.size()).isEqualTo(1);
    assertThat(buffer.isSaturated()).isFalse();
  }

  @Test
  public void shouldCheckBoundsOnInsert() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(4);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);
    buffer.addElementToHead(4L);

    // when
    final boolean elementAdded = buffer.addElementToHead(5L);

    // then
    assertThat(elementAdded).isFalse();
  }

  @Test
  public void shouldWorkWithNonPowerOf2Capacity() {
    // given
    final LongRingBuffer buffer = new LongRingBuffer(3);

    buffer.addElementToHead(1L);
    buffer.addElementToHead(2L);
    buffer.addElementToHead(3L);

    // when
    final boolean elementAdded = buffer.addElementToHead(4L);

    // then
    assertThat(elementAdded).isFalse();
  }
}
