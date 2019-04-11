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
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_SERVER_NAME;

import io.zeebe.broker.services.DispatcherService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.transport.clientapi.ClientApiMessageHandlerService;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.ByteValue;
import io.zeebe.util.collection.IntTuple;
import io.zeebe.util.sched.future.ActorFuture;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

public class TransportComponent implements Component {
  @Override
  public void init(final SystemContext context) {
    createSocketBindings(context);
    createClientTransports(context);
  }

  private void createClientTransports(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();
    final BrokerCfg brokerCfg = context.getBrokerConfiguration();
    final NetworkCfg networkCfg = brokerCfg.getNetwork();
    final int nodeId = brokerCfg.getCluster().getNodeId();
    final SocketAddress managementEndpoint = networkCfg.getManagement().toSocketAddress();

    final ActorFuture<ClientTransport> managementClientFuture =
        createClientTransport(
            serviceContainer,
            MANAGEMENT_API_CLIENT_NAME,
            new ByteValue(networkCfg.getDefaultSendBufferSize()),
            Collections.singletonList(new IntTuple<>(nodeId, managementEndpoint)));

    context.addRequiredStartAction(managementClientFuture);
  }

  private void createSocketBindings(final SystemContext context) {
    final NetworkCfg networkCfg = context.getBrokerConfiguration().getNetwork();
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final ActorFuture<BufferingServerTransport> managementApiFuture =
        bindBufferingProtocolEndpoint(
            context,
            serviceContainer,
            MANAGEMENT_API_SERVER_NAME,
            networkCfg.getManagement(),
            new ByteValue(networkCfg.getManagement().getReceiveBufferSize()));

    context.addRequiredStartAction(managementApiFuture);

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

  protected ActorFuture<BufferingServerTransport> bindBufferingProtocolEndpoint(
      final SystemContext systemContext,
      final ServiceContainer serviceContainer,
      final String name,
      final SocketBindingCfg socketBindingCfg,
      final ByteValue receiveBufferSize) {

    final SocketAddress bindAddr = socketBindingCfg.toSocketAddress();

    return createBufferingServerTransport(
        systemContext,
        serviceContainer,
        name,
        bindAddr.toInetSocketAddress(),
        new ByteValue(socketBindingCfg.getSendBufferSize()),
        receiveBufferSize);
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

  protected ActorFuture<BufferingServerTransport> createBufferingServerTransport(
      final SystemContext systemContext,
      final ServiceContainer serviceContainer,
      final String name,
      final InetSocketAddress bindAddress,
      final ByteValue sendBufferSize,
      final ByteValue receiveBufferSize) {
    final ServiceName<Dispatcher> receiveBufferName =
        createReceiveBuffer(serviceContainer, name, receiveBufferSize);

    final BufferingServerTransportService service =
        new BufferingServerTransportService(name, bindAddress, sendBufferSize);

    systemContext.addResourceReleasingDelegate(service.getReleasingResourcesDelegate());
    return serviceContainer
        .createService(TransportServiceNames.bufferingServerTransport(name), service)
        .dependency(receiveBufferName, service.getReceiveBufferInjector())
        .install();
  }

  protected void createDispatcher(
      final ServiceContainer serviceContainer,
      final ServiceName<Dispatcher> name,
      final ByteValue sendBufferSize) {
    final DispatcherBuilder dispatcherBuilder = Dispatchers.create(null).bufferSize(sendBufferSize);

    final DispatcherService receiveBufferService = new DispatcherService(dispatcherBuilder);
    serviceContainer.createService(name, receiveBufferService).install();
  }

  protected ServiceName<Dispatcher> createReceiveBuffer(
      final ServiceContainer serviceContainer,
      final String transportName,
      final ByteValue bufferSize) {
    final ServiceName<Dispatcher> serviceName =
        TransportServiceNames.receiveBufferName(transportName);
    createDispatcher(serviceContainer, serviceName, bufferSize);

    return serviceName;
  }

  protected ActorFuture<ClientTransport> createClientTransport(
      final ServiceContainer serviceContainer,
      final String name,
      final ByteValue sendBufferSize,
      final Collection<IntTuple<SocketAddress>> defaultEndpoints) {
    final ClientTransportService service =
        new ClientTransportService(name, defaultEndpoints, sendBufferSize);

    return serviceContainer
        .createService(TransportServiceNames.clientTransport(name), service)
        .install();
  }
}
