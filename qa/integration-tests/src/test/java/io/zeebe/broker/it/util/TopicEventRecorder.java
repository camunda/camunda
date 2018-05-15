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


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.api.clients.SubscriptionClient;
import io.zeebe.client.api.commands.JobCommand;
import io.zeebe.client.api.commands.JobCommand.JobCommandName;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.events.IncidentEvent.IncidentState;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent.WorkflowInstanceState;
import io.zeebe.client.api.subscription.TopicSubscription;
import org.junit.rules.ExternalResource;

public class TopicEventRecorder extends ExternalResource
{
    private static final String SUBSCRIPTION_NAME = "event-recorder";

    private final List<JobEvent> jobEvents = new CopyOnWriteArrayList<>();
    private final List<JobCommand> jobCommands = new CopyOnWriteArrayList<>();
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
        this(clientRule, clientRule.getDefaultTopic(), autoRecordEvents);
    }

    public TopicEventRecorder(
            final ClientRule clientRule,
            final String topicName,
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
            final SubscriptionClient client = clientRule.getClient().topicClient(topicName).subscriptionClient();

            subscription = client.newTopicSubscription()
                .name(SUBSCRIPTION_NAME)
                .jobEventHandler(e -> jobEvents.add(e))
                .jobCommandHandler(jobCommands::add)
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

    public boolean hasWorkflowInstanceEvent(final WorkflowInstanceState state)
    {
        return wfInstanceEvents.stream().anyMatch(state(state));
    }

    public List<WorkflowInstanceEvent> getWorkflowInstanceEvents(final WorkflowInstanceState state)
    {
        return wfInstanceEvents.stream().filter(state(state)).collect(Collectors.toList());
    }

    public WorkflowInstanceEvent getSingleWorkflowInstanceEvent(WorkflowInstanceState state)
    {
        return wfInstanceEvents.stream().filter(state(state)).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public boolean hasJobEvent(final Predicate<JobEvent> matcher)
    {
        return jobEvents.stream().anyMatch(matcher);
    }

    public boolean hasJobEvent(JobState state)
    {
        return jobEvents.stream().anyMatch(state(state));
    }

    public boolean hasJobCommand(final Predicate<JobCommand> matcher)
    {
        return jobCommands.stream().anyMatch(matcher);
    }

    public List<JobEvent> getJobEvents(JobState state)
    {
        return jobEvents.stream().filter(state(state)).collect(Collectors.toList());
    }

    public List<JobCommand> getJobCommands(final Predicate<JobCommand> matcher)
    {
        return jobCommands.stream().filter(matcher).collect(Collectors.toList());
    }

    public boolean hasIncidentEvent(IncidentState state)
    {
        return incidentEvents.stream().anyMatch(state(state));
    }

    public static Predicate<WorkflowInstanceEvent> state(final WorkflowInstanceState state)
    {
        return e -> e.getState().equals(state);
    }

    public static Predicate<JobEvent> state(final JobState state)
    {
        return e -> e.getState().equals(state);
    }

    public static Predicate<JobCommand> jobCommand(final JobCommandName command)
    {
        return e -> e.getName().equals(command);
    }

    public static Predicate<JobEvent> jobType(final String type)
    {
        return e -> e.getType().equals(type);
    }

    public static Predicate<JobEvent> jobRetries(final int retries)
    {
        return e -> e.getRetries() == retries;
    }

    public static Predicate<IncidentEvent> state(final IncidentState state)
    {
        return e -> e.getState().equals(state);
    }

}
