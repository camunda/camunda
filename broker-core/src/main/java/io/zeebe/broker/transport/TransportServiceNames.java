package io.zeebe.broker.transport;

import io.zeebe.broker.transport.clientapi.ClientApiMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

public class TransportServiceNames
{
    public static final ServiceName<ClientApiMessageHandler> CLIENT_API_MESSAGE_HANDLER = ServiceName.newServiceName("transport.clientApi.messageHandler", ClientApiMessageHandler.class);
    public static final ServiceName<ControlMessageHandlerManager> CONTROL_MESSAGE_HANDLER_MANAGER = ServiceName.newServiceName("transport.clientApi.controlMessage", ControlMessageHandlerManager.class);

    public static final String CLIENT_API_SERVER_NAME = "clientApi.server";
    public static final String MANAGEMENT_API_SERVER_NAME = "managementApi.server";
    public static final String REPLICATION_API_SERVER_NAME = "replicationApi.server";
    public static final String MANAGEMENT_API_CLIENT_NAME = "managementApi.client";
    public static final String REPLICATION_API_CLIENT_NAME = "replicationApi.client";

    public static ServiceName<Dispatcher> receiveBufferName(String identifier)
    {
        return ServiceName.newServiceName(String.format("transport.%s.receive-buffer", identifier), Dispatcher.class);
    }

    public static ServiceName<Dispatcher> sendBufferName(String identifier)
    {
        return ServiceName.newServiceName(String.format("transport.%s.send-buffer", identifier), Dispatcher.class);
    }

    public static ServiceName<ServerTransport> serverTransport(String identifier)
    {
        return ServiceName.newServiceName(String.format("transport.%s.server", identifier), ServerTransport.class);
    }

    // TODO: kann man das mit generics mit serverTransport(..) vereinigen?
    public static ServiceName<BufferingServerTransport> bufferingServerTransport(String identifier)
    {
        return ServiceName.newServiceName(String.format("transport.%s.buffering-server", identifier), BufferingServerTransport.class);
    }

    public static ServiceName<ClientTransport> clientTransport(String identifier)
    {
        return ServiceName.newServiceName(String.format("transport.%s.client", identifier), ClientTransport.class);
    }

}
