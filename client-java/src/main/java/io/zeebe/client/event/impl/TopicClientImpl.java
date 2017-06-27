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
package io.zeebe.client.event.impl;

import io.zeebe.client.TopicClient;
import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.ZeebeClientImpl;


public class TopicClientImpl implements TopicClient
{
    protected final ZeebeClientImpl client;
    protected final Topic topic;

    public TopicClientImpl(final ZeebeClientImpl client, final String topicName, final int partitionId)
    {
        this.client = client;
        this.topic = new Topic(topicName, partitionId);
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
        return new CreateTopicSubscriptionCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public CloseTopicSubscriptionCmdImpl closeTopicSubscription()
    {
        return new CloseTopicSubscriptionCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public AcknowledgeSubscribedEventCmdImpl acknowledgeEvent()
    {
        return new AcknowledgeSubscribedEventCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public Topic getTopic()
    {
        return topic;
    }

}
