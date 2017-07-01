package io.zeebe.broker.transport.clientapi;

import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class ClientApiMessageHandlerService implements Service<ClientApiMessageHandler>
{
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<Dispatcher> controlMessageBufferInjector = new Injector<>();
    protected final Injector<TopicSubscriptionService> topicSubcriptionServiceInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubcriptionManagerInjector = new Injector<>();
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
        final TopicSubscriptionService topicSubscriptionService = topicSubcriptionServiceInjector.getValue();
        final TaskSubscriptionManager taskSubscriptionManager = taskSubcriptionManagerInjector.getValue();
        final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter(sendBuffer);

        service = new ClientApiMessageHandler(
                sendBuffer,
                controlMessageBuffer,
                errorResponseWriter,
                topicSubscriptionService,
                taskSubscriptionManager);
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

    public Injector<TopicSubscriptionService> getTopicSubcriptionServiceInjector()
    {
        return topicSubcriptionServiceInjector;
    }

    public Injector<TaskSubscriptionManager> getTaskSubcriptionManagerInjector()
    {
        return taskSubcriptionManagerInjector;
    }
}
