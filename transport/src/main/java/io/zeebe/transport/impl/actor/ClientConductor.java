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

import org.agrona.nio.TransportPoller;

import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ConnectTransportPoller;

public class ClientConductor extends Conductor
{
    private final ConnectTransportPoller connectTransportPoller;
    private final TransportPoller[] closableTransportPoller;
    private final ClientChannelManager channelManager;

    public ClientConductor(ActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
        this.connectTransportPoller = new ConnectTransportPoller(context.getChannelConnectTimeout());
        closableTransportPoller = new TransportPoller[]{connectTransportPoller};
        this.channelManager = new ClientChannelManager(this, context.getRemoteAddressList());
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = super.doWork();

        workCount += connectTransportPoller.doWork();
        workCount += channelManager.maintainChannels();

        return workCount;
    }

    public TransportChannel openChannel(RemoteAddressImpl address)
    {
        final TransportChannel channel =
            channelFactory.buildClientChannel(
                this,
                address,
                transportContext.getMessageMaxLength(),
                transportContext.getReceiveHandler());

        if (channel.beginConnect())
        {
            connectTransportPoller.addChannel(channel);
            return channel;
        }
        else
        {
            return null;
        }

    }

    @Override
    protected TransportPoller[] getClosableTransportPoller()
    {
        return closableTransportPoller;
    }
}
