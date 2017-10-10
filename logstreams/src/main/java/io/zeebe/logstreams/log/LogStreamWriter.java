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

import org.agrona.DirectBuffer;
import io.zeebe.util.buffer.BufferWriter;

public interface LogStreamWriter
{

    void wrap(LogStream log);

    LogStreamWriter positionAsKey();

    LogStreamWriter key(long key);

    LogStreamWriter raftTermId(int termId);

    LogStreamWriter sourceEvent(int logStreamPartitionId, long position);

    LogStreamWriter producerId(int producerId);

    LogStreamWriter metadata(DirectBuffer buffer, int offset, int length);

    LogStreamWriter metadata(DirectBuffer buffer);

    LogStreamWriter metadataWriter(BufferWriter writer);

    LogStreamWriter value(DirectBuffer value, int valueOffset, int valueLength);

    LogStreamWriter value(DirectBuffer value);

    LogStreamWriter valueWriter(BufferWriter writer);

    void reset();

    /**
     * Attempts to write the event to the underlying stream.
     *
     * @return the event position or a negative value if fails to write the
     *         event
     */
    long tryWrite();

}
