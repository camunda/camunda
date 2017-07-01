package io.zeebe.broker.transport.binding;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ReceiveBufferChannelHandler;
import io.zeebe.transport.ServerSocketBinding;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transport;

public class ServerSocketBindingService implements Service<ServerSocketBinding>
{
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();

    protected final String bindingName;
    protected final SocketAddress bindAddress;

    protected ServerSocketBinding serverSocketBinding;

    public ServerSocketBindingService(String bindingName, SocketAddress bindAddress)
    {
        this.bindingName = bindingName;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();

        serviceContext.async(transport.createServerSocketBinding(bindAddress)
            .transportChannelHandler(new ReceiveBufferChannelHandler(receiveBuffer))
            .bindAsync()
            .thenAccept((binding) ->
            {
                serverSocketBinding = binding;
                System.out.format("Bound %s to %s.\n", bindingName, bindAddress);
            }));
    }

    @Override
    public void stop(ServiceStopContext serviceStopContext)
    {
        serviceStopContext.async(serverSocketBinding.closeAsync());
    }

    @Override
    public ServerSocketBinding get()
    {
        return serverSocketBinding;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

}
