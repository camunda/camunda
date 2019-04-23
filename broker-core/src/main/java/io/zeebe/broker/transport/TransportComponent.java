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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_MESSAGE_HANDLER;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.transport.clientapi.ClientApiMessageHandlerService;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.future.ActorFuture;
import java.net.InetSocketAddress;

public class TransportComponent implements Component {
  @Override
  public void init(final SystemContext context) {
    createSocketBindings(context);
  }

  private void createSocketBindings(final SystemContext context) {
    final NetworkCfg networkCfg = context.getBrokerConfiguration().getNetwork();
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final ActorFuture<ServerTransport> clientApiFuture =
        bindNonBufferingProtocolEndpoint(
            context,
            serviceContainer,
            CLIENT_API_SERVER_NAME,
            networkCfg.getClient(),
            CLIENT_API_MESSAGE_HANDLER,
            CLIENT_API_MESSAGE_HANDLER);

    context.addRequiredStartAction(clientApiFuture);

    final ClientApiMessageHandlerService messageHandlerService =
        new ClientApiMessageHandlerService();
    serviceContainer
        .createService(CLIENT_API_MESSAGE_HANDLER, messageHandlerService)
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, messageHandlerService.getLeaderParitionsGroupReference())
        .install();
  }

  protected ActorFuture<ServerTransport> bindNonBufferingProtocolEndpoint(
      final SystemContext systemContext,
      final ServiceContainer serviceContainer,
      final String name,
      final SocketBindingCfg socketBindingCfg,
      final ServiceName<? extends ServerRequestHandler> requestHandlerService,
      final ServiceName<? extends ServerMessageHandler> messageHandlerService) {

    final SocketAddress bindAddr = socketBindingCfg.toSocketAddress();

    return createServerTransport(
        systemContext,
        serviceContainer,
        name,
        bindAddr.toInetSocketAddress(),
        new ByteValue(socketBindingCfg.getSendBufferSize()),
        requestHandlerService,
        messageHandlerService);
  }

  protected ActorFuture<ServerTransport> createServerTransport(
      final SystemContext systemContext,
      final ServiceContainer serviceContainer,
      final String name,
      final InetSocketAddress bindAddress,
      final ByteValue sendBufferSize,
      final ServiceName<? extends ServerRequestHandler> requestHandlerDependency,
      final ServiceName<? extends ServerMessageHandler> messageHandlerDependency) {
    final ServerTransportService service =
        new ServerTransportService(name, bindAddress, sendBufferSize);

    systemContext.addResourceReleasingDelegate(service.getReleasingResourcesDelegate());

    return serviceContainer
        .createService(TransportServiceNames.serverTransport(name), service)
        .dependency(requestHandlerDependency, service.getRequestHandlerInjector())
        .dependency(messageHandlerDependency, service.getMessageHandlerInjector())
        .install();
  }
}
