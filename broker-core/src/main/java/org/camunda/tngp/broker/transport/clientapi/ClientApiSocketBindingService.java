package org.camunda.tngp.broker.transport.clientapi;

import java.net.InetSocketAddress;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;

public class ClientApiSocketBindingService implements Service<ServerSocketBinding>
{
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<ClientApiMessageHandler> messageHandlerInjector = new Injector<>();

    protected final String bindingName;
    protected final InetSocketAddress bindAddress;

    protected ServerSocketBinding serverSocketBinding;

    public ClientApiSocketBindingService(String bindingName, InetSocketAddress bindAddress)
    {
        this.bindingName = bindingName;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientApiMessageHandler messageHandler = messageHandlerInjector.getValue();

        final Transport transport = transportInjector.getValue();

        startContext.async(transport.createServerSocketBinding(bindAddress)
            .transportChannelHandler(new ClientApiChannelHandler(messageHandler))
            .bindAsync()
            .thenAccept((binding) ->
            {
                serverSocketBinding = binding;
                System.out.format("Bound %s to %s.\n", bindingName, bindAddress);
            }));
    }

    @Override
    public ServerSocketBinding get()
    {
        return serverSocketBinding;
    }

    public Injector<ClientApiMessageHandler> getMessageHandlerInjector()
    {
        return messageHandlerInjector;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }
}
