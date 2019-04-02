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

public interface LogStreamRecordWriter extends LogStreamWriter {

  void wrap(LogStream log);

  LogStreamRecordWriter keyNull();

  LogStreamRecordWriter key(long key);

  LogStreamRecordWriter sourceRecordPosition(long position);

  LogStreamRecordWriter producerId(int producerId);

  LogStreamRecordWriter metadata(DirectBuffer buffer, int offset, int length);

  LogStreamRecordWriter metadata(DirectBuffer buffer);

  LogStreamRecordWriter metadataWriter(BufferWriter writer);

  LogStreamRecordWriter value(DirectBuffer value, int valueOffset, int valueLength);

  LogStreamRecordWriter value(DirectBuffer value);

  LogStreamRecordWriter valueWriter(BufferWriter writer);

  void reset();
}
