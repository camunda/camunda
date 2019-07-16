/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.COMMAND_API_MESSAGE_HANDLER;
import static io.zeebe.broker.transport.TransportServiceNames.COMMAND_API_SERVER_NAME;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.transport.commandapi.CommandApiMessageHandlerService;
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

    final ActorFuture<ServerTransport> commandApiFuture =
        bindNonBufferingProtocolEndpoint(
            context,
            serviceContainer,
            COMMAND_API_SERVER_NAME,
            networkCfg.getCommandApi(),
            COMMAND_API_MESSAGE_HANDLER,
            COMMAND_API_MESSAGE_HANDLER);

    context.addRequiredStartAction(commandApiFuture);

    final CommandApiMessageHandlerService messageHandlerService =
        new CommandApiMessageHandlerService();
    serviceContainer
        .createService(COMMAND_API_MESSAGE_HANDLER, messageHandlerService)
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
