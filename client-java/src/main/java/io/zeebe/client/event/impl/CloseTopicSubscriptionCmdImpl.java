package io.zeebe.client.event.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.util.EnsureUtil;

public class CloseTopicSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<CloseSubscriptionRequest>
{

    protected CloseSubscriptionRequest request = new CloseSubscriptionRequest();
    protected long subscriberKey;

    public CloseTopicSubscriptionCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, CloseSubscriptionRequest.class, ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
    }

    @Override
    public void validate()
    {
        topic.validate();
        EnsureUtil.ensureGreaterThanOrEqual("subscriberKey", subscriberKey, 0);
    }

    public CloseTopicSubscriptionCmdImpl subscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        request.setTopicName(topic.getTopicName());
        request.setPartitionId(topic.getPartitionId());
        request.setSubscriberKey(subscriberKey);

        return request;
    }

    @Override
    protected void reset()
    {
        request.reset();
    }

}
