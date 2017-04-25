/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.TopicClient;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.AcknowledgeSubscribedEventCmdImpl;
import org.camunda.tngp.client.impl.cmd.CloseTopicSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.CreateTopicSubscriptionCmdImpl;

public class TopicClientImpl implements TopicClient
{
    protected final TngpClientImpl client;
    protected final String topicName;
    protected final int partitionId;

    public TopicClientImpl(TngpClientImpl client, final String topicName, int partitionId)
    {
        this.client = client;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    public TopicSubscriptionBuilder newSubscription()
    {
        return client.getSubscriptionManager().newTopicSubscription(this);
    }

    @Override
    public PollableTopicSubscriptionBuilder newPollableSubscription()
    {
        return client.getSubscriptionManager().newPollableTopicSubscription(this);
    }

    public CreateTopicSubscriptionCmdImpl createTopicSubscription()
    {
        return new CreateTopicSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public CloseTopicSubscriptionCmdImpl closeTopicSubscription()
    {
        return new CloseTopicSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public AcknowledgeSubscribedEventCmdImpl acknowledgeEvent()
    {
        return new AcknowledgeSubscribedEventCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

}
