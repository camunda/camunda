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
package io.zeebe.raft;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import org.agrona.MutableDirectBuffer;

public class IncomingRaftRequest
{
    private ServerOutput output;
    private RemoteAddress remoteAddress;
    private long requestId;
    private MutableDirectBuffer requestData;

    public IncomingRaftRequest(ServerOutput output,
        RemoteAddress remoteAddress,
        long requestId,
        MutableDirectBuffer requestData)
    {
        this.output = output;
        this.remoteAddress = remoteAddress;
        this.requestId = requestId;
        this.requestData = requestData;
    }

    public ServerOutput getOutput()
    {
        return output;
    }

    public RemoteAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public long getRequestId()
    {
        return requestId;
    }

    public MutableDirectBuffer getRequestData()
    {
        return requestData;
    }
}
