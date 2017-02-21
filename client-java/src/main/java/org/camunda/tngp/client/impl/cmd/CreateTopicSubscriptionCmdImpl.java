package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTopicSubscriptionCmdImpl extends AbstractControlMessageCmd<TopicSubscription, Long>
{
    protected TopicSubscription subscription = new TopicSubscription();

    public CreateTopicSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper)
    {
        super(cmdExecutor, objectMapper, TopicSubscription.class, ControlMessageType.ADD_TOPIC_SUBSCRIPTION, TopicSubscription::getId);
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topicId", subscription.getTopicId(), 0);
    }

    public CreateTopicSubscriptionCmdImpl topicId(int topicId)
    {
        this.subscription.setTopicId(topicId);
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
