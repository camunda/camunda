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
package org.camunda.tngp.broker.it.util;

import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.junit.rules.ExternalResource;

public class RecordingTaskEventHandler extends ExternalResource
{
    private static final TopicEventHandler NOOP_EVENT_HANDLER = (m, e) ->
    { };

    protected String subscriptionName = "recording-handler";

    protected final List<TaskEvent> taskEvents = new CopyOnWriteArrayList<>();

    protected final ClientRule clientRule;
    protected final String topicName;
    protected final int partitionId;

    protected TopicSubscription subscription;

    public RecordingTaskEventHandler(final ClientRule clientRule)
    {
        this(clientRule, DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public RecordingTaskEventHandler(final ClientRule clientRule, final String topicName, final int partitionId)
    {
        this.clientRule = clientRule;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    protected void before() throws Throwable
    {
        final TaskTopicClient client = clientRule.getClient().taskTopic(topicName, partitionId);

        subscription = client.newSubscription()
            .name(subscriptionName)
            .defaultHandler(NOOP_EVENT_HANDLER)
            .taskEventHandler((metadata, taskEvent) -> taskEvents.add(taskEvent))
            .open();
    }

    @Override
    protected void after()
    {
        subscription.close();
    }

    public boolean hasTaskEvent(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().anyMatch(matcher);
    }

    public List<TaskEvent> getTaskEvents(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public static Predicate<TaskEvent> eventType(final TaskEventType type)
    {
        return task -> task.getEventType().equals(type.name());
    }

    public static Predicate<TaskEvent> retries(final int retries)
    {
        return task -> task.getRetries() == retries;
    }

}
