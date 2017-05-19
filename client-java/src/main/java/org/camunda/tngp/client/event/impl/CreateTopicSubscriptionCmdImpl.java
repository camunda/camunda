package org.camunda.tngp.client.event.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptionCreationResult;
import org.camunda.tngp.protocol.clientapi.EventType;

public class CreateTopicSubscriptionCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriberEvent, EventSubscriptionCreationResult>
{
    protected final TopicSubscriberEvent subscription = new TopicSubscriberEvent();

    public CreateTopicSubscriptionCmdImpl(final ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, final String topicName, final int partitionId)
    {
        super(cmdExecutor, objectMapper, TopicSubscriberEvent.class, topicName, partitionId, EventType.SUBSCRIBER_EVENT);
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
    protected EventSubscriptionCreationResult getResponseValue(int channelId, long key, TopicSubscriberEvent event)
    {
        return new EventSubscriptionCreationResult(key, channelId);
    }

}
