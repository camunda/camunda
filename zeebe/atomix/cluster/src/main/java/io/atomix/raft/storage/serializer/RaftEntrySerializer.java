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
package io.atomix.raft.storage.serializer;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface RaftEntrySerializer {

  /**
   * Determines the length in bytes of a serialized application entry.
   *
   * @param entry to determine the length in bytes for
   * @return the length in bytes when the entry gets serialized
   */
  int getApplicationEntrySerializedLength(ApplicationEntry entry);

  /**
   * Determines the length in bytes of a serialized initial entry.
   *
   * @return the length in bytes of a serialized initial entry
   */
  int getInitialEntrySerializedLength();

  /**
   * Determines the length in bytes of a serialized configuration entry.
   *
   * @param entry to determine the length in bytes for
   * @return the length in bytes when the entry gets serialized
   */
  int getConfigurationEntrySerializedLength(ConfigurationEntry entry);

  /**
   * Writes the term and entry into given buffer at the given offset
   *
   * @param term the term of the entry
   * @param entry the ApplicationEntry to write
   * @param buffer the buffer to write to
   * @param offset the offset in the buffer at which the term and entry will be written
   * @return the number of bytes written
   */
  int writeApplicationEntry(
      long term, ApplicationEntry entry, MutableDirectBuffer buffer, int offset);

  /**
   * Writes the term and entry into given buffer at the given offset
   *
   * @param term the term of the entry
   * @param entry the InitialEntry to write
   * @param buffer the buffer to write to
   * @param offset the offset in the buffer at which the term and entry will be written
   * @return the number of bytes written
   */
  int writeInitialEntry(long term, InitialEntry entry, MutableDirectBuffer buffer, int offset);

  /**
   * Writes the term and entry into given buffer at the given offset
   *
   * @param term the term of the entry
   * @param entry the ConfigurationEntry to write
   * @param buffer the buffer to write to
   * @param offset the offset in the buffer at which the term and entry will be written
   * @return the number of bytes written
   */
  int writeConfigurationEntry(
      long term, ConfigurationEntry entry, MutableDirectBuffer buffer, int offset);

  /**
   * Read the raft log entry from the buffer
   *
   * @param buffer to read the raft log entry from
   * @return RaftLogEntry
   */
  RaftLogEntry readRaftLogEntry(DirectBuffer buffer);

  /** A buffer writer based on serializer logic provided as functions. */
  record SerializedBufferWriterAdapter(
      Supplier<Integer> getRecordLengthFunction,
      BiFunction<MutableDirectBuffer, Integer, Integer> writeFunction)
      implements BufferWriter {

    @Override
    public int getLength() {
      return getRecordLengthFunction.get();
    }

    @Override
    public int write(final MutableDirectBuffer writeBuffer, final int offset) {
      return writeFunction.apply(writeBuffer, offset);
    }
  }
}
