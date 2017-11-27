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
package io.zeebe.test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.zeebe.client.TopicsClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.WorkflowInstanceEvent;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource
{
    private static final String SUBSCRIPTION_NAME = "event-recorder";

    private final List<TaskEvent> taskEvents = new CopyOnWriteArrayList<>();
    private final List<WorkflowInstanceEvent> wfInstanceEvents = new CopyOnWriteArrayList<>();

    private final ClientRule clientRule;

    private final String topicName;

    protected TopicSubscription subscription;

    public TopicEventRecorder(
            final ClientRule clientRule,
            final String topicName)
    {
        this.clientRule = clientRule;
        this.topicName = topicName;
    }

    @Override
    protected void before() throws Throwable
    {
        startRecordingEvents();
    }

    @Override
    protected void after()
    {
        stopRecordingEvents();
    }

    private void startRecordingEvents()
    {
        final TopicsClient client = clientRule.getClient().topics();

        subscription = client.newSubscription(topicName)
            .name(SUBSCRIPTION_NAME)
            .taskEventHandler(t -> taskEvents.add(t))
            .workflowInstanceEventHandler(wf -> wfInstanceEvents.add(wf))
            .open();
    }

    private void stopRecordingEvents()
    {
        subscription.close();
        subscription = null;
    }

    private <T> Optional<T> getLastEvent(Stream<T> eventStream)
    {
        final List<T> events = eventStream.collect(Collectors.toList());

        if (events.isEmpty())
        {
            return Optional.empty();
        }
        else
        {
            return Optional.of(events.get(events.size() - 1));
        }
    }

    public List<WorkflowInstanceEvent> getWorkflowInstanceEvents(final Predicate<WorkflowInstanceEvent> matcher)
    {
        return wfInstanceEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public WorkflowInstanceEvent getLastWorkflowInstanceEvent(final Predicate<WorkflowInstanceEvent> matcher)
    {
        return getLastEvent(wfInstanceEvents.stream().filter(matcher)).orElseThrow(() -> new AssertionError("no event found"));
    }

    public List<TaskEvent> getTaskEvents(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public TaskEvent getLastTaskEvent(final Predicate<TaskEvent> matcher)
    {
        return getLastEvent(taskEvents.stream().filter(matcher)).orElseThrow(() -> new AssertionError("no event found"));
    }

    public static Predicate<WorkflowInstanceEvent> wfInstance(final String eventType)
    {
        return e -> e.getState().equals(eventType);
    }

    public static Predicate<WorkflowInstanceEvent> wfInstanceKey(final long key)
    {
        return e -> e.getWorkflowInstanceKey() == key;
    }

    public static Predicate<TaskEvent> taskKey(final long key)
    {
        return e -> e.getMetadata().getKey() == key;
    }

    public static Predicate<TaskEvent> taskType(final String type)
    {
        return e -> e.getType().equals(type);
    }

}
