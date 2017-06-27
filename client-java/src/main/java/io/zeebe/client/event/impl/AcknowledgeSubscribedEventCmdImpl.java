package io.zeebe.client.event.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.EnsureUtil;

public class AcknowledgeSubscribedEventCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriptionEvent, Long>
{
    protected final TopicSubscriptionEvent ack = new TopicSubscriptionEvent();

    public AcknowledgeSubscribedEventCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TopicSubscriptionEvent.class, EventType.SUBSCRIPTION_EVENT);
    }

    @Override
    public void validate()
    {
        super.validate();
        EnsureUtil.ensureGreaterThanOrEqual("ackPosition", ack.getAckPosition(), 0L);
        EnsureUtil.ensureNotNull("subscriptionName", ack.getName());
    }

    public AcknowledgeSubscribedEventCmdImpl subscriptionName(String name)
    {
        this.ack.setName(name);
        return this;
    }

    public AcknowledgeSubscribedEventCmdImpl ackPosition(long position)
    {
        this.ack.setAckPosition(position);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        return ack;
    }

    @Override
    protected void reset()
    {
        ack.reset();
    }

    @Override
    protected long getKey()
    {
        return -1L;
    }

    @Override
    protected Long getResponseValue(long key, TopicSubscriptionEvent event)
    {
        return key;
    }

}
