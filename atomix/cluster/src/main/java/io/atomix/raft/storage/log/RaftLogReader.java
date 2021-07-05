/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.storage.log;

public interface RaftLogReader extends java.util.Iterator<IndexedRaftLogEntry>, AutoCloseable {

  /**
   * Reset the reader to the first index of the log.
   *
   * @return return the first index
   */
  long reset();

  /**
   * Seeks to the given index.
   *
   * @param index the index to seeks to
   * @return the index of the next record to be read
   */
  long seek(long index);

  /**
   * Seeks to the last index
   *
   * @return the index of the next record to be read
   */
  long seekToLast();

  /**
   * Seek to a record with the highest ASQN less than or equal to the given asqn.
   *
   * @param asqn the asqn to seek to
   * @return the index of the record that will be returned by {@link #next()}
   */
  long seekToAsqn(final long asqn);

  @Override
  void close();
}
