/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.journal.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.atomix.storage.journal.Indexed;
import org.junit.Test;

/** Sparse journal index test. */
public class SparseJournalIndexTest {

  @Test
  public void shouldNotFindIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    final Position position = index.lookup(1);

    // then
    assertNull(position);
  }

  public static Indexed asIndexedEntry(final long index) {
    return new Indexed(index, null, 0);
  }

  @Test
  public void shouldFindIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);

    // then
    assertEquals(5, index.lookup(5).index());
    assertEquals(10, index.lookup(5).position());
  }

  @Test
  public void shouldFindLowerIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);

    // when
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
  }

  @Test
  public void shouldFindNextIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);

    // when
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // then
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldTruncateIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.truncate(8);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
    assertEquals(5, index.lookup(10).index());
    assertEquals(10, index.lookup(10).position());
  }

  @Test
  public void shouldTruncateCompleteIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);
    index.truncate(8);

    // when
    index.truncate(4);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));
    assertNull(index.lookup(10));
  }

  @Test
  public void shouldNotCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.compact(8);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asIndexedEntry(1), 2);
    index.index(asIndexedEntry(2), 4);
    index.index(asIndexedEntry(3), 6);
    index.index(asIndexedEntry(4), 8);
    index.index(asIndexedEntry(5), 10);
    index.index(asIndexedEntry(6), 12);
    index.index(asIndexedEntry(7), 14);
    index.index(asIndexedEntry(8), 16);
    index.index(asIndexedEntry(9), 18);
    index.index(asIndexedEntry(10), 20);

    // when
    index.compact(11);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));

    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
    assertEquals(10, index.lookup(12).index());
    assertEquals(20, index.lookup(12).position());
  }
}
