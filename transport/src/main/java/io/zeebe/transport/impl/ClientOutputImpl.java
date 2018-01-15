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
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferWriter;

public class ClientOutputImpl implements ClientOutput
{
    protected final Dispatcher sendBuffer;
    protected final ClientRequestPool requestPool;
    protected final RequestManager requestManager;
    protected final long defaultRequestRetryTimeout;

    public ClientOutputImpl(Dispatcher sendBuffer,
            ClientRequestPool requestPool,
            RequestManager requestManager,
            long defaultRequestRetryTimeout)
    {
        this.sendBuffer = sendBuffer;
        this.requestPool = requestPool;
        this.requestManager = requestManager;
        this.defaultRequestRetryTimeout = defaultRequestRetryTimeout;
    }

    @Override
    public boolean sendMessage(TransportMessage transportMessage)
    {
        return transportMessage.trySend(sendBuffer);
    }

    @Override
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        return requestPool.openRequest(addr, writer);
    }

    @Override
    public ClientRequest sendRequestWithRetry(RemoteAddress addr, BufferWriter writer, long timeout)
    {
        ensureRequestManagerEnabled();
        return requestManager.openRequest(addr, writer, timeout);
    }

    @Override
    public ClientRequest sendRequestWithRetry(RemoteAddress addr, BufferWriter writer)
    {
        return sendRequestWithRetry(addr, writer, defaultRequestRetryTimeout);
    }

    private void ensureRequestManagerEnabled()
    {
        if (requestManager == null)
        {
            throw new UnsupportedOperationException("Managed requests are disabled. Must be enabled on the client transport builder");
        }
    }

}
