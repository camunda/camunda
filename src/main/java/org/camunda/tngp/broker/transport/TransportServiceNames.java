package org.camunda.tngp.broker.transport;

import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;

public class TransportServiceNames
{
    public final static ServiceName<Transport> TRANSPORT = ServiceName.newServiceName("transport", Transport.class);
    public final static ServiceName<Dispatcher> TRANSPORT_SEND_BUFFER = ServiceName.newServiceName("transport.sendbuffer", Dispatcher.class);

    public final static String CLIENT_API_SOCKET_BINDING_NAME = "clientApi";

    public final static ServiceName<ServerSocketBinding> serverSocketBindingServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("transport.server-socket-binding.%s", bindingName), ServerSocketBinding.class);
    }

    public final static ServiceName<Dispatcher> serverSocketBindingReceiveBufferName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("transport.server-socket-binding.%s.receive-buffer", bindingName), Dispatcher.class);
    }

}
