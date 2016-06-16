package org.camunda.tngp.broker.transport.binding;

import java.net.InetSocketAddress;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;

public class ServerSocketBindingService implements Service<ServerSocketBinding>
{
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();

    protected final String bindingName;
    protected final InetSocketAddress bindAddress;

    protected ServerSocketBinding serverSocketBinding;

    public ServerSocketBindingService(String bindingName, InetSocketAddress bindAddress)
    {
        this.bindingName = bindingName;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();

        serverSocketBinding = transport.createServerSocketBinding(bindAddress)
            .transportChannelHandler(new ReceiveBufferChannelHandler(receiveBuffer))
            .bind();

        System.out.println(String.format("Bound %s to %s.", bindingName, bindAddress));
    }

    @Override
    public void stop()
    {
        serverSocketBinding.close();
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
