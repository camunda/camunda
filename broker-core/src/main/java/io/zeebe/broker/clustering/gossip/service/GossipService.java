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
package io.zeebe.broker.clustering.gossip.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class GossipService implements Service<Gossip>
{
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final Injector<BufferingServerTransport> bufferingServerTransportInjector = new Injector<>();

    private Gossip gossip;
    private ActorReference actorRef;
    private final TransportComponentCfg transportComponentCfg;

    public GossipService(TransportComponentCfg transportComponentCfg)
    {
        this.transportComponentCfg = transportComponentCfg;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        final SocketAddress host = new SocketAddress(transportComponentCfg.managementApi.host, transportComponentCfg.managementApi.port);

        this.gossip = new Gossip(host, bufferingServerTransportInjector.getValue(),
                                 clientTransportInjector.getValue(), transportComponentCfg.gossip);

        final List<SocketAddress> collect = Arrays.stream(transportComponentCfg.gossip.initialContactPoints)
                                                  .map(SocketAddress::from)
                                                  .collect(Collectors.toList());

        if (!collect.isEmpty())
        {
            gossip.join(collect);
        }

        actorRef = actorScheduler.schedule(gossip);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(gossip.leave()
                                .whenComplete((v, t) -> actorRef.close()));
    }

    @Override
    public Gossip get()
    {
        return gossip;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

    public Injector<BufferingServerTransport> getBufferingServerTransportInjector()
    {
        return bufferingServerTransportInjector;
    }
}
