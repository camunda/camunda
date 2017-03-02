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
    protected final int topicId;

    public TopicClientImpl(TngpClientImpl client, int topicId)
    {
        this.client = client;
        this.topicId = topicId;
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
        return new CreateTopicSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    public CloseTopicSubscriptionCmdImpl closeTopicSubscription()
    {
        return new CloseTopicSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper());
    }

    public AcknowledgeSubscribedEventCmdImpl acknowledgeEvent()
    {
        return new AcknowledgeSubscribedEventCmdImpl(client.getCmdExecutor(), client.getObjectMapper());
    }

}
