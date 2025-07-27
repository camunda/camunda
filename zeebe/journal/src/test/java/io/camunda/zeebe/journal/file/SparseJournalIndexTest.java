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
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.util.TestJournalRecord;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** Sparse journal index test. */
class SparseJournalIndexTest {

  @Test
  void shouldNotFindIndexWhenNotReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    final IndexInfo position = index.lookup(1);

    // then
    assertThat(position).isNull();
  }

  public static JournalRecord asJournalRecord(final long index, final long asqn) {
    return new TestJournalRecord(index, asqn, 0, null, null);
  }

  @Test
  void shouldFindIndexWhenReachedDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);

    // when
    index.index(asJournalRecord(1, 1), 2);
    index.index(asJournalRecord(2, 2), 4);
    index.index(asJournalRecord(3, 3), 6);
    index.index(asJournalRecord(4, 4), 8);
    index.index(asJournalRecord(5, 5), 10);

    // then
    assertThat(index.lookup(5).index()).isEqualTo(5);
    assertThat(index.lookup(5).position()).isEqualTo(10);
    assertThat(index.lookupAsqn(5)).isEqualTo(5);
  }

  @Test
  void shouldFindLowerIndexWhenNotReachedDensity() {
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
    assertThat(index.lookup(8).index()).isEqualTo(5);
    assertThat(index.lookup(8).position()).isEqualTo(10);
    assertThat(index.lookupAsqn(8)).isEqualTo(5);
  }

  @Test
  void shouldFindNextIndexWhenReachedDensity() {
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
    assertThat(index.lookup(10).index()).isEqualTo(10);
    assertThat(index.lookup(10).position()).isEqualTo(20);
    assertThat(index.lookupAsqn(10)).isEqualTo(10);
  }

  @Test
  void shouldTruncateIndex() {
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
    assertThat(index.lookup(8).index()).isEqualTo(5);
    assertThat(index.lookup(8).position()).isEqualTo(10);
    assertThat(index.lookup(10).index()).isEqualTo(5);
    assertThat(index.lookup(10).position()).isEqualTo(10);
    assertThat(index.lookupAsqn(80)).isEqualTo(5);
    assertThat(index.lookupAsqn(90)).isEqualTo(5);
  }

  @Test
  void shouldTruncateCompleteIndex() {
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
    assertThat(index.lookup(4)).isNull();
    assertThat(index.lookup(5)).isNull();
    assertThat(index.lookup(8)).isNull();
    assertThat(index.lookup(10)).isNull();
    assertThat(index.lookupAsqn(40)).isNull();
    assertThat(index.lookupAsqn(50)).isNull();
    assertThat(index.lookupAsqn(80)).isNull();
    assertThat(index.lookupAsqn(100)).isNull();
  }

  @Test
  void shouldNotCompactIndex() {
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
    assertThat(index.lookup(8)).isNull();
    assertThat(index.lookup(10).index()).isEqualTo(10);
    assertThat(index.lookup(10).position()).isEqualTo(20);
  }

  @Test
  void shouldCompactIndex() {
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
    assertThat(index.lookup(4)).isNull();
    assertThat(index.lookup(5)).isNull();
    assertThat(index.lookup(8)).isNull();
    assertThat(index.lookupAsqn(40)).isNull();
    assertThat(index.lookupAsqn(50)).isNull();
    assertThat(index.lookupAsqn(80)).isNull();
  }

  @Test
  void shouldFindAsqnWithInBound() {
    // given - every 2nd index is added
    final JournalIndex index = new SparseJournalIndex(2);

    // when
    index.index(asJournalRecord(1, 1), 2);
    index.index(asJournalRecord(2, 2), 4);
    index.index(asJournalRecord(3, 3), 6);
    index.index(asJournalRecord(4, 4), 8);
    index.index(asJournalRecord(5, 5), 10);
    index.index(asJournalRecord(6, 6), 10);

    // then
    assertThat(index.lookupAsqn(5, 1)).isNull();
    assertThat(index.lookupAsqn(5, 3)).isEqualTo(2);
    assertThat(index.lookupAsqn(5, 3)).isEqualTo(2);
    assertThat(index.lookupAsqn(5, 4)).isEqualTo(4);
    assertThat(index.lookupAsqn(5, 5)).isEqualTo(4);
    assertThat(index.lookupAsqn(Long.MAX_VALUE, 5)).isEqualTo(4);
    assertThat(index.lookupAsqn(Long.MAX_VALUE, 6)).isEqualTo(6);
  }

  @Test
  void shouldReturnAsIndexedWhenWithInDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    index.index(asJournalRecord(5, 1), 2);

    // when - then
    assertThat(index.hasIndexed(6)).isTrue();
    assertThat(index.hasIndexed(7)).isTrue();
    assertThat(index.hasIndexed(8)).isTrue();
    assertThat(index.hasIndexed(9)).isTrue();
  }

  @Test
  void shouldReturnAsNotIndexedWhenOutsideDensity() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(5);
    index.index(asJournalRecord(5, 1), 2);

    // when - then
    assertThat(index.hasIndexed(10)).isFalse();
    assertThat(index.hasIndexed(11)).isFalse();
    assertThat(index.hasIndexed(100)).isFalse();
  }

  @Test
  void shouldDeleteInRange() {
    // given - every 5 index is added
    final JournalIndex index = new SparseJournalIndex(1);
    // index entries
    LongStream.rangeClosed(1, 10).forEach(i -> index.index(asJournalRecord(i, i), (int) (i * 2)));

    // when
    index.deleteInRange(3, 7);

    // then all lookups in range 3-7 should return the 2nd entry as it's the floor entry of the
    // journal index
    IntStream.rangeClosed(3, 7)
        .forEach(
            i -> {
              final var info = index.lookup(i);
              assertThat(info).isNotNull();
              assertThat(info.index()).isEqualTo(2);
              assertThat(info.position()).isEqualTo(4);
              assertThat(index.lookupAsqn(i)).isEqualTo(2);
            });

    IntStream.rangeClosed(1, 2)
        .forEach(
            i -> {
              assertThat(index.lookup(i).index()).isEqualTo(i);
              assertThat(index.lookup(i).position()).isEqualTo(i * 2);
              assertThat(index.lookupAsqn(i)).isEqualTo(i);
            });

    IntStream.rangeClosed(8, 10)
        .forEach(
            i -> {
              assertThat(index.lookup(i).index()).isEqualTo(i);
              assertThat(index.lookup(i).position()).isEqualTo(i * 2);
              assertThat(index.lookupAsqn(i)).isEqualTo(i);
            });
  }
}
