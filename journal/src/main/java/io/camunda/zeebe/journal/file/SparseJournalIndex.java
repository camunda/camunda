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
package io.zeebe.journal.file;

import io.zeebe.journal.JournalRecord;
import java.util.Map;
import java.util.TreeMap;

class SparseJournalIndex implements JournalIndex {

  private final int density;
  private final TreeMap<Long, Integer> indexToPosition = new TreeMap<>();
  private final TreeMap<Long, Long> asqnToIndex = new TreeMap<>();
  // This is added to make deleteAfter and deleteUntil easier.
  // TODO: Check if this can be improved. https://github.com/zeebe-io/zeebe/issues/6220
  private final TreeMap<Long, Long> indexToAsqn = new TreeMap<>();

  public SparseJournalIndex(final int density) {
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
}
