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

import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import java.util.Collection;

public class ClientTransportService implements Service<ClientTransport> {
  protected final Collection<SocketAddress> defaultEndpoints;
  private final ByteValue messageBufferSize;

  protected ClientTransport transport;

  public ClientTransportService(
      Collection<SocketAddress> defaultEndpoints, ByteValue messageBufferSize) {
    this.defaultEndpoints = defaultEndpoints;
    this.messageBufferSize = messageBufferSize;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final ActorScheduler scheduler = startContext.getScheduler();

    final ClientTransportBuilder transportBuilder = Transports.newClientTransport();

    transport =
        transportBuilder
            .messageMemoryPool(new NonBlockingMemoryPool(messageBufferSize))
            // client transport in broker should no do any high volume interactions using
            // request/resp
            .requestMemoryPool(new UnboundedMemoryPool())
            .scheduler(scheduler)
            .build();

    if (defaultEndpoints != null) {
      // make transport open and manage channels to the default endpoints
      defaultEndpoints.forEach(s -> transport.registerRemoteAddress(s));
    }
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(transport.closeAsync());
  }

  @Override
  public ClientTransport get() {
    return transport;
  }
}
