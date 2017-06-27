package io.zeebe.client.event.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.protocol.clientapi.EventType;

public class CreateTopicSubscriptionCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriberEvent, EventSubscriptionCreationResult>
{
    protected final TopicSubscriberEvent subscription = new TopicSubscriberEvent();

    public CreateTopicSubscriptionCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TopicSubscriberEvent.class, EventType.SUBSCRIBER_EVENT);
    }

    public CreateTopicSubscriptionCmdImpl startPosition(long startPosition)
    {
        this.subscription.setStartPosition(startPosition);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl name(String name)
    {
        this.subscription.setName(name);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl prefetchCapacity(int prefetchCapacity)
    {
        this.subscription.setPrefetchCapacity(prefetchCapacity);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl forceStart(boolean forceStart)
    {
        this.subscription.setForceStart(forceStart);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        this.subscription.setEventType(SubscriberEventType.SUBSCRIBE);
        return subscription;
    }

    @Override
    protected void reset()
    {
        subscription.reset();
    }

    @Override
    protected long getKey()
    {
        return -1;
    }

    @Override
    protected EventSubscriptionCreationResult getResponseValue(long key, TopicSubscriberEvent event)
    {
        return new EventSubscriptionCreationResult(key);
    }

}
