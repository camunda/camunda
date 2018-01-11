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
package io.zeebe.broker.transport;

import java.util.Collection;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ClientTransportBuilder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.actor.ActorScheduler;

public class ClientTransportService implements Service<ClientTransport>
{
    protected final Injector<ActorScheduler> schedulerInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();

    protected final int requestPoolSize;
    protected final Collection<SocketAddress> defaultEndpoints;
    protected final boolean enableManagedRequests;

    protected ClientTransport transport;

    public ClientTransportService(int requestPoolSize, boolean enableManagedRequests, Collection<SocketAddress> defaultEndpoints)
    {
        this.requestPoolSize = requestPoolSize;
        this.defaultEndpoints = defaultEndpoints;
        this.enableManagedRequests = enableManagedRequests;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {

        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final ActorScheduler scheduler = schedulerInjector.getValue();

        final ClientTransportBuilder transportBuilder = Transports.newClientTransport();
        if (enableManagedRequests)
        {
            transportBuilder.enableManagedRequests();
        }

        transport = transportBuilder
            .messageReceiveBuffer(receiveBuffer)
            .sendBuffer(sendBuffer)
            .requestPoolSize(requestPoolSize)
            .scheduler(scheduler)
            .build();

        if (defaultEndpoints != null)
        {
            // make transport open and manage channels to the default endpoints
            defaultEndpoints.forEach(s -> transport.registerRemoteAddress(s));
        }
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(transport.closeAsync());
    }

    @Override
    public ClientTransport get()
    {
        return transport;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

    public Injector<ActorScheduler> getSchedulerInjector()
    {
        return schedulerInjector;
    }

}
