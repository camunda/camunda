package org.camunda.tngp.broker.transport.clientapi;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class ClientApiMessageHandlerService implements Service<ClientApiMessageHandler>
{
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<Dispatcher> controlMessageBufferInjector = new Injector<>();
    protected ClientApiMessageHandler service;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> service.addStream(stream))
        .onRemove((name, stream) -> service.removeStream(stream))
        .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final Dispatcher controlMessageBuffer = controlMessageBufferInjector.getValue();

        service = new ClientApiMessageHandler(sendBuffer, controlMessageBuffer);
    }

    @Override
    public void stop(ServiceStopContext arg0)
    {
        // nothing to do
    }

    @Override
    public ClientApiMessageHandler get()
    {
        return service;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<Dispatcher> getControlMessageBufferInjector()
    {
        return controlMessageBufferInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }
}
