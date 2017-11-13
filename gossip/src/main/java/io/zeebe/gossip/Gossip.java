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
package io.zeebe.gossip;

import io.zeebe.transport.*;
import io.zeebe.util.actor.Actor;
import org.agrona.DirectBuffer;

public class Gossip implements Actor, ServerMessageHandler, ServerRequestHandler
{

    private final SocketAddress socketAddress;
    private final GossipConfiguration configuration;
    private final BufferingServerTransport serverTransport;
    private final ClientTransport clientTransport;

    public Gossip(final SocketAddress socketAddress, final GossipConfiguration configuration, final BufferingServerTransport serverTransport, final ClientTransport clientTransport)
    {
        this.socketAddress = socketAddress;
        this.configuration = configuration;
        this.serverTransport = serverTransport;
        this.clientTransport = clientTransport;
    }

    @Override
    public int doWork() throws Exception
    {
        // TODO: implement
        return 0;
    }

    @Override
    public boolean onMessage(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length)
    {
        // TODO: implement
        return false;
    }

    @Override
    public boolean onRequest(final ServerOutput output, final RemoteAddress remoteAddress, final DirectBuffer buffer, final int offset, final int length, final long requestId)
    {
        // TODO: implement
        return false;
    }
}
