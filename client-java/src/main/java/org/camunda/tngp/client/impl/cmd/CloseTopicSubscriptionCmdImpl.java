package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CloseTopicSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<CloseSubscriptionRequest>
{

    protected CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    public CloseTopicSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, int topicId)
    {
        super(cmdExecutor, objectMapper, CloseSubscriptionRequest.class, ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
        request.setTopicId(topicId);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topicId", request.getTopicId(), 0);
        EnsureUtil.ensureGreaterThanOrEqual("subscriberKey", request.getSubscriberKey(), 0);
    }

    public CloseTopicSubscriptionCmdImpl subscriberKey(long subscriberKey)
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
