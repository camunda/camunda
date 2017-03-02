package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CloseTopicSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<CloseSubscriptionRequest>
{

    protected CloseSubscriptionRequest subscription = new CloseSubscriptionRequest();

    public CloseTopicSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper)
    {
        super(cmdExecutor, objectMapper, CloseSubscriptionRequest.class, ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("id", subscription.getSubscriptionId(), 0);
    }

    public CloseTopicSubscriptionCmdImpl id(long id)
    {
        this.subscription.setSubscriptionId(id);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        return subscription;
    }

    @Override
    protected void reset()
    {
        subscription.reset();
    }

}
