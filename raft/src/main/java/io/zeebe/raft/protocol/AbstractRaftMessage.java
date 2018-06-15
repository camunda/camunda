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
package io.zeebe.raft.protocol;

import io.zeebe.raft.MessageHeaderDecoder;
import io.zeebe.raft.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public abstract class AbstractRaftMessage implements BufferReader, BufferWriter
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    public boolean tryWrap(final DirectBuffer buffer, final int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);

        if (headerDecoder.version() != getVersion() || headerDecoder.schemaId() != getSchemaId() || headerDecoder.templateId() != getTemplateId())
        {
            return false;
        }

        wrap(buffer, offset, length);

        return true;
    }

    protected abstract int getVersion();

    protected abstract int getSchemaId();

    protected abstract int getTemplateId();

    protected int wrapVarData(final DirectBuffer buffer, final int offset, final DirectBuffer data, final int headerLength, final int length)
    {
        if (length > 0)
        {
            final int dataOffset = offset + headerLength;
            data.wrap(buffer, dataOffset, length);
        }
        return headerLength + length;
    }

}
