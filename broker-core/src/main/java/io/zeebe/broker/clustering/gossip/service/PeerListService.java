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

import static io.zeebe.clustering.gossip.PeerState.ALIVE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.list.CompactList;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.StreamUtil;

public class PeerListService implements Service<PeerList>
{
    private final Injector<Peer> localPeerInjector = new Injector<>();

    private final GossipConfiguration config;

    private PeerList peers;

    public PeerListService(final GossipConfiguration config)
    {
        this.config = config;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final Peer localPeer = localPeerInjector.getValue();
        startContext.run(() ->
        {
            peers = new PeerList(config.peerCapacity);

            addStoredPeers(peers, config.fileName());

            addContacts(peers, config.initialContactPoints);
            addLocalPeer(peers, localPeer);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public PeerList get()
    {
        return peers;
    }

    public Injector<Peer> getLocalPeerInjector()
    {
        return localPeerInjector;
    }

    protected void addStoredPeers(final PeerList peers, final String path)
    {
        final File file = new File(path);

        if (StreamUtil.canRead(file, StreamUtil.getSha1Digest()))
        {
            final byte[] data = new byte[(int) file.length()];

            try (InputStream is = new FileInputStream(file))
            {
                StreamUtil.read(is, data);
            }
            catch (final IOException e)
            {
                // ignore
            }

            final UnsafeBuffer buffer = new UnsafeBuffer(data);
            final CompactList underlyingList = new CompactList(buffer);
            final PeerList stored = new PeerList(underlyingList);

            peers.addAll(stored);
        }
    }

    protected void addContacts(final PeerList peers, final String[] contacts)
    {
        for (int i = 0; i < contacts.length; i++)
        {
            final String[] parts = contacts[i].split(":");
            final String host = parts[0];
            final int port = getPort(parts[1]);

            final Peer contactPoint = new Peer();

            contactPoint.managementEndpoint()
                .host(host)
                .port(port);

            contactPoint.heartbeat()
                .generation(0)
                .version(0);

            contactPoint.state(ALIVE);

            if (peers.find(contactPoint) < 0)
            {
                peers.insert(contactPoint);
            }
        }
    }

    protected void addLocalPeer(final PeerList peers, final Peer localPeer)
    {
        final int idx = peers.find(localPeer);
        if (idx > -1)
        {
            peers.set(idx, localPeer);
        }
        else
        {
            peers.insert(localPeer);
        }
    }

    protected int getPort(final String port)
    {
        try
        {
            return Integer.parseInt(port);
        }
        catch (final NumberFormatException e)
        {
            throw new RuntimeException(e);
        }
    }
}
