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

import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_MESSAGE_HANDLER;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_SERVER_NAME;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.clustering.ClusterServiceNames;
import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.services.DispatcherService;
import io.zeebe.broker.services.DispatcherSubscriptionNames;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.task.TaskQueueServiceNames;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.broker.transport.clientapi.ClientApiMessageHandlerService;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManagerService;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerTransportBuilder;
import io.zeebe.transport.SocketAddress;

public class TransportComponent implements Component
{
    protected static final int MGMT_REQUEST_POOL_SIZE = 128;

    @Override
    public void init(SystemContext context)
    {
        final TransportComponentCfg transportComponentCfg = context.getConfigurationManager().readEntry("network", TransportComponentCfg.class);
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final CompletableFuture<Void> replactionApiFuture = bindBufferingProtocolEndpoint(
                serviceContainer,
                REPLICATION_API_SERVER_NAME,
                transportComponentCfg.replicationApi,
                transportComponentCfg);

        final CompletableFuture<Void> managementApiFuture = bindBufferingProtocolEndpoint(
                serviceContainer,
                MANAGEMENT_API_SERVER_NAME,
                transportComponentCfg.managementApi,
                transportComponentCfg);

        final CompletableFuture<Void> clientApiFuture = bindNonBufferingProtocolEndpoint(
                serviceContainer,
                CLIENT_API_SERVER_NAME,
                transportComponentCfg.clientApi,
                transportComponentCfg,
                CLIENT_API_MESSAGE_HANDLER,
                CLIENT_API_MESSAGE_HANDLER);

        final SocketAddress managementEndpoint = transportComponentCfg.managementApi.toSocketAddress(transportComponentCfg.host);

        final CompletableFuture<Void> managementClientFuture = createClientTransport(serviceContainer,
                MANAGEMENT_API_CLIENT_NAME,
                transportComponentCfg.managementApi.getReceiveBufferSize(transportComponentCfg.defaultReceiveBufferSize),
                MGMT_REQUEST_POOL_SIZE,
                true,
                Collections.singletonList(managementEndpoint));

        final CompletableFuture<Void> replicationClientFuture = createClientTransport(serviceContainer,
                REPLICATION_API_CLIENT_NAME,
                transportComponentCfg.replicationApi.getReceiveBufferSize(transportComponentCfg.defaultReceiveBufferSize),
                MGMT_REQUEST_POOL_SIZE,
                false,
                null);

        final ServiceName<Dispatcher> controlMessageBufferService = createReceiveBuffer(
            serviceContainer,
            CLIENT_API_SERVER_NAME,
            transportComponentCfg.clientApi.getReceiveBufferSize(transportComponentCfg.defaultReceiveBufferSize),
            DispatcherSubscriptionNames.TRANSPORT_CONTROL_MESSAGE_HANDLER_SUBSCRIPTION);

        final ClientApiMessageHandlerService messageHandlerService = new ClientApiMessageHandlerService();
        serviceContainer.createService(CLIENT_API_MESSAGE_HANDLER, messageHandlerService)
            .dependency(controlMessageBufferService, messageHandlerService.getControlMessageBufferInjector())
            .groupReference(LogStreamServiceNames.WORKFLOW_STREAM_GROUP, messageHandlerService.getLogStreamsGroupReference())
            .groupReference(LogStreamServiceNames.SYSTEM_STREAM_GROUP, messageHandlerService.getLogStreamsGroupReference())
            .install();

        final long controlMessageRequestTimeoutInMillis = transportComponentCfg.clientApi.getControlMessageRequestTimeoutInMillis(Long.MAX_VALUE);

        final ControlMessageHandlerManagerService controlMessageHandlerManagerService = new ControlMessageHandlerManagerService(controlMessageRequestTimeoutInMillis);
        final CompletableFuture<Void> controlMessageServiceFuture = serviceContainer.createService(TransportServiceNames.CONTROL_MESSAGE_HANDLER_MANAGER, controlMessageHandlerManagerService)
            .dependency(controlMessageBufferService, controlMessageHandlerManagerService.getControlMessageBufferInjector())
            .dependency(TransportServiceNames.serverTransport(CLIENT_API_SERVER_NAME), controlMessageHandlerManagerService.getTransportInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, controlMessageHandlerManagerService.getActorSchedulerInjector())
            .dependency(TaskQueueServiceNames.TASK_QUEUE_SUBSCRIPTION_MANAGER, controlMessageHandlerManagerService.getTaskSubscriptionManagerInjector())
            .dependency(TopicSubscriptionServiceNames.TOPIC_SUBSCRIPTION_SERVICE, controlMessageHandlerManagerService.getTopicSubscriptionServiceInjector())
            .dependency(SystemServiceNames.SYSTEM_LOG_MANAGER, controlMessageHandlerManagerService.getSystemPartitionManagerInjector())
            .dependency(ClusterServiceNames.CLUSTER_MANAGER_SERVICE, controlMessageHandlerManagerService.getClusterManagerInjector())
            .install();

        context.addRequiredStartAction(replactionApiFuture);
        context.addRequiredStartAction(managementApiFuture);
        context.addRequiredStartAction(clientApiFuture);
        context.addRequiredStartAction(managementClientFuture);
        context.addRequiredStartAction(replicationClientFuture);
        context.addRequiredStartAction(controlMessageServiceFuture);
    }

