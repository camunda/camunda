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
package io.zeebe.broker.it.util;

import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.TopicsClient;
import io.zeebe.client.event.*;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource
{
    private static final String SUBSCRIPTION_NAME = "event-recorder";

    private final List<TaskEvent> taskEvents = new CopyOnWriteArrayList<>();
    private final List<WorkflowEvent> wfEvents = new CopyOnWriteArrayList<>();
    private final List<WorkflowInstanceEvent> wfInstanceEvents = new CopyOnWriteArrayList<>();
    private final List<IncidentEvent> incidentEvents = new CopyOnWriteArrayList<>();

    private final ClientRule clientRule;
    private final String topicName;

    protected TopicSubscription subscription;
    protected final boolean autoRecordEvents;

    public TopicEventRecorder(final ClientRule clientRule)
    {
        this(clientRule, true);
    }


    public TopicEventRecorder(final ClientRule clientRule, boolean autoRecordEvents)
    {
        this(clientRule, DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID, autoRecordEvents);
    }

    public TopicEventRecorder(
            final ClientRule clientRule,
            final String topicName,
            final int partitionId,
            boolean autoRecordEvents)
    {
        this.clientRule = clientRule;
        this.topicName = topicName;
        this.autoRecordEvents = autoRecordEvents;
    }

    @Override
    protected void before() throws Throwable
    {
        if (autoRecordEvents)
        {
            startRecordingEvents();
        }
    }

    @Override
    protected void after()
    {
        stopRecordingEvents();
    }

    public void startRecordingEvents()
    {
        if (subscription == null)
        {
            final TopicsClient client = clientRule.getClient().topics();

            subscription = client.newSubscription(topicName)
                .name(SUBSCRIPTION_NAME)
                .taskEventHandler(e -> taskEvents.add(e))
                .workflowEventHandler(e -> wfEvents.add(e))
                .workflowInstanceEventHandler(e -> wfInstanceEvents.add(e))
                .incidentEventHandler(e -> incidentEvents.add(e))
                .open();
        }
        else
        {
            throw new RuntimeException("Subscription already open");
        }
    }

    public void stopRecordingEvents()
    {
        if (subscription != null)
        {
            subscription.close();
            subscription = null;
        }
    }

    public boolean hasWorkflowInstanceEvent(final Predicate<WorkflowInstanceEvent> matcher)
    {
        return wfInstanceEvents.stream().anyMatch(matcher);
    }

    public List<WorkflowInstanceEvent> getWorkflowInstanceEvents(final Predicate<WorkflowInstanceEvent> matcher)
    {
        return wfInstanceEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public WorkflowInstanceEvent getSingleWorkflowInstanceEvent(final Predicate<WorkflowInstanceEvent> matcher)
    {
        return wfInstanceEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public boolean hasTaskEvent(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().anyMatch(matcher);
    }

    public List<TaskEvent> getTaskEvents(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public TaskEvent getSingleTaskEvent(final Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public boolean hasIncidentEvent(final Predicate<IncidentEvent> matcher)
    {
        return incidentEvents.stream().anyMatch(matcher);
    }

    public List<IncidentEvent> getIncidentEvents(final Predicate<IncidentEvent> matcher)
    {
        return incidentEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public IncidentEvent getSingleIncidentEvent(final Predicate<IncidentEvent> matcher)
    {
        return incidentEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public boolean hasWorkflowEvent(final Predicate<WorkflowEvent> matcher)
    {
        return wfEvents.stream().anyMatch(matcher);
    }

    public List<WorkflowEvent> getWorkflowEvents(final Predicate<WorkflowEvent> matcher)
    {
        return wfEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public WorkflowEvent getSingleWorkflowEvent(final Predicate<WorkflowEvent> matcher)
    {
        return wfEvents.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public static Predicate<WorkflowInstanceEvent> wfInstanceEvent(final String type)
    {
        return e -> e.getState().equals(type);
    }

    public static Predicate<WorkflowEvent> wfEvent(final String type)
    {
        return e -> e.getState().equals(type);
    }

    public static Predicate<TaskEvent> taskEvent(final String type)
    {
        return e -> e.getState().equals(type);
    }

    public static Predicate<TaskEvent> taskType(final String type)
    {
        return e -> e.getType().equals(type);
    }

    public static Predicate<TaskEvent> taskRetries(final int retries)
    {
        return e -> e.getRetries() == retries;
    }

    public static Predicate<IncidentEvent> incidentEvent(final String type)
    {
        return e -> e.getState().equals(type);
    }

}
