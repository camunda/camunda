package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CloseTopicSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<CloseSubscriptionRequest>
{

    protected CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    public CloseTopicSubscriptionCmdImpl(final ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, final String topicName, final int partitionId)
    {
        super(cmdExecutor, objectMapper, CloseSubscriptionRequest.class, ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
        request.setTopicName(topicName);
        request.setPartitionId(partitionId);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("partitionId", request.getPartitionId(), 0);
        EnsureUtil.ensureGreaterThanOrEqual("subscriberKey", request.getSubscriberKey(), 0);
    }

    public CloseTopicSubscriptionCmdImpl subscriberKey(final long subscriberKey)
    {
        this.request.setSubscriberKey(subscriberKey);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        return request;
    }

    @Override
    protected void reset()
    {
        request.reset();
    }

}
