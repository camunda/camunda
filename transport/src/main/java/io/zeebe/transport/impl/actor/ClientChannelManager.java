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

import java.util.Iterator;

import org.agrona.collections.Int2ObjectHashMap;

import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.TransportChannel;

public class ClientChannelManager
{

    protected Int2ObjectHashMap<TransportChannel> channels = new Int2ObjectHashMap<>();
    protected final RemoteAddressListImpl registeredAddresses;
    protected final ClientConductor conductor;

    public ClientChannelManager(ClientConductor conductor, RemoteAddressListImpl registeredAddresses)
    {
        this.registeredAddresses = registeredAddresses;
        this.conductor = conductor;
    }

    public int maintainChannels()
    {
        int workCount = 0;

        workCount += openNewChannels();
        workCount += reopenChannels();

        return workCount;
    }

    private int reopenChannels()
    {
        int workCount = 0;
        final Iterator<TransportChannel> channelIt = channels.values().iterator();

        while (channelIt.hasNext())
        {
            final TransportChannel channel = channelIt.next();
            if (channel.isClosed())
            {
                final RemoteAddressImpl remoteAddress = channel.getRemoteAddress();
                if (remoteAddress.isActive())
                {
                    openChannel(remoteAddress);
                }
                else
                {
                    channelIt.remove();
                }
                workCount++;
            }
        }

        return workCount;
    }

    private int openNewChannels()
    {
        final Iterator<RemoteAddressImpl> addressIt = registeredAddresses.iterator();

        int workCount = 0;
        while (addressIt.hasNext())
        {
            final RemoteAddressImpl address = addressIt.next();
            if (!channels.containsKey(address.getStreamId()) && address.isActive())
            {
                openChannel(address);
                workCount++;
            }
        }

        return workCount;
    }

    private void openChannel(final RemoteAddressImpl address)
    {
        final TransportChannel channel = conductor.openChannel(address);

        if (channel != null)
        {
            channels.put(address.getStreamId(), channel);
        }
    }
}
