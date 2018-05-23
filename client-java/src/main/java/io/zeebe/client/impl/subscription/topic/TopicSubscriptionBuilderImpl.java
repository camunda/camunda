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

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.RecordType;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep2;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.client.impl.command.*;
import io.zeebe.client.impl.event.*;
import io.zeebe.client.impl.record.*;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilderStep1, TopicSubscriptionBuilderStep2, TopicSubscriptionBuilderStep3
{
    private RecordHandler defaultRecordHandler;

    private static final BiEnumMap<RecordType, ValueType, Class<? extends RecordImpl>> RECORD_CLASSES =
            new BiEnumMap<>(RecordType.class, ValueType.class, Class.class);
    static
    {
        RECORD_CLASSES.put(RecordType.COMMAND, ValueType.JOB, JobCommandImpl.class);
        RECORD_CLASSES.put(RecordType.EVENT, ValueType.JOB, JobEventImpl.class);

        RECORD_CLASSES.put(RecordType.COMMAND, ValueType.INCIDENT, IncidentCommandImpl.class);
        RECORD_CLASSES.put(RecordType.EVENT, ValueType.INCIDENT, IncidentEventImpl.class);

        RECORD_CLASSES.put(RecordType.EVENT, ValueType.RAFT, RaftEventImpl.class);

        RECORD_CLASSES.put(RecordType.COMMAND, ValueType.TOPIC, TopicCommandImpl.class);
        RECORD_CLASSES.put(RecordType.EVENT, ValueType.TOPIC, TopicEventImpl.class);

        RECORD_CLASSES.put(RecordType.COMMAND, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceCommandImpl.class);
        RECORD_CLASSES.put(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceEventImpl.class);

        for (ValueType valueType : ValueType.values())
        {
            final Class<? extends RecordImpl> commandClass = RECORD_CLASSES.get(RecordType.COMMAND, valueType);
            RECORD_CLASSES.put(RecordType.COMMAND_REJECTION, valueType, commandClass);
        }
    }

    private BiEnumMap<RecordType, ValueType, CheckedConsumer<RecordImpl>> handlers =
            new BiEnumMap<>(RecordType.class, ValueType.class, CheckedConsumer.class);

    private final TopicSubscriberGroupBuilder builder;

    public TopicSubscriptionBuilderImpl(TopicClientImpl client)
    {
        final int bufferSize = client.getConfiguration().getDefaultTopicSubscriptionBufferSize();
        this.builder = new TopicSubscriberGroupBuilder(client.getTopic(), client.getSubscriptionManager(),
                bufferSize);
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
        handlers.put(RecordType.EVENT, ValueType.JOB, e -> handler.onJobEvent((JobEvent) e));

        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 jobCommandHandler(JobCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("jobCommandHandler", handler);
        handlers.put(RecordType.COMMAND, ValueType.JOB, e -> handler.onJobCommand((JobCommand) e));
        handlers.put(RecordType.COMMAND_REJECTION, ValueType.JOB, e -> handler.onJobCommandRejection((JobCommand) e));

        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 workflowInstanceEventHandler(WorkflowInstanceEventHandler handler)
    {
        EnsureUtil.ensureNotNull("workflowInstanceEventHandler", handler);
        handlers.put(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, e -> handler.onWorkflowInstanceEvent((WorkflowInstanceEvent) e));
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 workflowInstanceCommandHandler(WorkflowInstanceCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("workflowInstanceCommandHandler", handler);
        handlers.put(RecordType.COMMAND, ValueType.WORKFLOW_INSTANCE, e -> handler.onWorkflowInstanceCommand((WorkflowInstanceCommand) e));
        handlers.put(RecordType.COMMAND_REJECTION, ValueType.WORKFLOW_INSTANCE, e -> handler.onWorkflowInstanceCommand((WorkflowInstanceCommand) e));
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 incidentEventHandler(IncidentEventHandler handler)
    {
        EnsureUtil.ensureNotNull("incidentEventHandler", handler);
        handlers.put(RecordType.EVENT, ValueType.INCIDENT, e -> handler.onIncidentEvent((IncidentEvent) e));
        return this;
    }

    @Override
    public TopicSubscriptionBuilderStep3 incidentCommandHandler(IncidentCommandHandler handler)
    {
        EnsureUtil.ensureNotNull("incidentCommandHandler", handler);
        handlers.put(RecordType.COMMAND, ValueType.INCIDENT, e -> handler.onIncidentCommand((IncidentCommand) e));
        handlers.put(RecordType.COMMAND_REJECTION, ValueType.INCIDENT, e -> handler.onIncidentCommand((IncidentCommand) e));
        return this;
    }

    @Override
    public TopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler handler)
    {
        EnsureUtil.ensureNotNull("raftEventHandler", handler);
        handlers.put(RecordType.EVENT, ValueType.RAFT, e -> handler.onRaftEvent((RaftEvent) e));
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
    public TopicSubscriptionBuilderStep3 bufferSize(int numberOfRecords)
    {
        builder.bufferSize(numberOfRecords);
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
        builder.handler(this::dispatchRecord);

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    protected <T extends RecordImpl> void dispatchRecord(UntypedRecordImpl record) throws Exception
    {
        final RecordMetadataImpl metadata = record.getMetadata();
        final ValueType valueType = metadata.getValueType();
        final RecordType recordType = metadata.getRecordType();

        final Class<T> targetClass = (Class<T>) RECORD_CLASSES.get(recordType, valueType);

        if (targetClass == null)
        {
            throw new ClientException("Cannot deserialize record " + recordType + "/" + valueType + ": No POJO class registered");
        }

        final T typedRecord = record.asRecordType(targetClass);
        typedRecord.updateMetadata(record.getMetadata());

        final CheckedConsumer<T> handler = (CheckedConsumer<T>) handlers.get(recordType, valueType);

        if (handler != null)
        {
            handler.accept(typedRecord);
        }
        else if (defaultRecordHandler != null)
        {
            defaultRecordHandler.onRecord(typedRecord);
        }
    }

}
