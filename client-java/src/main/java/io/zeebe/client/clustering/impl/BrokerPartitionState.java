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
package io.zeebe.client.clustering.impl;

public class BrokerPartitionState
{

    protected String topicName;
    protected int partitionId;
    private String state;

    public BrokerPartitionState setTopicName(final String topicName)
    {
        this.topicName = topicName;
        return this;
    }

    public BrokerPartitionState setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public String getState()
    {
        return state;
    }

    public BrokerPartitionState setState(String state)
    {
        this.state = state;
        return this;
    }

    @Override
    public String toString()
    {
        return "BrokerPartitionState{" + "topicName='" + topicName + '\'' + ", partitionId=" + partitionId + ", state='" + state + '\'' + '}';
    }
}
