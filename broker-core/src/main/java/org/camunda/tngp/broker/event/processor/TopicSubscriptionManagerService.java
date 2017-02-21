package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.transport.clientapi.SingleMessageWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class TopicSubscriptionManagerService implements Service<TopicSubscriptionManager>
{
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected AgentRunnerService agentRunnerService;

    protected TopicSubscriptionManager subscriptionManager;
    protected StreamProcessorManager streamProcessorManager;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> streamProcessorManager.addLogStream(name, stream))
        .onRemove((name, stream) -> streamProcessorManager.removeLogStream(stream))
        .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        agentRunnerService = agentRunnerServicesInjector.getValue().conductorAgentRunnerService();

        final AsyncContext asyncContext = new AsyncContext();
        streamProcessorManager = new StreamProcessorManager(startContext, asyncContext);

        subscriptionManager = new TopicSubscriptionManager(
            streamProcessorManager,
            asyncContext,
            () ->
            {
                return new SubscribedEventWriter(new SingleMessageWriter(sendBufferInjector.getValue()));
            });

        agentRunnerService.run(subscriptionManager);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        agentRunnerService.remove(subscriptionManager);
    }

    @Override
    public TopicSubscriptionManager get()
    {
        return subscriptionManager;
    }

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }
}
