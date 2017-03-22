package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AcknowledgeSubscribedEventCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriptionEvent, Long>
{
    protected final TopicSubscriptionEvent ack = new TopicSubscriptionEvent();

    public AcknowledgeSubscribedEventCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, final int topicId)
    {
        super(cmdExecutor, objectMapper, TopicSubscriptionEvent.class, topicId, EventType.SUBSCRIPTION_EVENT);
    }

    @Override
    public void validate()
    {
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
