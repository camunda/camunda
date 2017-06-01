package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.Topic;


public interface TopicCommand
{

    String getTopicName();

    int getPartitionId();

    default Topic getTopic()
    {
        return new Topic(getTopicName(), getPartitionId());
    }

}
