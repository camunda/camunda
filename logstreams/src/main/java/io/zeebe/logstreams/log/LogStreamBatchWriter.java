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
package io.zeebe.logstreams.log;

import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Write log entries to the log stream write buffer as batch. This ensures that the log entries are
 * written atomically.
 *
 * <p>Note that the log entry data is buffered until {@link #tryWrite()} is called.
 */
public interface LogStreamBatchWriter extends LogStreamWriter {
  /** Builder to add a log entry to the batch. */
  interface LogEntryBuilder {
    /** Use the default values as key. */
    LogEntryBuilder keyNull();

    /** Set the log entry key. */
    LogEntryBuilder key(long key);

    /** Set the log entry metadata. */
    LogEntryBuilder metadata(DirectBuffer buffer, int offset, int length);

    /** Set the log entry metadata. */
    LogEntryBuilder metadata(DirectBuffer buffer);

    /** Set the log entry metadata. */
    LogEntryBuilder metadataWriter(BufferWriter writer);

    /** Set the log entry value. */
    LogEntryBuilder value(DirectBuffer value, int valueOffset, int valueLength);

    /** Set the log entry value. */
    LogEntryBuilder value(DirectBuffer value);

    /** Set the log entry value. */
    LogEntryBuilder valueWriter(BufferWriter writer);

    /** Add the log entry to the batch. */
    LogStreamBatchWriter done();
  }

  /** Initialize the write for the given log stream. */
  void wrap(LogStream log);

  /** Set the source event for all log entries. */
  LogStreamBatchWriter sourceRecordPosition(long position);

  /** Set the producer id for all log entries. */
  LogStreamBatchWriter producerId(int producerId);

  /** Returns the builder to add a new log entry to the batch. */
  LogEntryBuilder event();

  /** Discard all non-written batch data. */
  void reset();
}
