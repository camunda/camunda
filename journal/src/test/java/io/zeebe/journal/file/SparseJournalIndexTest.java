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
package io.zeebe.journal.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.zeebe.journal.JournalRecord;
import org.junit.jupiter.api.Test;

/** Sparse journal index test. */
public class SparseJournalIndexTest {

  @Test
  public void shouldNotFindIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    final IndexInfo position = index.lookup(1);

    // then
    assertNull(position);
  }

  public static JournalRecord asJournalRecord(final long index, final long asqn) {
    return new TestJournalRecord(index, asqn, 0, null);
  }

  @Test
  public void shouldFindIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    index.index(asJournalRecord(1, 1), 2);
    index.index(asJournalRecord(2, 2), 4);
    index.index(asJournalRecord(3, 3), 6);
    index.index(asJournalRecord(4, 4), 8);
    index.index(asJournalRecord(5, 5), 10);

    // then
    assertEquals(5, index.lookup(5).index());
    assertEquals(10, index.lookup(5).position());
  }

  @Test
  public void shouldFindLowerIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 1), 2);
    index.index(asJournalRecord(2, 2), 4);
    index.index(asJournalRecord(3, 3), 6);
    index.index(asJournalRecord(4, 4), 8);
    index.index(asJournalRecord(5, 5), 10);

    // when
    index.index(asJournalRecord(6, 6), 12);
    index.index(asJournalRecord(7, 7), 14);
    index.index(asJournalRecord(8, 8), 16);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
  }

  @Test
  public void shouldFindNextIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 1), 2);
    index.index(asJournalRecord(2, 2), 4);
    index.index(asJournalRecord(3, 3), 6);
    index.index(asJournalRecord(4, 4), 8);
    index.index(asJournalRecord(5, 5), 10);
    index.index(asJournalRecord(6, 6), 12);
    index.index(asJournalRecord(7, 7), 14);
    index.index(asJournalRecord(8, 8), 16);

    // when
    index.index(asJournalRecord(9, 9), 18);
    index.index(asJournalRecord(10, 10), 20);

    // then
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldTruncateIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 10), 2);
    index.index(asJournalRecord(2, 20), 4);
    index.index(asJournalRecord(3, 30), 6);
    index.index(asJournalRecord(4, 40), 8);
    index.index(asJournalRecord(5, 50), 10);
    index.index(asJournalRecord(6, 60), 12);
    index.index(asJournalRecord(7, 70), 14);
    index.index(asJournalRecord(8, 80), 16);
    index.index(asJournalRecord(9, 90), 18);
    index.index(asJournalRecord(10, 100), 20);

    // when
    index.deleteAfter(8);

    // then
    assertEquals(5, index.lookup(8).index());
    assertEquals(10, index.lookup(8).position());
    assertEquals(5, index.lookup(10).index());
    assertEquals(10, index.lookup(10).position());
    assertEquals(5, index.lookupAsqn(80));
    assertEquals(5, index.lookupAsqn(90));
  }

  @Test
  public void shouldTruncateCompleteIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 10), 2);
    index.index(asJournalRecord(2, 20), 4);
    index.index(asJournalRecord(3, 30), 6);
    index.index(asJournalRecord(4, 40), 8);
    index.index(asJournalRecord(5, 50), 10);
    index.index(asJournalRecord(6, 60), 12);
    index.index(asJournalRecord(7, 70), 14);
    index.index(asJournalRecord(8, 80), 16);
    index.index(asJournalRecord(9, 90), 18);
    index.index(asJournalRecord(10, 100), 20);
    index.deleteAfter(8);

    // when
    index.deleteAfter(4);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));
    assertNull(index.lookup(10));
    assertNull(index.lookupAsqn(40));
    assertNull(index.lookupAsqn(50));
    assertNull(index.lookupAsqn(80));
    assertNull(index.lookupAsqn(100));
  }

  @Test
  public void shouldNotCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 10), 2);
    index.index(asJournalRecord(2, 20), 4);
    index.index(asJournalRecord(3, 30), 6);
    index.index(asJournalRecord(4, 40), 8);
    index.index(asJournalRecord(5, 50), 10);
    index.index(asJournalRecord(6, 60), 12);
    index.index(asJournalRecord(7, 70), 14);
    index.index(asJournalRecord(8, 80), 16);
    index.index(asJournalRecord(9, 90), 18);
    index.index(asJournalRecord(10, 100), 20);

    // when
    index.deleteUntil(8);

    // then
    assertNull(index.lookup(8));
    assertEquals(10, index.lookup(10).index());
    assertEquals(20, index.lookup(10).position());
  }

  @Test
  public void shouldCompactIndex() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    // index entries
    index.index(asJournalRecord(1, 10), 2);
    index.index(asJournalRecord(2, 20), 4);
    index.index(asJournalRecord(3, 30), 6);
    index.index(asJournalRecord(4, 40), 8);
    index.index(asJournalRecord(5, 50), 10);
    index.index(asJournalRecord(6, 60), 12);
    index.index(asJournalRecord(7, 70), 14);
    index.index(asJournalRecord(8, 80), 16);
    index.index(asJournalRecord(9, 90), 18);
    index.index(asJournalRecord(10, 100), 20);
    // when
    index.deleteUntil(11);

    // then
    assertNull(index.lookup(4));
    assertNull(index.lookup(5));
    assertNull(index.lookup(8));
    assertNull(index.lookupAsqn(40));
    assertNull(index.lookupAsqn(50));
    assertNull(index.lookupAsqn(80));
  }
}
