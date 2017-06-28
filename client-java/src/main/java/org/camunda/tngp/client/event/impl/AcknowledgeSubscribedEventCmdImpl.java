package org.camunda.tngp.client.event.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.util.EnsureUtil;

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
