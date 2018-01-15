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

import java.util.ArrayList;

import org.agrona.collections.ArrayListUtil;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.buffer.BufferWriter;

public class RequestManager implements Actor
{
    protected final ArrayList<ManagedClientRequestImpl> activeRequests = new ArrayList<>();
    protected final ClientRequestPool requestPool;

    public RequestManager(ClientRequestPool requestPool)
    {
        this.requestPool = requestPool;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;
        for (int i = 0; i < activeRequests.size(); i++)
        {
            final ManagedClientRequestImpl requestImpl = activeRequests.get(i);
            workCount += requestImpl.doWork();

            if (requestImpl.isClosed())
            {
                ArrayListUtil.fastUnorderedRemove(activeRequests, i);
                i = i - 1; // continue with the replacing element
            }
        }

        return workCount;
    }

    public ManagedClientRequestImpl openRequest(RemoteAddress addr, BufferWriter writer, long timeout)
    {

        ManagedClientRequestImpl resilientRequest = null;
        final ClientRequestImpl request = requestPool.poll(addr);

        if (request != null)
        {
            try
            {
                resilientRequest = new ManagedClientRequestImpl(request, addr, writer, timeout);
            }
            finally
            {
                if (resilientRequest == null)
                {
                    request.close();
                }
                else
                {
                    activeRequests.add(resilientRequest);
                }
            }
        }

        return resilientRequest;
    }

}
