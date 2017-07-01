package io.zeebe.broker.transport.clientapi;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerSocketBinding;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transport;

public class ClientApiSocketBindingService implements Service<ServerSocketBinding>
{
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<ClientApiMessageHandler> messageHandlerInjector = new Injector<>();

    protected final String bindingName;
    protected final SocketAddress bindAddress;

    protected ServerSocketBinding serverSocketBinding;

    public ClientApiSocketBindingService(String bindingName, SocketAddress bindAddress)
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
    public void stop(ServiceStopContext context)
    {
        context.async(serverSocketBinding.closeAsync());
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
