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
package io.zeebe.gossip.protocol;

import io.zeebe.transport.ClientRequest;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;

public class GossipEventResponse
{
    private final GossipEvent event;

    private ClientRequest request;
    private long requestId;
    private long timeout;

    public GossipEventResponse(GossipEvent event)
    {
        this.event = event;
    }

    public void wrap(ClientRequest request)
    {
        this.request = request;
        this.requestId = request.getRequestId();
    }

    public void wrap(ClientRequest request, long durationInMillis)
    {
        this.request = request;
        this.requestId = request.getRequestId();
        this.timeout = ClockUtil.getCurrentTimeInMillis() + durationInMillis;
    }

    public boolean isReceived()
    {
        return request.isDone() && !request.isFailed() && request.getRequestId() > 0 && request.getRequestId() == requestId;
    }

    public boolean isFailed()
    {
        return request.isFailed() || request.getRequestId() < 0 || request.getRequestId() != requestId;
    }

    public boolean isTimedOut()
    {
        return timeout > 0 ? ClockUtil.getCurrentTimeInMillis() >= timeout : false;
    }

    public void process()
    {
        if (isReceived())
        {
            final DirectBuffer response = request.join();

            event.wrap(response, 0, response.capacity());

            clear();
        }
        else
        {
            throw new IllegalStateException("Response isn't received yet.");
        }
    }

    public void clear()
    {
        if (request != null)
        {
            request.close();
        }
        this.request = null;
        this.timeout = -1L;
    }

}
