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
package io.zeebe.broker.clustering.base.connections;

import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;

/**
 * Registers / retires remote addresses as members / join leave the cluster
 */
public class RemoteAddressService implements Service<RemoteAddress>
{
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final SocketAddress socketAddress;
    private ClientTransport clientTransport;
    private RemoteAddress remoteAddress;

    public RemoteAddressService(SocketAddress socketAddress)
    {
        this.socketAddress = socketAddress;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        clientTransport = clientTransportInjector.getValue();
        remoteAddress = clientTransport.registerRemoteAddress(socketAddress);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        clientTransport.deactivateRemoteAddress(remoteAddress);
    }

    @Override
    public RemoteAddress get()
    {
        return remoteAddress;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }
}
