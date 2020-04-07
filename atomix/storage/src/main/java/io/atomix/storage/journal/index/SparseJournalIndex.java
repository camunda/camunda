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

import io.atomix.storage.journal.Indexed;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/** Sparse index. */
public class SparseJournalIndex implements JournalIndex {

  private final int density;
  private final TreeMap<Long, Integer> positions = new TreeMap<>();

  public SparseJournalIndex(final int density) {
    this.density = density;
  }

  @Override
  public void index(final Indexed indexedEntry, final int position) {
    final long index = indexedEntry.index();
    if (index % density == 0) {
      positions.put(index, position);
    }
  }

  @Override
  public Position lookup(final long index) {
    final Map.Entry<Long, Integer> entry = positions.floorEntry(index);
    return entry != null ? new Position(entry.getKey(), entry.getValue()) : null;
  }

  @Override
  public void truncate(final long index) {
    positions.tailMap(index, false).clear();
  }

  @Override
  public void compact(final long index) {
    final Entry<Long, Integer> floorEntry = positions.floorEntry(index);

    if (floorEntry != null) {
      positions.headMap(floorEntry.getKey(), false).clear();
    }
  }
}
