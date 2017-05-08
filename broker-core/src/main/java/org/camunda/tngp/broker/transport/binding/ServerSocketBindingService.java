package org.camunda.tngp.broker.transport.binding;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.Transport;

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
