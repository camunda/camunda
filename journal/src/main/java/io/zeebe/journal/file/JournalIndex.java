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

/**
 * JournalIndex that indexes record's index, position and asqn. JournalReader may use this to
 * optimize seek.
 */
interface JournalIndex {

  /**
   * Indexes the record and its position with in a segment
   *
   * @param record the record that should be indexed
   * @param position the position of the given index
   */
  void index(JournalRecord record, int position);

  /**
   * Looks up the position of the given index.
   *
   * @param index the index to lookup
   * @return the position of the given index or a lesser index
   */
  IndexInfo lookup(long index);

  /**
   * Look up the index for the given application sequence number.
   *
   * @param asqn asqn to lookup
   * @return the index of a record with asqn less than or equal to the given asqn.
   */
  Long lookupAsqn(long asqn);

  /**
   * Look up the index for the given application sequence number. Same as {code lookupAsqn(asqn)},
   * but the returned index will be less than or equal to the given indexUpperBound.
   *
   * @param asqn asqn to lookup
   * @param indexUpperBound the upper bound of the index that will be returned
   * @return the index (<= indexUpperBound) of a record with asqn less than or equal to the given
   *     asqn.
   */
  Long lookupAsqn(long asqn, long indexUpperBound);

  /**
   * Delete all entries after the given index.
   *
   * @param indexExclusive the index after which to be deleted
   */
  void deleteAfter(long indexExclusive);

  /**
   * Compacts the index until the next stored index (exclusively), which means everything lower then
   * the stored index will be removed.
   *
   * <p>Example Index: {5 -> 10; 10 -> 20; 15 -> 30}, when compact is called with index 11. The next
   * lower stored index is 15, everything lower then this index will be removed.
   *
   * @param indexExclusive the index to which to compact the index
   */
  void deleteUntil(long indexExclusive);

  /** Delete all index mappings */
  void clear();
}
