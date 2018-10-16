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

public class DisabledLogStreamWriter implements LogStreamRecordWriter {

  @Override
  public void wrap(LogStream log) {}

  @Override
  public LogStreamRecordWriter positionAsKey() {
    return this;
  }

  @Override
  public LogStreamRecordWriter keyNull() {
    return this;
  }

  @Override
  public LogStreamRecordWriter key(long key) {
    return this;
  }

  @Override
  public LogStreamRecordWriter sourceRecordPosition(long position) {
    return this;
  }

  @Override
  public LogStreamRecordWriter producerId(int producerId) {
    return this;
  }

  @Override
  public LogStreamRecordWriter metadata(DirectBuffer buffer, int offset, int length) {
    return this;
  }

  @Override
  public LogStreamRecordWriter metadata(DirectBuffer buffer) {
    return this;
  }

  @Override
  public LogStreamRecordWriter metadataWriter(BufferWriter writer) {
    return this;
  }

  @Override
  public LogStreamRecordWriter value(DirectBuffer value, int valueOffset, int valueLength) {
    return this;
  }

  @Override
  public LogStreamRecordWriter value(DirectBuffer value) {
    return this;
  }

  @Override
  public LogStreamRecordWriter valueWriter(BufferWriter writer) {
    return this;
  }

  @Override
  public void reset() {}

  @Override
  public long tryWrite() {
    throw new RuntimeException("Cannot write event; Writing is disabled");
  }
}
