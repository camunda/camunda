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
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import java.net.InetSocketAddress;
import org.slf4j.Logger;

public class ServerTransportService implements Service<ServerTransport> {
  public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final Injector<ServerRequestHandler> requestHandlerInjector = new Injector<>();
  protected final Injector<ServerMessageHandler> messageHandlerInjector = new Injector<>();

  protected final String readableName;
  protected final InetSocketAddress bindAddress;
  private final ByteValue sendBufferSize;

  protected ServerTransport serverTransport;

  public ServerTransportService(
      String readableName, InetSocketAddress bindAddress, ByteValue sendBufferSize) {
    this.readableName = readableName;
    this.bindAddress = bindAddress;
    this.sendBufferSize = sendBufferSize;
  }

  @Override
  public void start(ServiceStartContext serviceContext) {
    final ActorScheduler scheduler = serviceContext.getScheduler();
    final ServerRequestHandler requestHandler = requestHandlerInjector.getValue();
    final ServerMessageHandler messageHandler = messageHandlerInjector.getValue();

    serverTransport =
        Transports.newServerTransport()
            .name(readableName)
            .bindAddress(bindAddress)
            .scheduler(scheduler)
            .messageMemoryPool(new NonBlockingMemoryPool(sendBufferSize))
            .build(messageHandler, requestHandler);

    LOG.info("Bound {} to {}", readableName, bindAddress);
  }

  @Override
  public void stop(ServiceStopContext serviceStopContext) {
    serviceStopContext.async(serverTransport.closeAsync());
  }

  @Override
  public ServerTransport get() {
    return serverTransport;
  }

  public Injector<ServerRequestHandler> getRequestHandlerInjector() {
    return requestHandlerInjector;
  }

  public Injector<ServerMessageHandler> getMessageHandlerInjector() {
    return messageHandlerInjector;
  }
}
