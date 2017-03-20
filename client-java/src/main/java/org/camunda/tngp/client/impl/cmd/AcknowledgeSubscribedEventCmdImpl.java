package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AcknowledgeSubscribedEventCmdImpl extends AbstractExecuteCmdImpl<SubscriptionAcknowledgement, Long>
{
    protected final SubscriptionAcknowledgement ack = new SubscriptionAcknowledgement();

    public AcknowledgeSubscribedEventCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, final int topicId)
    {
        super(cmdExecutor, objectMapper, SubscriptionAcknowledgement.class, topicId, EventType.SUBSCRIPTION_EVENT);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("ackPosition", ack.getAckPosition(), 0L);
        EnsureUtil.ensureNotNull("subscriptionName", ack.getSubscriptionName());
    }

    public AcknowledgeSubscribedEventCmdImpl subscriptionName(String name)
    {
        this.ack.setSubscriptionName(name);
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
    protected Long getResponseValue(long key, SubscriptionAcknowledgement event)
    {
        return key;
    }

}
