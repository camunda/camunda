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
package io.zeebe.client.impl.subscription.topic;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.api.record.RecordMetadata.RecordType;
import io.zeebe.client.api.record.RecordMetadata.ValueType;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep2;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.client.impl.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.command.*;
import io.zeebe.client.impl.event.*;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.client.impl.record.RecordMetadataImpl;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilderStep1, TopicSubscriptionBuilderStep2, TopicSubscriptionBuilderStep3
{
    private RecordHandler defaultRecordHandler;
    private JobEventHandler jobEventHandler;
    private JobCommandHandler jobCommandHandler;
    private WorkflowInstanceEventHandler workflowInstanceEventHandler;
    private WorkflowInstanceCommandHandler workflowInstanceCommandHandler;
    private IncidentEventHandler incidentEventHandler;
    private IncidentCommandHandler incidentCommandHandler;
    private RaftEventHandler raftEventHandler;

    private final TopicSubscriberGroupBuilder builder;
    private final ZeebeObjectMapperImpl objectMapper;

    public TopicSubscriptionBuilderImpl(TopicClientImpl client)
    {
        this.objectMapper = client.getObjectMapper();

        final int prefetchCapacity = client.getConfiguration().getTopicSubscriptionPrefetchCapacity();
        this.builder = new TopicSubscriberGroupBuilder(client.getTopic(), client.getSubscriptionManager(),
                prefetchCapacity);
    }

    @Override
    public TopicSubscriptionBuilderStep3 recordHandler(RecordHandler handler)
    {
        EnsureUtil.ensureNotNull("recordHandler", handler);
        this.defaultRecordHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 jobEventHandler(JobEventHandler handler)
    {
        EnsureUtil.ensureNotNull("jobEventHandler", handler);
        this.jobEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 jobCommandHandler(JobCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("jobCommandHandler", handler);
        this.jobCommandHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 workflowInstanceEventHandler(WorkflowInstanceEventHandler handler)
    {
        EnsureUtil.ensureNotNull("workflowInstanceEventHandler", handler);
        this.workflowInstanceEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 workflowInstanceCommandHandler(WorkflowInstanceCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("workflowInstanceCommandHandler", handler);
        this.workflowInstanceCommandHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 incidentEventHandler(IncidentEventHandler handler)
    {
        EnsureUtil.ensureNotNull("incidentEventHandler", handler);
        this.incidentEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 incidentCommandHandler(IncidentCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("incidentCommandHandler", handler);
        this.incidentCommandHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler handler)
    {
        EnsureUtil.ensureNotNull("raftEventHandler", handler);
        this.raftEventHandler = handler;
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 startAtPosition(int partitionId, long position)
    {
        builder.startPosition(partitionId, position);
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 startAtTailOfTopic()
    {
        builder.startAtTailOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 startAtHeadOfTopic()
    {
        builder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 name(String name)
    {
        EnsureUtil.ensureNotNull("name", name);
        builder.name(name);
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 forcedStart()
    {
        builder.forceStart();
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
        builder.handler(this::dispatchEvent);

        return builder.build();
    }

    protected void dispatchEvent(GeneralRecordImpl event) throws Exception
    {
        final RecordMetadataImpl metadata = event.getMetadata();
        final ValueType valueType = metadata.getValueType();
        final RecordType recordType = metadata.getRecordType();
        boolean handled = false;

        // TODO: refactor this to make it easier extensible
        if (ValueType.JOB == valueType)
        {
            if (RecordType.EVENT == recordType && jobEventHandler != null)
            {
                final JobEventImpl jobEvent = objectMapper.fromJson(event.getAsMsgPack(), JobEventImpl.class);
                jobEvent.updateMetadata(event.getMetadata());

                jobEventHandler.onJobEvent(jobEvent);
                handled = true;
            }
            else if (RecordType.COMMAND == recordType && jobCommandHandler != null)
            {
                final JobCommandImpl jobCommand = objectMapper.fromJson(event.getAsMsgPack(), JobCommandImpl.class);
                jobCommand.updateMetadata(event.getMetadata());

                jobCommandHandler.onJobCommand(jobCommand);
                handled = true;
            }
            else if (RecordType.COMMAND_REJECTION == recordType && jobCommandHandler != null)
            {
                final JobCommandImpl jobCommand = objectMapper.fromJson(event.getAsMsgPack(), JobCommandImpl.class);
                jobCommand.updateMetadata(event.getMetadata());

                jobCommandHandler.onJobCommandRejection(jobCommand);
                handled = true;
            }
        }
        else if (ValueType.WORKFLOW_INSTANCE == valueType)
        {
            if (RecordType.EVENT == recordType && workflowInstanceEventHandler != null)
            {
                final WorkflowInstanceEventImpl workflowInstanceEvent = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceEventImpl.class);
                workflowInstanceEvent.updateMetadata(event.getMetadata());

                workflowInstanceEventHandler.onWorkflowInstanceEvent(workflowInstanceEvent);
                handled = true;
            }
            else if (RecordType.COMMAND == recordType && workflowInstanceCommandHandler != null)
            {
                final WorkflowInstanceCommandImpl workflowInstanceCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceCommandImpl.class);
                workflowInstanceCommand.updateMetadata(event.getMetadata());

                workflowInstanceCommandHandler.onWorkflowInstanceCommand(workflowInstanceCommand);
                handled = true;
            }
            else if (RecordType.COMMAND_REJECTION == recordType && workflowInstanceCommandHandler != null)
            {
                final WorkflowInstanceCommandImpl workflowInstanceCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceCommandImpl.class);
                workflowInstanceCommand.updateMetadata(event.getMetadata());

                workflowInstanceCommandHandler.onWorkflowInstanceCommandRejection(workflowInstanceCommand);
                handled = true;
            }
        }
        else if (ValueType.INCIDENT == valueType)
        {
            if (RecordType.EVENT == recordType && incidentEventHandler != null)
            {
                final IncidentEventImpl incidentEvent = objectMapper.fromJson(event.getAsMsgPack(), IncidentEventImpl.class);
                incidentEvent.updateMetadata(event.getMetadata());

                incidentEventHandler.onIncidentEvent(incidentEvent);
                handled = true;
            }
            else if (RecordType.COMMAND == recordType && incidentCommandHandler != null)
            {
                final IncidentCommandImpl incidentCommand = objectMapper.fromJson(event.getAsMsgPack(), IncidentCommandImpl.class);
                incidentCommand.updateMetadata(event.getMetadata());

                incidentCommandHandler.onIncidentCommand(incidentCommand);
                handled = true;
            }
            else if (RecordType.COMMAND_REJECTION == recordType && jobCommandHandler != null)
            {
                final IncidentCommandImpl incidentCommand = objectMapper.fromJson(event.getAsMsgPack(), IncidentCommandImpl.class);
                incidentCommand.updateMetadata(event.getMetadata());

                incidentCommandHandler.onIncidentCommandRejection(incidentCommand);
                handled = true;
            }
        }
        else if (ValueType.RAFT == valueType)
        {
            if (RecordType.EVENT == recordType && raftEventHandler != null)
            {
                final RaftEventImpl raftEvent = objectMapper.fromJson(event.getAsMsgPack(), RaftEventImpl.class);
                raftEvent.updateMetadata(event.getMetadata());

                raftEventHandler.onRaftEvent(raftEvent);
                handled = true;
            }
        }


        if (!handled && defaultRecordHandler != null)
        {
            defaultRecordHandler.onRecord(event);
        }
    }

}