    protected CompletableFuture<Void> bindBufferingProtocolEndpoint(
            ServiceContainer serviceContainer,
            String name,
            SocketBindingCfg socketBindingCfg,
            TransportComponentCfg defaultConfig)
    {

        final SocketAddress bindAddr = socketBindingCfg.toSocketAddress(defaultConfig.host);

        return createBufferingServerTransport(
                serviceContainer,
                name,
                bindAddr.toInetSocketAddress(),
                socketBindingCfg.getSendBufferSize(defaultConfig.sendBufferSize),
                socketBindingCfg.getReceiveBufferSize(defaultConfig.defaultReceiveBufferSize));
    }

    protected CompletableFuture<Void> bindNonBufferingProtocolEndpoint(
            ServiceContainer serviceContainer,
            String name,
            SocketBindingCfg socketBindingCfg,
            TransportComponentCfg defaultConfig,
            ServiceName<? extends ServerRequestHandler> requestHandlerService,
            ServiceName<? extends ServerMessageHandler> messageHandlerService)
    {

        final SocketAddress bindAddr = socketBindingCfg.toSocketAddress(defaultConfig.host);

        return createServerTransport(
                serviceContainer,
                name,
                bindAddr.toInetSocketAddress(),
                socketBindingCfg.getSendBufferSize(defaultConfig.sendBufferSize),
                requestHandlerService,
                messageHandlerService);
    }

    protected CompletableFuture<Void> createServerTransport(
            ServiceContainer serviceContainer,
            String name,
            InetSocketAddress bindAddress,
            int sendBufferSize,
            ServiceName<? extends ServerRequestHandler> requestHandlerDependency,
            ServiceName<? extends ServerMessageHandler> messageHandlerDependency)
    {
        final ServiceName<Dispatcher> sendBufferName = createSendBuffer(serviceContainer, name, sendBufferSize);

        final ServerTransportService service = new ServerTransportService(name, bindAddress);

        return serviceContainer.createService(TransportServiceNames.serverTransport(name), service)
            .dependency(sendBufferName, service.getSendBufferInjector())
            .dependency(requestHandlerDependency, service.getRequestHandlerInjector())
            .dependency(messageHandlerDependency, service.getMessageHandlerInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, service.getSchedulerInjector())
            .install();

    }

    protected CompletableFuture<Void> createBufferingServerTransport(
            ServiceContainer serviceContainer,
            String name,
            InetSocketAddress bindAddress,
            int sendBufferSize,
            int receiveBufferSize)
    {
        final ServiceName<Dispatcher> sendBufferName = createSendBuffer(serviceContainer, name, sendBufferSize);
        final ServiceName<Dispatcher> receiveBufferName = createReceiveBuffer(serviceContainer, name, receiveBufferSize);

        final BufferingServerTransportService service = new BufferingServerTransportService(name, bindAddress);

        return serviceContainer.createService(TransportServiceNames.bufferingServerTransport(name), service)
            .dependency(receiveBufferName, service.getReceiveBufferInjector())
            .dependency(sendBufferName, service.getSendBufferInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, service.getSchedulerInjector())
            .install();
    }

    protected void createDispatcher(ServiceContainer serviceContainer, ServiceName<Dispatcher> name, int bufferSize, String... subscriptions)
    {
        final DispatcherBuilder dispatcherBuilder = Dispatchers.create(null)
            .bufferSize(bufferSize)
            .subscriptions(subscriptions);

        final DispatcherService receiveBufferService = new DispatcherService(dispatcherBuilder);
        serviceContainer.createService(name, receiveBufferService)
            .dependency(ACTOR_SCHEDULER_SERVICE, receiveBufferService.getActorSchedulerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, receiveBufferService.getCountersManagerInjector())
            .install();
    }

    protected ServiceName<Dispatcher> createSendBuffer(ServiceContainer serviceContainer, String transportName, int bufferSize)
    {
        final ServiceName<Dispatcher> serviceName = TransportServiceNames.sendBufferName(transportName);
        createDispatcher(serviceContainer, serviceName, bufferSize, ServerTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME);

        return serviceName;
    }

    protected ServiceName<Dispatcher> createReceiveBuffer(
            ServiceContainer serviceContainer,
            String transportName,
            int bufferSize,
            String... subscriptionNames)
    {
        final ServiceName<Dispatcher> serviceName = TransportServiceNames.receiveBufferName(transportName);
        createDispatcher(serviceContainer, serviceName, bufferSize, subscriptionNames);

        return serviceName;
    }

    protected CompletableFuture<Void> createClientTransport(
            ServiceContainer serviceContainer,
            String name,
            int receiveBufferSize,
            int requestPoolSize,
            boolean enableManagedRequests,
            Collection<SocketAddress> defaultEndpoints)
    {
        final ServiceName<Dispatcher> receiveBufferName = createReceiveBuffer(serviceContainer, name, receiveBufferSize);
        final ServiceName<Dispatcher> sendBufferName = createSendBuffer(serviceContainer, name, receiveBufferSize);

        final ClientTransportService service = new ClientTransportService(requestPoolSize, enableManagedRequests, defaultEndpoints);

        return serviceContainer.createService(TransportServiceNames.clientTransport(name), service)
            .dependency(receiveBufferName, service.getReceiveBufferInjector())
            .dependency(sendBufferName, service.getSendBufferInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, service.getSchedulerInjector())
            .install();
    }
}
