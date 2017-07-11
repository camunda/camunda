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

public class DisabledLogStreamWriter implements LogStreamWriter
{

    @Override
    public void wrap(LogStream log)
    {
    }

    @Override
    public LogStreamWriter positionAsKey()
    {
        return this;
    }

    @Override
    public LogStreamWriter key(long key)
    {
        return this;
    }

    @Override
    public LogStreamWriter sourceEvent(final DirectBuffer logStreamTopicName, int logStreamPartitionId, long position)
    {
        return this;
    }

    @Override
    public LogStreamWriter producerId(int producerId)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer, int offset, int length)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadataWriter(BufferWriter writer)
    {
        return this;
    }

    @Override
    public LogStreamWriter value(DirectBuffer value, int valueOffset, int valueLength)
    {
        return this;
    }

    @Override
    public LogStreamWriter value(DirectBuffer value)
    {
        return this;
    }

    @Override
    public LogStreamWriter valueWriter(BufferWriter writer)
    {
        return this;
    }

    @Override
    public void reset()
    {
    }

    @Override
    public long tryWrite()
    {
        throw new RuntimeException("Cannot write event; Writing is disabled");
    }

}
