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
import org.camunda.tngp.client.TopicClient;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.event.WorkflowInstanceEvent;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource
{
    private static final String SUBSCRIPTION_NAME = "event-recorder";

    private final List<ReceivedTaskEvent> taskEvents = new CopyOnWriteArrayList<>();
    private final List<ReceivedWorkflowEvent> wfEvents = new CopyOnWriteArrayList<>();

    private final ClientRule clientRule;
    private final String topicName;
    private final int partitionId;

    protected TopicSubscription subscription;

    public TopicEventRecorder(final ClientRule clientRule)
    {
        this(clientRule, DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public TopicEventRecorder(final ClientRule clientRule, final String topicName, final int partitionId)
    {
        this.clientRule = clientRule;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    protected void before() throws Throwable
    {
        final TopicClient client = clientRule.getClient().topic(topicName, partitionId);

        subscription = client.newSubscription()
            .name(SUBSCRIPTION_NAME)
            .taskEventHandler((m, e) -> taskEvents.add(new ReceivedTaskEvent(m, e)))
            .workflowInstanceEventHandler((m, e) -> wfEvents.add(new ReceivedWorkflowEvent(m, e)))
            .open();
    }

    @Override
    protected void after()
    {
        subscription.close();
    }

    public boolean hasWorkflowEvent(final Predicate<ReceivedWorkflowEvent> matcher)
    {
        return wfEvents.stream().anyMatch(matcher);
    }

    public List<ReceivedWorkflowEvent> getWorkflowEvents(final Predicate<ReceivedWorkflowEvent> matcher)
    {
        return wfEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public ReceivedWorkflowEvent getSingleWorkflowEvent(final Predicate<ReceivedWorkflowEvent> matcher)
    {
        return wfEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public boolean hasTaskEvent(final Predicate<ReceivedTaskEvent> matcher)
    {
        return taskEvents.stream().anyMatch(matcher);
    }

    public List<ReceivedTaskEvent> getTaskEvents(final Predicate<ReceivedTaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public ReceivedTaskEvent getSingleTaskEvent(final Predicate<ReceivedTaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public static Predicate<ReceivedWorkflowEvent> wfEvent(final String type)
    {
        return e -> e.getEvent().getEventType().equals(type);
    }

    public static Predicate<ReceivedTaskEvent> taskEvent(final String type)
    {
        return e -> e.getEvent().getEventType().equals(type);
    }

    public static Predicate<ReceivedTaskEvent> taskType(final String type)
    {
        return e -> e.getEvent().getType().equals(type);
    }

    public static Predicate<ReceivedTaskEvent> taskRetries(final int retries)
    {
        return e -> e.getEvent().getRetries() == retries;
    }

    public class ReceivedTaskEvent
    {
        private final EventMetadata metadata;
        private final TaskEvent event;

        ReceivedTaskEvent(EventMetadata metadata, TaskEvent event)
        {
            this.metadata = metadata;
            this.event = event;
        }

        public EventMetadata getMetadata()
        {
            return metadata;
        }

        public TaskEvent getEvent()
        {
            return event;
        }
    }

    public class ReceivedWorkflowEvent
    {
        private final EventMetadata metadata;
        private final WorkflowInstanceEvent event;

        ReceivedWorkflowEvent(EventMetadata metadata, WorkflowInstanceEvent event)
        {
            this.metadata = metadata;
            this.event = event;
        }

        public EventMetadata getMetadata()
        {
            return metadata;
        }

        public WorkflowInstanceEvent getEvent()
        {
            return event;
        }
    }
}
