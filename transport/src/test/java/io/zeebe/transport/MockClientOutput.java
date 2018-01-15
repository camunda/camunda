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
package io.zeebe.transport;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.util.buffer.BufferWriter;

public class MockClientOutput implements ClientOutput
{

    protected final Queue<ClientRequest> requestsToReturn;
    protected final List<DirectBuffer> sentRequests = new ArrayList<>();

    public MockClientOutput()
    {
        this.requestsToReturn = new LinkedList<>();
    }

    public void addStubRequests(ClientRequest... requestsToReturn)
    {
        for (ClientRequest request : requestsToReturn)
        {
            this.requestsToReturn.add(request);
        }
    }

    @Override
    public boolean sendMessage(TransportMessage transportMessage)
    {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        final ClientRequest clientRequest = requestsToReturn.remove();

        if (clientRequest != null)
        {
            final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
            writer.write(buffer, 0);
            sentRequests.add(buffer);
        }

        return clientRequest;
    }

    @Override
    public ClientRequest sendRequestWithRetry(RemoteAddress addr, BufferWriter writer, long timeout)
    {
        return sendRequest(addr, writer);
    }

    @Override
    public ClientRequest sendRequestWithRetry(RemoteAddress addr, BufferWriter writer)
    {
        return sendRequestWithRetry(addr, writer, -1);
    }
}
