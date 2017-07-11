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

import org.agrona.BitUtil;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferWriter;

public class ClientRequestPool implements AutoCloseable
{
    private final int capacity;
    private final ManyToManyConcurrentArrayQueue<ClientRequestImpl> availableRequests;
    private ClientRequestImpl[] requests;

    public ClientRequestPool(int requestedCapacity, Dispatcher sendBuffer)
    {
        capacity = BitUtil.findNextPositivePowerOfTwo(requestedCapacity);

        availableRequests = new ManyToManyConcurrentArrayQueue<>(capacity);
        requests = new ClientRequestImpl[capacity];

        for (int i = 0; i < capacity; i++)
        {
            final ClientRequestImpl request = new ClientRequestImpl(new RequestIdGenerator(i, capacity), sendBuffer, this::onRequestClose);
            requests[i] = request;
            availableRequests.add(request);
        }
    }

    public ClientRequestImpl open(RemoteAddress remoteAddress, BufferWriter writer)
    {

        final ClientRequestImpl request = availableRequests.poll();

        if (request != null)
        {
            boolean requestOpened = false;

            try
            {
                requestOpened = request.open(remoteAddress, writer);
            }
            catch (Exception e)
            {
                throw e;
            }
            finally
            {
                if (!requestOpened)
                {
                    availableRequests.add(request);
                    return null;
                }
            }
        }

        return request;
    }

    public ClientRequestImpl getOpenRequestById(long id)
    {
        ClientRequestImpl result = null;

        final int offset = (int) (id & (capacity - 1));
        final ClientRequestImpl request = requests[offset];

        if (request.getRequestId() == id)
        {
            result = request;
        }

        return result;
    }

    public void failPendingRequestsToRemote(RemoteAddress remote, Exception e)
    {
        for (int i = 0; i < requests.length; i++)
        {
            final ClientRequestImpl request = requests[i];
            if (request.isAwaitingResponse() && remote.equals(request.getRemoteAddress()))
            {
                request.fail(e);
            }
        }
    }

    @Override
    public void close()
    {
        for (int i = 0; i < requests.length; i++)
        {
            final ClientRequestImpl clientRequestImpl = requests[i];
            try
            {
                clientRequestImpl.close();
            }
            catch (Exception e)
            {
                // ignore
                e.printStackTrace();
            }
        }
    }

    public void onRequestClose(ClientRequestImpl requestImpl)
    {
        availableRequests.add(requestImpl);
    }

    public static class RequestIdGenerator
    {
        private final int poolCapacity;
        private long lastId;

        RequestIdGenerator(int offset, int poolCapacity)
        {
            this.poolCapacity = poolCapacity;
            this.lastId = offset;
        }

        public long getNextRequestId()
        {
            lastId += poolCapacity;
            return lastId;
        }
    }
}
