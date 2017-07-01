package io.zeebe.client.impl;

import static io.zeebe.util.EnsureUtil.*;


public class Topic
{

    private String topicName;
    private int partitionId;

    public Topic(final String topicName, final int partitionId)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public Topic setTopicName(final String topicName)
    {
        this.topicName = topicName;
        return this;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public Topic setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public void validate()
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
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
