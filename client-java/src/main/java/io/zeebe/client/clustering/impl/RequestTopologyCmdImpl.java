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
package io.zeebe.client.clustering.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.cmd.AbstractControlMessageCmd;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

public class RequestTopologyCmdImpl extends AbstractControlMessageCmd<TopologyResponse, TopologyResponse> implements RequestTopologyCmd
{
    public RequestTopologyCmdImpl(ClientCommandManager commandManager, final ObjectMapper objectMapper)
    {
        super(commandManager, objectMapper, null, TopologyResponse.class, ControlMessageType.REQUEST_TOPOLOGY);
    }

    @Override
    protected Object writeCommand()
    {
        return null;
    }

    @Override
    protected void reset()
    {
    }

    @Override
    protected TopologyResponse getResponseValue(final TopologyResponse topology)
    {
        return topology;
    }

    @Override
    protected void validate()
    {

    }

    public BufferWriter getRequestWriter()
    {
        final ExpandableArrayBuffer writeBuffer = new ExpandableArrayBuffer();

        writeCommand(writeBuffer);

        return new BufferWriter()
        {
            @Override
            public void write(MutableDirectBuffer buffer, int offset)
            {
                buffer.putBytes(offset, writeBuffer, 0, writeBuffer.capacity());
            }

            @Override
            public int getLength()
            {
                return writeBuffer.capacity();
            }
        };
    }
}
