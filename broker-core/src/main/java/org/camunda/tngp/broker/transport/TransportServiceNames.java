package org.camunda.tngp.broker.transport;

import org.camunda.tngp.broker.transport.clientapi.ClientApiMessageHandler;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageHandlerManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;

public class TransportServiceNames
{
    public static final ServiceName<Transport> TRANSPORT = ServiceName.newServiceName("transport", Transport.class);
    public static final ServiceName<Dispatcher> TRANSPORT_SEND_BUFFER = ServiceName.newServiceName("transport.sendbuffer", Dispatcher.class);
    public static final ServiceName<ClientApiMessageHandler> CLIENT_API_MESSAGE_HANDLER = ServiceName.newServiceName("transport.clientApi.messageHandler", ClientApiMessageHandler.class);
    public static final ServiceName<ControlMessageHandlerManager> CONTROL_MESSAGE_HANDLER_MANAGER = ServiceName.newServiceName("transport.clientApi.controlMessage", ControlMessageHandlerManager.class);

    public static final String CLIENT_API_SOCKET_BINDING_NAME = "clientApi";
    public static final String MANAGEMENT_SOCKET_BINDING_NAME = "managementApi";
    public static final String REPLICATION_SOCKET_BINDING_NAME = "replicationApi";

    public static ServiceName<ServerSocketBinding> serverSocketBindingServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("transport.server-socket-binding.%s", bindingName), ServerSocketBinding.class);
    }


    public static ServiceName<Dispatcher> serverSocketBindingReceiveBufferName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("transport.server-socket-binding.%s.receive-buffer", bindingName), Dispatcher.class);
    }

}
