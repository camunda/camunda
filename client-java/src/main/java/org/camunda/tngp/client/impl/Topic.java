package org.camunda.tngp.client.impl;

public class Topic
{

    private final String topicName;
    private final int partitionId;

    public Topic(final String topicName, final int partitionId)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Topic topic = (Topic) o;

        if (partitionId != topic.partitionId)
        {
            return false;
        }
        return topicName != null ? topicName.equals(topic.topicName) : topic.topicName == null;
    }

    @Override
    public int hashCode()
    {
        int result = topicName != null ? topicName.hashCode() : 0;
        result = 31 * result + partitionId;
        return result;
    }

    @Override
    public String toString()
    {
        return "Topic{" +
            "topicName='" + topicName + '\'' +
            ", partitionId=" + partitionId +
            '}';
    }

}
