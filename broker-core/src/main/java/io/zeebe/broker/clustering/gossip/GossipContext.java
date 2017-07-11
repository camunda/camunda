/*
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
package io.zeebe.broker.clustering.gossip;

import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;

public class GossipContext
{
    private GossipConfiguration config;

    private Peer localPeer;
    private PeerList peers;

    protected ClientTransport clientTransport;
    protected BufferingServerTransport serverTransport;

    private PeerSelector peerSelector;

    public GossipConfiguration getConfig()
    {
        return config;
    }

    public void setConfig(GossipConfiguration config)
    {
        this.config = config;
    }

    public void setClientTransport(ClientTransport clientTransport)
    {
        this.clientTransport = clientTransport;
    }

    public ClientTransport getClientTransport()
    {
        return clientTransport;
    }

    public void setServerTransport(BufferingServerTransport serverTransport)
    {
        this.serverTransport = serverTransport;
    }

    public BufferingServerTransport getServerTransport()
    {
        return serverTransport;
    }

    public Peer getLocalPeer()
    {
        return localPeer;
    }

    public void setLocalPeer(Peer localPeer)
    {
        this.localPeer = localPeer;
    }

    public PeerList getPeers()
    {
        return peers;
    }

    public void setPeers(PeerList peers)
    {
        this.peers = peers;
    }

    public PeerSelector getPeerSelector()
    {
        return peerSelector;
    }

    public void setPeerSelector(PeerSelector peerSelector)
    {
        this.peerSelector = peerSelector;
    }

}
