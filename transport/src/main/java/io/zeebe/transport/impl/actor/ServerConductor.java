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
package io.zeebe.transport.impl.actor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.AcceptTransportPoller;
import org.agrona.nio.TransportPoller;

public class ServerConductor extends Conductor
{
    private final AcceptTransportPoller acceptTransportPoller;
    private final TransportPoller[] closableTransportPoller;

    public ServerConductor(ServerActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
        acceptTransportPoller = new AcceptTransportPoller(actorContext);
        acceptTransportPoller.addServerSocketBinding(context.getServerSocketBinding());
        closableTransportPoller = new TransportPoller[]{acceptTransportPoller};
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = super.doWork();

        workCount += acceptTransportPoller.pollNow();

        return workCount;
    }

    public void onServerChannelOpened(SocketChannel serverChannel)
    {
        deferred.runAsync(() ->
        {
            SocketAddress socketAddress = null;

            try
            {
                socketAddress = new SocketAddress((InetSocketAddress) serverChannel.getRemoteAddress());
            }
            catch (IOException e)
            {
                try
                {
                    serverChannel.close();
                }
                catch (IOException e1)
                {
                    return;
                }
            }

            RemoteAddress remoteAddress = remoteAddressList.getByAddress(socketAddress);

            if (remoteAddress != null)
            {
                // make sure to generate a new stream id
                remoteAddressList.retire(remoteAddress);
            }

            remoteAddress = remoteAddressList.register(socketAddress);

            final TransportChannel ch = channelFactory.buildServerChannel(
                this,
                remoteAddress,
                transportContext.getMessageMaxLength(),
                transportContext.getReceiveHandler(),
                serverChannel);

            onChannelConnected(ch);
        });
    }

    @Override
    protected TransportPoller[] getClosableTransportPoller()
    {
        return closableTransportPoller;
    }
}
