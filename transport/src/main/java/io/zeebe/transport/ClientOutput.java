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

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.ClientRequestPool;
import io.zeebe.util.buffer.BufferWriter;

public class ClientOutput
{
    protected final Dispatcher sendBuffer;
    protected final ClientRequestPool requestPool;

    public ClientOutput(Dispatcher sendBuffer, ClientRequestPool requestPool)
    {
        this.sendBuffer = sendBuffer;
        this.requestPool = requestPool;
    }

    public boolean sendMessage(TransportMessage transportMessage)
    {
        return transportMessage.trySend(sendBuffer);
    }

    /**
     * Returns null if request cannot be currently written due to exhausted capacity.
     * Throws an exception if the request is not sendable at all (e.g. buffer writer throws exception).
     */
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        return requestPool.open(addr, writer);
    }

}
