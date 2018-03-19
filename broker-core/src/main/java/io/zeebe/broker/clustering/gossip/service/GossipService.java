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

import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

public class GossipService implements Service<Gossip>
{
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final Injector<BufferingServerTransport> bufferingServerTransportInjector = new Injector<>();
    private final TransportComponentCfg transportComponentCfg;

    private Gossip gossip;
    private ActorScheduler actorScheduler;

    public GossipService(TransportComponentCfg transportComponentCfg)
    {
        this.transportComponentCfg = transportComponentCfg;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        actorScheduler = startContext.getScheduler();
        final SocketAddress host = new SocketAddress(transportComponentCfg.managementApi.getHost(transportComponentCfg.host), transportComponentCfg.managementApi.port);

        this.gossip = new Gossip(host, bufferingServerTransportInjector.getValue(),
                                 clientTransportInjector.getValue(), transportComponentCfg.gossip);

        actorScheduler.submitActor(gossip);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(actorScheduler.submitActor(new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                final ActorFuture<Void> leaveFuture = gossip.leave();
                actor.runOnCompletion(leaveFuture, ((aVoid, throwable) ->
                {
                    final ActorFuture<Void> closeFuture = gossip.close();
                    actor.runOnCompletion(closeFuture, ((aVoid1, throwable1) ->
                    {
                        // closed
                    }));
                }));
            }
        }));
    }

    @Override
    public Gossip get()
    {
        return gossip;
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
