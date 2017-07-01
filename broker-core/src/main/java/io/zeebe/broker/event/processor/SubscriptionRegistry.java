package io.zeebe.broker.event.processor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

public class SubscriptionRegistry
{

    protected SubscriptionIterator iterator = new SubscriptionIterator();

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

    /**
     * This is not supposed to be used concurrently
     */
    public Iterator<TopicSubscriptionPushProcessor> iterateSubscriptions()
    {
        iterator.reset();
        return iterator;
    }

    protected class SubscriptionIterator implements Iterator<TopicSubscriptionPushProcessor>
    {
        protected Iterator<TopicSubscriptionPushProcessor> innerIterator;
        protected TopicSubscriptionPushProcessor currentValue = null;

        protected void reset()
        {
            innerIterator = subscriptionProcessorsByKey.values().iterator();
        }

        @Override
        public boolean hasNext()
        {
            return innerIterator.hasNext();
        }

        @Override
        public TopicSubscriptionPushProcessor next()
        {
            currentValue = innerIterator.next();
            return currentValue;
        }

        @Override
        public void remove()
        {
            innerIterator.remove();
            subscriptionProcessorsByName.remove(currentValue.getName());
        }
    }
}
