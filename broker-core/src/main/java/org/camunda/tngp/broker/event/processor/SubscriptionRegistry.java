package org.camunda.tngp.broker.event.processor;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

public class SubscriptionRegistry
{

    protected final Long2ObjectHashMap<TopicSubscriptionProcessor> subscriptionProcessorsById = new Long2ObjectHashMap<>();
    protected final Map<DirectBuffer, TopicSubscriptionProcessor> subscriptionProcessorsByName = new HashMap<>();

    public void addSubscription(TopicSubscriptionProcessor processor)
    {
        subscriptionProcessorsById.put(processor.getSubscriptionId(), processor);
        subscriptionProcessorsByName.put(processor.getName(), processor);
    }

    public TopicSubscriptionProcessor getProcessorById(long id)
    {
        return subscriptionProcessorsById.get(id);
    }

    public TopicSubscriptionProcessor getProcessorByName(DirectBuffer name)
    {
        return subscriptionProcessorsByName.get(name);
    }

    public TopicSubscriptionProcessor removeProcessorById(long id)
    {
        final TopicSubscriptionProcessor processor = subscriptionProcessorsById.remove(id);
        if (processor != null)
        {
            subscriptionProcessorsByName.remove(processor.getName());
        }

        return processor;
    }
}
