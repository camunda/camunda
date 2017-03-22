package org.camunda.tngp.broker.event.processor;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

public class SubscriptionRegistry
{

    protected final Long2ObjectHashMap<TopicSubscriptionPushProcessor> subscriptionProcessorsByKey = new Long2ObjectHashMap<>();
    protected final Map<DirectBuffer, TopicSubscriptionPushProcessor> subscriptionProcessorsByName = new HashMap<>();

    public void addSubscription(TopicSubscriptionPushProcessor processor)
    {
        subscriptionProcessorsByKey.put(processor.getSubscriptionId(), processor);
        subscriptionProcessorsByName.put(processor.getName(), processor);
    }

    public TopicSubscriptionPushProcessor getProcessorByName(DirectBuffer name)
    {
        return subscriptionProcessorsByName.get(name);
    }

    public TopicSubscriptionPushProcessor removeProcessorByKey(long key)
    {
        final TopicSubscriptionPushProcessor processor = subscriptionProcessorsByKey.remove(key);
        if (processor != null)
        {
            subscriptionProcessorsByName.remove(processor.getName());
        }

        return processor;
    }
}
