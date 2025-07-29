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
package io.camunda.zeebe.journal;

import java.util.Iterator;

public interface JournalReader extends Iterator<JournalRecord>, AutoCloseable {

  /**
   * Seek to a record at the given index. if seek(index) return true, {@link JournalReader#next()}
   * should return a record at index.
   *
   * <p>If the journal is empty, it will return {@link Journal#getFirstIndex()}, but it should not
   * do anything.
   *
   * <p>If the index is less than {@link Journal#getFirstIndex()}, {@link JournalReader#next()}
   * should return a record at index {@link Journal#getFirstIndex()}.
   *
   * <p>If the index is greater than {@link Journal#getLastIndex()}, the read is positioned past the
   * end of the journal, such that {@link JournalReader#hasNext()} will return false. In this case,
   * the returned index would be {@code {@link Journal#getLastIndex()} + 1}.
   *
   * <p>Callers are expected to call {@link #hasNext()} after a seek, regardless of the result
   * returned.
   *
   * @param index the index to seek to.
   * @return the index of the next record to be read
   */
  long seek(long index);

  /**
   * Seek to the first index of the journal. The index returned is that of the record which would be
   * returned by calling {@link #next()}.
   *
   * <p>Equivalent to calling seek(journal.getFirstIndex()).
   *
   * <p>Callers are expected to call {@link #hasNext()} after a seek, regardless of the result
   * returned.
   *
   * <p>If the journal is empty, then the index returned is the {@link Journal#getFirstIndex()}.
   *
   * @return the first index of the journal
   */
  long seekToFirst();

  /**
   * Seek to the last index of the journal. The index returned is that of the record which would be
   * returned by calling {@link #next()}.
   *
   * <p>Equivalent to calling seek(journal.getLastIndex()).
   *
   * <p>Callers are expected to call {@link #hasNext()} after a seek, regardless of the result
   * returned.
   *
   * <p>If the journal is empty, then the index returned is the {@link Journal#getLastIndex()},
   * which may be lower than {@link Journal#getFirstIndex()}.
   *
   * @return the last index of the journal
   */
  long seekToLast();

  /**
   * Seek to a record with the highest ASQN less than or equal to the given {@code asqn}.
   *
   * <p>If there are no records with a lower or equal ASQN, then the reader will be positioned at
   * the beginning of the record. That is it behaves as if {@link JournalReader#seekToFirst()}. In
   * this case it's possible that the next record has an ASQN of {@code -1}, as not all records have
   * an ASQN assigned.
   *
   * <p>Callers are expected to call {@link #hasNext()} after a seek, regardless of the result
   * returned.
   *
   * <p>The index returned is that of a valid record, or if the journal is empty, {@link
   * Journal#getFirstIndex()}.
   *
   * @param asqn application sequence number to seek
   * @return the index of the record that will be returned by {@link #next()}
   */
  long seekToAsqn(long asqn);

  /**
   * Seek to a record with the highest ASQN less than or equal to the given {@code asqn}, with the
   * restriction that it seeks to a record with index less than or equal to the given
   * indexUpperBound.
   *
   * <p>Otherwise, the semantics is similar to {@code seekToAsqn(asqn)}
   *
   * @param asqn application sequence number to seek
   * @param indexUpperBound the index until which it seeks
   * @return the index of the record that will be returned by {@link #next()}
   */
  long seekToAsqn(long asqn, long indexUpperBound);

  /** Get the index of the next record to be read. */
  long getNextIndex();

  @Override
  void close();
}
