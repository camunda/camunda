package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AcknowledgeSubscribedEventCmdImpl extends AbstractControlMessageCmd<SubscriptionAcknowledgement, Long>
{
    protected final SubscriptionAcknowledgement ack = new SubscriptionAcknowledgement();

    public AcknowledgeSubscribedEventCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper)
    {
        super(cmdExecutor, objectMapper, SubscriptionAcknowledgement.class, ControlMessageType.ACKNOWLEDGE_TOPIC_EVENT, SubscriptionAcknowledgement::getSubscriptionId);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topicId", ack.getAcknowledgedPosition(), 0L);
        EnsureUtil.ensureGreaterThanOrEqual("subscriptionId", ack.getSubscriptionId(), 0L);
    }

    public AcknowledgeSubscribedEventCmdImpl subscriptionId(long id)
    {
        this.ack.setSubscriptionId(id);
        return this;
    }

    public AcknowledgeSubscribedEventCmdImpl eventPosition(long position)
    {
        this.ack.setAcknowledgedPosition(position);
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

}
