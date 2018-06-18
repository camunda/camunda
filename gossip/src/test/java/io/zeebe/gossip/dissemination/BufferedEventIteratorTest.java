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
package io.zeebe.gossip.dissemination;

import static io.zeebe.gossip.dissemination.BufferedEventIterator.DEFAULT_SPREAD_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BufferedEventIteratorTest {
  public static final int WORK_COUNT = 1_000;
  private final List<BufferedEvent<Integer>> eventList = new ArrayList<>();

  @Rule public final ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    for (int i = 0; i < WORK_COUNT; i++) {
      eventList.add(new BufferedEvent<>(i));
    }
  }

  @Test
  public void shouldHasNext() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    bufferedEventIterator.wrap(iterator, 10);

    // when
    final boolean hasNext = bufferedEventIterator.hasNext();

    // then
    assertThat(hasNext).isTrue();
  }

  @Test
  public void shouldIterateToLimit() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    final int limit = 10;
    bufferedEventIterator.wrap(iterator, limit);

    // when - then
    int count = 0;
    while (bufferedEventIterator.hasNext()) {
      final Integer next = bufferedEventIterator.next();
      assertThat(next).isEqualTo(count);
      count++;
    }
    assertThat(count).isEqualTo(limit);
  }

  @Test
  public void shouldNotIterateOverLimit() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    final int limit = 10;
    bufferedEventIterator.wrap(iterator, limit);

    // then
    for (int i = 0; i < limit; i++) {
      assertThat(bufferedEventIterator.hasNext()).isTrue();
      assertThat(bufferedEventIterator.next()).isEqualTo(i);
    }

    assertThat(bufferedEventIterator.hasNext()).isFalse();
  }

  @Test
  public void shouldNotIterateOverSize() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    bufferedEventIterator.wrap(iterator, WORK_COUNT);

    // then
    for (int i = 0; i < WORK_COUNT; i++) {
      assertThat(bufferedEventIterator.hasNext()).isTrue();
      assertThat(bufferedEventIterator.next()).isEqualTo(i);
    }
  }

  @Test
  public void shouldIterateOverAll() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    bufferedEventIterator.wrap(iterator, Integer.MAX_VALUE);

    // when - then
    int count = 0;
    while (bufferedEventIterator.hasNext()) {
      final Integer next = bufferedEventIterator.next();
      assertThat(next).isEqualTo(count);
      count++;
    }
    assertThat(count).isEqualTo(WORK_COUNT);
  }

  @Test
  public void shouldThrowExceptionIfNoNextElementExists() {
    // given
    final Iterator<BufferedEvent<Integer>> iterator = eventList.iterator();
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();
    bufferedEventIterator.wrap(iterator, 1);

    // when
    bufferedEventIterator.next();

    // then
    exception.expect(NoSuchElementException.class);

    bufferedEventIterator.next();
  }

  @Test
  public void shouldNotRemoveIfIncrementSpreadCountFlagIsFalse() {

    // given
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>();

    // when
    for (int i = 0; i < DEFAULT_SPREAD_LIMIT; i++) {
      bufferedEventIterator.wrap(eventList.iterator(), 10);
      while (bufferedEventIterator.hasNext()) {
        bufferedEventIterator.next();
      }
    }

    // then
    bufferedEventIterator.wrap(eventList.iterator(), 10);
    int count = 0;
    while (bufferedEventIterator.hasNext()) {
      final Integer next = bufferedEventIterator.next();
      assertThat(next).isEqualTo(count);
      count++;
    }
    assertThat(count).isEqualTo(10);
  }

  @Test
  public void shouldRemoveIfIncrementSpreadCountFlagIsTrue() {
    // given
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>(true);

    // when
    for (int i = 0; i < DEFAULT_SPREAD_LIMIT; i++) {
      bufferedEventIterator.wrap(eventList.iterator(), 10);
      while (bufferedEventIterator.hasNext()) {
        bufferedEventIterator.next();
      }
    }

    // then
    bufferedEventIterator.wrap(eventList.iterator(), 10);
    int count = 0;
    while (bufferedEventIterator.hasNext()) {
      final Integer next = bufferedEventIterator.next();
      assertThat(next).isEqualTo(count + 10);
      count++;
    }
    assertThat(count).isEqualTo(10);
  }

  @Test
  public void shouldRemoveEarlierIfSpreadLimitIsSetToLowValue() {
    // given
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>(true);
    bufferedEventIterator.setSpreadLimit(1);

    // when
    bufferedEventIterator.wrap(eventList.iterator(), 10);
    while (bufferedEventIterator.hasNext()) {
      bufferedEventIterator.next();
    }

    // then
    bufferedEventIterator.wrap(eventList.iterator(), 10);
    int count = 0;
    while (bufferedEventIterator.hasNext()) {
      final Integer next = bufferedEventIterator.next();
      assertThat(next).isEqualTo(count + 10);
      count++;
    }
    assertThat(count).isEqualTo(10);
  }

  @Test
  public void shouldRemoveAllValues() {
    // given
    final BufferedEventIterator<Integer> bufferedEventIterator = new BufferedEventIterator<>(true);
    bufferedEventIterator.setSpreadLimit(1);

    // when
    bufferedEventIterator.wrap(eventList.iterator(), Integer.MAX_VALUE);
    while (bufferedEventIterator.hasNext()) {
      bufferedEventIterator.next();
    }

    // then
    assertThat(eventList.iterator().hasNext()).isFalse();
    assertThat(eventList.isEmpty()).isTrue();
  }
}
