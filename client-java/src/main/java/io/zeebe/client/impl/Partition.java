/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import static io.zeebe.util.EnsureUtil.*;


public class Partition
{

    private String topicName;
    private int partitionId;

    public Partition(final String topicName, final int partitionId)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public Partition setTopicName(final String topicName)
    {
        this.topicName = topicName;
        return this;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public Partition setPartitionId(final int partitionId)
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

        final Partition topic = (Partition) o;

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
