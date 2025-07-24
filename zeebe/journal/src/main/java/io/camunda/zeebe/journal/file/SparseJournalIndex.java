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
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalRecord;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

final class SparseJournalIndex implements JournalIndex {

  private final int density;
  private final ConcurrentNavigableMap<Long, Integer> indexToPosition =
      new ConcurrentSkipListMap<>();
  private final ConcurrentNavigableMap<Long, Long> asqnToIndex = new ConcurrentSkipListMap<>();
  // This is added to make deleteAfter and deleteUntil easier.
  // TODO: Check if this can be improved. https://github.com/zeebe-io/zeebe/issues/6220
  private final ConcurrentNavigableMap<Long, Long> indexToAsqn = new ConcurrentSkipListMap<>();

  SparseJournalIndex(final int density) {
    this.density = density;
  }

  @Override
  public void index(final JournalRecord indexedEntry, final int position) {
    final long index = indexedEntry.index();
    if (index % density == 0) {
      indexToPosition.put(index, position);
      final long asqn = indexedEntry.asqn();
      if (asqn != SegmentedJournal.ASQN_IGNORE) {
        asqnToIndex.put(asqn, index);
        indexToAsqn.put(index, asqn);
      }
    }
  }

  @Override
  public IndexInfo lookup(final long index) {
    final Map.Entry<Long, Integer> entry = indexToPosition.floorEntry(index);
    return entry != null ? new IndexInfo(entry.getKey(), entry.getValue()) : null;
  }

  @Override
  public Long lookupAsqn(final long asqn) {
    return lookupAsqn(asqn, Long.MAX_VALUE);
  }

  @Override
  public Long lookupAsqn(final long asqn, final long indexUpperBound) {
    final Map.Entry<Long, Long> entry = asqnToIndex.floorEntry(asqn);
    if (entry != null) {
      if (entry.getValue() <= indexUpperBound) {
        return entry.getValue();
      } else {
        return indexToAsqn.floorKey(indexUpperBound);
      }
    }
    return null;
  }

  @Override
  public void deleteAfter(final long index) {
    indexToPosition.tailMap(index, false).clear();
    final var asqnEntryToDelete = indexToAsqn.ceilingEntry(index);
    if (asqnEntryToDelete != null) {
      final var asqnToDelete = asqnEntryToDelete.getValue();
      indexToAsqn.tailMap(index, false).clear();
      final boolean include = asqnEntryToDelete.getKey() > index;
      asqnToIndex.tailMap(asqnToDelete, include).clear();
    }
  }

  @Override
  public void deleteInRange(final long fromIndexInclusive, final long toIndexInclusive) {
    indexToPosition.subMap(fromIndexInclusive, true, toIndexInclusive, true).clear();
    final var indexToAsqnSubMap =
        indexToAsqn.subMap(fromIndexInclusive, true, toIndexInclusive, true);
    asqnToIndex
        .subMap(
            indexToAsqnSubMap.firstEntry().getValue(),
            true,
            indexToAsqnSubMap.lastEntry().getValue(),
            true)
        .clear();
    indexToAsqnSubMap.clear();
  }

  @Override
  public void deleteUntil(final long index) {
    indexToPosition.headMap(index, false).clear();

    final var asqnEntryToDelete = indexToAsqn.floorEntry(index);
    if (asqnEntryToDelete != null) {
      final var asqnToDelete = asqnEntryToDelete.getValue();
      indexToAsqn.headMap(index, false).clear();
      asqnToIndex.headMap(asqnToDelete, false).clear();
    }
  }

  @Override
  public void clear() {
    indexToPosition.clear();
    indexToAsqn.clear();
    asqnToIndex.clear();
  }

  @Override
  public boolean hasIndexed(final long index) {
    final var indexInfo = lookup(index);
    if (indexInfo == null) {
      return false;
    } else {
      return indexInfo.index() > index - density;
    }
  }
}
