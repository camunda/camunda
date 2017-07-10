/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.gossip.service;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;

public class LocalPeerService implements Service<Peer>
{
    private final SocketAddress clientEndpoint;
    private final SocketAddress managementEndpoint;
    private final SocketAddress replicationEndpoint;

    private Peer localPeer;

    public LocalPeerService(final SocketAddress clientEndpoint, final SocketAddress managementEndpoint, final SocketAddress replicationEndpoint)
    {
        this.clientEndpoint = clientEndpoint;
        this.managementEndpoint = managementEndpoint;
        this.replicationEndpoint = replicationEndpoint;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.localPeer = new Peer();

        this.localPeer.heartbeat().generation(System.currentTimeMillis());
        this.localPeer.heartbeat().version(0);

        this.localPeer.clientEndpoint().wrap(clientEndpoint);
        this.localPeer.managementEndpoint().wrap(managementEndpoint);
        this.localPeer.replicationEndpoint().wrap(replicationEndpoint);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public Peer get()
    {
        return localPeer;
    }

}
