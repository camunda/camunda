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

import org.camunda.tngp.client.EventsClient;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.task.impl.SubscriptionManager;

public class TngpEventsClientImpl implements EventsClient
{
    protected final ClientCmdExecutor commandExecutor;
    protected final SubscriptionManager subscriptionManager;

    public TngpEventsClientImpl(ClientCmdExecutor commandExecutor, SubscriptionManager subscriptionManager)
    {
        this.commandExecutor = commandExecutor;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public TopicSubscriptionBuilder newSubscription(int topicId)
    {
        return subscriptionManager.newTopicSubscription(topicId);
    }

    @Override
    public PollableTopicSubscriptionBuilder newPollableSubscription(int topicId)
    {
        return subscriptionManager.newPollableTopicSubscription(topicId);
    }

    @Override
    public TaskTopicSubscriptionBuilder newTaskTopicSubscription(int topicId)
    {
        return subscriptionManager.newTaskTopicSubscription(topicId);
    }

}
