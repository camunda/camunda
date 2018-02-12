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
package io.zeebe.client.event.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.IncidentEventHandler;
import io.zeebe.client.event.RaftEventHandler;
import io.zeebe.client.event.TaskEventHandler;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.event.UniversalEventHandler;
import io.zeebe.client.event.WorkflowEventHandler;
import io.zeebe.client.event.WorkflowInstanceEventHandler;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.impl.subscription.SubscriptionManager;
import io.zeebe.client.workflow.impl.WorkflowInstanceEventImpl;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilder
{
    protected UniversalEventHandler defaultEventHandler;
    protected TaskEventHandler taskEventHandler;
    protected WorkflowInstanceEventHandler wfInstanceEventHandler;
    protected WorkflowEventHandler wfEventHandler;
    protected IncidentEventHandler incidentEventHandler;
    protected RaftEventHandler raftEventHandler;

    protected final TopicSubscriberGroupBuilder builder;
    protected final MsgPackMapper msgPackMapper;

    public TopicSubscriptionBuilderImpl(
            String topic,
            SubscriptionManager acquisition,
            MsgPackMapper msgPackMapper,
            int prefetchCapacity)
    {
        builder = new TopicSubscriberGroupBuilder(topic, acquisition, prefetchCapacity);
        this.msgPackMapper = msgPackMapper;
    }

    @Override
    public TopicSubscriptionBuilder handler(UniversalEventHandler handler)
    {
        this.defaultEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder taskEventHandler(TaskEventHandler handler)
    {
        this.taskEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder workflowInstanceEventHandler(WorkflowInstanceEventHandler handler)
    {
        this.wfInstanceEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder workflowEventHandler(WorkflowEventHandler handler)
    {
        this.wfEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilder incidentEventHandler(IncidentEventHandler handler)
    {
        this.incidentEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler raftEventHandler)
    {
        this.raftEventHandler = raftEventHandler;
        return this;
    }

    @Override
    public TopicSubscription open()
    {
        final Future<TopicSubscriberGroup> subscription = buildSubscriberGroup();

        try
        {
            return subscription.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new ClientException("Could not open subscriber group", e);
        }
    }

    public Future<TopicSubscriberGroup> buildSubscriberGroup()
    {
        EnsureUtil.ensureNotNull("name", builder.getName());
        if (defaultEventHandler == null && taskEventHandler == null && wfEventHandler == null && wfInstanceEventHandler == null && incidentEventHandler == null
                && raftEventHandler == null)
        {
            throw new ClientException("at least one handler must be set");
        }

        builder.handler(this::dispatchEvent);

        return builder.build();
    }

    protected void dispatchEvent(GeneralEventImpl event) throws Exception
    {
        final TopicEventType eventType = event.getMetadata().getType();

        if (TopicEventType.TASK == eventType && taskEventHandler != null)
        {
            final TaskEventImpl taskEvent = msgPackMapper.convert(event.getAsMsgPack(), TaskEventImpl.class);
            taskEvent.updateMetadata(event.getMetadata());
            taskEventHandler.handle(taskEvent);
        }
        else if (TopicEventType.WORKFLOW_INSTANCE == eventType && wfInstanceEventHandler != null)
        {
            final WorkflowInstanceEventImpl wfInstanceEvent = msgPackMapper.convert(event.getAsMsgPack(), WorkflowInstanceEventImpl.class);
            wfInstanceEvent.updateMetadata(event.getMetadata());
            wfInstanceEventHandler.handle(wfInstanceEvent);
        }
        else if (TopicEventType.WORKFLOW == eventType && wfEventHandler != null)
        {
            final WorkflowEventImpl wfEvent = msgPackMapper.convert(event.getAsMsgPack(), WorkflowEventImpl.class);
            wfEvent.updateMetadata(event.getMetadata());
            wfEventHandler.handle(wfEvent);
        }
        else if (TopicEventType.INCIDENT == eventType && incidentEventHandler != null)
        {
            final IncidentEventImpl incidentEvent = msgPackMapper.convert(event.getAsMsgPack(), IncidentEventImpl.class);
            incidentEvent.updateMetadata(event.getMetadata());
            incidentEventHandler.handle(incidentEvent);
        }
        else if (TopicEventType.RAFT == eventType && raftEventHandler != null)
        {
            final RaftEventImpl raftEvent = msgPackMapper.convert(event.getAsMsgPack(), RaftEventImpl.class);
            raftEvent.updateMetadata(event.getMetadata());
            raftEventHandler.handle(raftEvent);
        }
        else if (defaultEventHandler != null)
        {
            defaultEventHandler.handle(event);
        }
    }

    @Override
    public TopicSubscriptionBuilder startAtPosition(int partitionId, long position)
    {
        builder.startPosition(partitionId, position);
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtTailOfTopic()
    {
        builder.startAtTailOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtHeadOfTopic()
    {
        builder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder name(String name)
    {
        builder.name(name);
        return this;
    }

    @Override
    public TopicSubscriptionBuilder forcedStart()
    {
        builder.forceStart();
        return this;
    }
}
