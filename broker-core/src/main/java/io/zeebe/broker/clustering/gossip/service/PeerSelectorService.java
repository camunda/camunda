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

import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.broker.clustering.gossip.protocol.util.SimplePeerSelector;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class PeerSelectorService implements Service<PeerSelector>
{
    private final Injector<PeerList> peerListInjector = new Injector<>();

    private PeerSelector peerSelector;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final PeerList peers = peerListInjector.getValue();
        this.peerSelector = new SimplePeerSelector(peers);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public PeerSelector get()
    {
        return peerSelector;
    }

    public Injector<PeerList> getPeerListInjector()
    {
        return peerListInjector;
    }

}
