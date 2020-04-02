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

/** Journal index. */
public interface JournalIndex {

  /**
   * Adds an entry for the given index at the given position.
   *
   * @param indexed the indexed entry for which to add the entry
   * @param position the position of the given index
   */
  void index(Indexed indexed, int position);

  /**
   * Looks up the position of the given index.
   *
   * @param index the index to lookup
   * @return the position of the given index or a lesser index
   */
  Position lookup(long index);

  /**
   * Truncates the index to the given index, which means everything higher will be removed from the
   * index
   *
   * @param index the index to which to truncate the index
   */
  void truncate(long index);

  /**
   * Compacts the index until the next stored index (exclusively), which means everything lower then
   * the stored index will be removed.
   *
   * <p>Example Index: {5 -> 10; 10 -> 20; 15 -> 30}, when compact is called with index 11. The next
   * lower stored index is 10, everything lower then this index will be removed. This means the
   * mapping {5 -> 10}, should be removed.
   *
   * @param index the index to which to compact the index
   */
  void compact(long index);
}
