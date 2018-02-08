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

import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import org.agrona.collections.Int2ObjectHashMap;

public class ClientChannelManager
{
    protected Int2ObjectHashMap<TransportChannel> channels = new Int2ObjectHashMap<>();
    protected final ClientConductor conductor;

    public ClientChannelManager(ClientConductor conductor)
    {
        this.conductor = conductor;
    }

    private void openChannel(final RemoteAddressImpl address)
    {
        final TransportChannel channel = conductor.openChannel(address);

        if (channel != null)
        {
            channels.put(address.getStreamId(), channel);
        }
    }

    public void onChannelClosed(TransportChannel channel)
    {
        // TODO: this callback does not work yet in a reliable way;
        // it is only called if the channel was connected before, but we also want to retry
        // if the channel was not connected

        final RemoteAddressImpl remoteAddress = channel.getRemoteAddress();

        if (remoteAddress.isActive())
        {
            openChannel(remoteAddress);
        }
        else
        {
            channels.remove(remoteAddress.getStreamId());
        }
    }

    public void onRemoteAddressAdded(RemoteAddressImpl remoteAddress)
    {
        final TransportChannel channel = channels.get(remoteAddress.getStreamId());

        if (channel == null)
        {
            openChannel(remoteAddress);
        }
        else
        {
            if (channel.isClosed())
            {
                openChannel(remoteAddress);
            }
        }
    }
}
