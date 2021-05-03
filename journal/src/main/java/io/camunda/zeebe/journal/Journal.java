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
package io.zeebe.journal;

import io.zeebe.journal.JournalException.InvalidChecksum;
import io.zeebe.journal.JournalException.InvalidIndex;
import org.agrona.DirectBuffer;

public interface Journal extends AutoCloseable {

  /**
   * Appends a new {@link JournalRecord} that contains the given data. asqn refers to Application
   * Sequence Number. It is a sequence number provided by the application. The given asqn must be
   * positive and, it must be greater than the asqn of the previous record.
   *
   * @param asqn A sequence number provided by the application.
   * @param data The data to be appended
   * @return the journal record that was appended
   */
  JournalRecord append(long asqn, DirectBuffer data);

  /**
   * Appends a new {@link JournalRecord} that contains the given data. The asqn of this record could
   * not be used to seek. Use this for records that do not have a specific applicationSqNum.
   * Examples for such record is raft record that indicates a leader change.
   *
   * @param data The data to be appended
   * @return the journal record that was appended
   */
  JournalRecord append(DirectBuffer data);

  /**
   * Appends a {@link JournalRecord}. If the index of the record is not the next expected index, the
   * append will fail.
   *
   * @param record the record to be appended
   * @exception InvalidIndex if the index of record is not the next expected index
   * @exception InvalidChecksum if the checksum in record does not match the checksum of the data
   */
  void append(JournalRecord record);

  /**
   * Delete all records after indexExclusive. After a call to this method, {@link
   * Journal#getLastIndex()} should return indexExclusive.
   *
   * @param indexExclusive the index after which the records will be deleted.
   */
  void deleteAfter(long indexExclusive);

  /**
   * Attempts to delete all records until indexExclusive. The records may be immediately deleted or
   * marked to be deleted later depending on the implementation.
   *
   * @param indexExclusive the index until which will be deleted. The record at this index is not
   *     deleted.
   */
  void deleteUntil(long indexExclusive);

  /**
   * Delete all records in the journal and reset the next index to nextIndex. The following calls to
   * {@link Journal#append(long, DirectBuffer)} will append at index nextIndex.
   *
   * @param nextIndex the next index of the journal.
   */
  void reset(long nextIndex);

  /**
   * Returns the index of last record in the journal
   *
   * @return the last index
   */
  long getLastIndex();

  /**
   * Returns the index of the first record.
   *
   * @return the first index
   */
  long getFirstIndex();

  /**
   * Check if the journal is empty.
   *
   * @return true if empty, false otherwise.
   */
  boolean isEmpty();

  /**
   * Depending on the implementation, appends to the journal may not be immediately flushed to the
   * persistent storage. A call to this method guarantees that all records written are safely
   * flushed to the persistent storage.
   */
  void flush();

  /**
   * Opens a new {@link JournalReader}
   *
   * @return a journal reader
   */
  JournalReader openReader();

  /**
   * Check if the journal is open
   *
   * @return true if open, false otherwise
   */
  boolean isOpen();
}
