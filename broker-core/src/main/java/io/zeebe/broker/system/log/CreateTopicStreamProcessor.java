/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.log;

import java.util.Iterator;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.management.Partition;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriterImpl;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.snapshot.ComposedZbMapSnapshot;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.DeferredCommandContext;

public class CreateTopicStreamProcessor implements StreamProcessor
{
    protected final PartitionManager partitionManager;
    protected LogStreamReader sourceLogReader;

    protected final TopicsIndex topics;
    protected final PartitionsIndex partitions;

    protected ResolvePendingPartitionsCommand resolvePartitionsCommand;

    // --- TODO: below: things that should not be managed in a stream processor
    //   => https://github.com/zeebe-io/zeebe/issues/411
    protected final BrokerEventMetadata sourceMetadata = new BrokerEventMetadata();
    protected final TopicEvent topicEvent = new TopicEvent();
    protected final PartitionEvent partitionEvent = new PartitionEvent();

    protected final BrokerEventMetadata targetMetadata = new BrokerEventMetadata()
            .protocolVersion(Protocol.PROTOCOL_VERSION);

    protected LogStreamBatchWriter logStreamBatchWriter;
    protected LoggedEvent currentEvent;
    protected LogStream sourceStream;
    protected LogStream targetStream;
    protected final CommandResponseWriter responseWriter;
    protected int streamProcessorId;

    protected CreateTopicProcessor createTopicProcessor = new CreateTopicProcessor();
    protected CreatePartitionProcessor createPartitionProcessor = new CreatePartitionProcessor();
    protected CompletePartitionProcessor completePartitionProcessor = new CompletePartitionProcessor();
    protected PartitionCreatedProcessor partitionCreatedProcessor = new PartitionCreatedProcessor();

    protected final ComposedZbMapSnapshot snapshotSupport;

    protected DeferredCommandContext commandQueue;
    // ---

    public CreateTopicStreamProcessor(CommandResponseWriter responseWriter, PartitionManager partitionManager)
    {
        this.responseWriter = responseWriter;
        this.partitionManager = partitionManager;

        this.topics = new TopicsIndex();
        this.partitions = new PartitionsIndex();
        this.snapshotSupport = new ComposedZbMapSnapshot(topics.getStateResource(), partitions.getStateResource());
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.sourceStream = context.getSourceStream();
        this.targetStream = context.getTargetStream();
        this.logStreamBatchWriter = new LogStreamBatchWriterImpl(targetStream);
        this.streamProcessorId = context.getId();
        this.sourceLogReader = new BufferedLogStreamReader(sourceStream);
        this.commandQueue = context.getStreamProcessorCmdQueue();
        this.resolvePartitionsCommand = new ResolvePendingPartitionsCommand();
        this.topics.put(Protocol.SYSTEM_TOPIC_BUF, 0, -1); // ensure that the system topic cannot be created
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotSupport;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent currentEvent)
    {
        this.currentEvent = currentEvent;

        sourceMetadata.reset();
        partitionEvent.reset();
        currentEvent.readMetadata(sourceMetadata);

        if (EventType.TOPIC_EVENT == sourceMetadata.getEventType())
        {
            topicEvent.reset();
            currentEvent.readValue(topicEvent);

            if (TopicState.CREATE == topicEvent.getState())
            {
                return createTopicProcessor;
            }
            else
            {
                return null;
            }
        }
        else if (EventType.PARTITION_EVENT == sourceMetadata.getEventType())
        {
            partitionEvent.reset();
            currentEvent.readValue(partitionEvent);

            if (PartitionState.CREATE == partitionEvent.getState())
            {
                return createPartitionProcessor;
            }
            else if (PartitionState.CREATE_COMPLETE == partitionEvent.getState())
            {
                return completePartitionProcessor;
            }
            else if (PartitionState.CREATED == partitionEvent.getState())
            {
                return partitionCreatedProcessor;
            }
            else
            {
                return null;
            }
        }
        else
        {
            return null;
        }
    }

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TOPIC_EVENT ||
                m.getEventType() == EventType.PARTITION_EVENT;
    }

    public void checkPendingPartitionsAsync()
    {
        if (resolvePartitionsCommand != null)
        {
            commandQueue.runAsync(resolvePartitionsCommand);
        }
    }

    protected class CreateTopicProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            final DirectBuffer nameBuffer = topicEvent.getName();
            final boolean topicExists = topics.moveTo(nameBuffer);

            if (topicExists)
            {
                topicEvent.setState(TopicState.CREATE_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            if (topicEvent.getState() == TopicState.CREATE_REJECTED)
            {
                return responseWriter
                    .topicName(sourceStream.getTopicName())
                    .partitionId(sourceStream.getPartitionId())
                    .position(currentEvent.getPosition())
                    .key(currentEvent.getKey())
                    .eventWriter(topicEvent)
                    .tryWriteResponse(sourceMetadata.getRequestStreamId(), sourceMetadata.getRequestId());
            }
            else
            {
                return true;
            }

        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetMetadata
                .raftTermId(targetStream.getTerm());

            if (topicEvent.getState() == TopicState.CREATE_REJECTED)
            {
                targetMetadata
                    .eventType(EventType.TOPIC_EVENT);

                return writer
                    .key(currentEvent.getKey())
                    .metadataWriter(targetMetadata)
                    .valueWriter(topicEvent)
                    .tryWrite();
            }
            else
            {

                targetMetadata
                    .eventType(EventType.PARTITION_EVENT);

                logStreamBatchWriter.reset();

                logStreamBatchWriter
                    .producerId(streamProcessorId)
                    .sourceEvent(sourceStream.getTopicName(), sourceStream.getPartitionId(), currentEvent.getPosition());

                for (int i = 0; i < topicEvent.getPartitions(); i++)
                {
                    partitionEvent.reset();
                    partitionEvent.setState(PartitionState.CREATE);
                    partitionEvent.setTopicName(topicEvent.getName());
                    partitionEvent.setId(i);

                    logStreamBatchWriter.event()
                        .positionAsKey()
                        .metadataWriter(targetMetadata)
                        .valueWriter(partitionEvent)
                        .done();
                }

                return logStreamBatchWriter.tryWrite();
            }
        }

        @Override
        public void updateState()
        {
            final DirectBuffer nameBuffer = topicEvent.getName();

            topics.put(nameBuffer, topicEvent.getPartitions(), currentEvent.getPosition());
        }

    }

    protected class CreatePartitionProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            partitionEvent.setState(PartitionState.CREATING);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetMetadata.raftTermId(targetStream.getTerm());
            targetMetadata.eventType(EventType.PARTITION_EVENT);

            return writer
                .key(currentEvent.getKey())
                .metadataWriter(targetMetadata)
                .valueWriter(partitionEvent)
                .tryWrite();
        }

        @Override
        public boolean executeSideEffects()
        {
            partitionManager.createPartitionAsync(partitionEvent.getTopicName(), partitionEvent.getId());

            return true;
        }

        @Override
        public void updateState()
        {
            partitions.putPartitionKey(
                    partitionEvent.getTopicName(),
                    partitionEvent.getId(),
                    currentEvent.getKey());
        }
    }

    protected class CompletePartitionProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final DirectBuffer topicName = partitionEvent.getTopicName();
            final long pendingPartitionKey = partitions.getPartitionKey(topicName, partitionEvent.getId());

            if (pendingPartitionKey >= 0)
            {
                partitionEvent.setState(PartitionState.CREATED);
            }
            else
            {
                partitionEvent.setState(PartitionState.CREATE_COMPLETE_REJECTED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetMetadata.raftTermId(targetStream.getTerm());
            targetMetadata.eventType(EventType.PARTITION_EVENT);

            return writer
                .key(currentEvent.getKey())
                .metadataWriter(targetMetadata)
                .valueWriter(partitionEvent)
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            final DirectBuffer topicName = partitionEvent.getTopicName();

            if (partitionEvent.getState() == PartitionState.CREATED)
            {
                topics.moveTo(topicName);
                final int remainingPartitions = topics.getRemainingPartitions();

                if (remainingPartitions > 0)
                {
                    topics.putRemainingPartitions(topicName, remainingPartitions - 1);
                }

                partitions.removePartitionKey(topicName, partitionEvent.getId());
            }

        }
    }

    public class PartitionCreatedProcessor implements EventProcessor
    {
        protected boolean topicCreationComplete = false;
        protected LoggedEvent request;
        protected final BrokerEventMetadata topicEventMetadata = new BrokerEventMetadata();

        @Override
        public void processEvent()
        {
            final DirectBuffer topicName = partitionEvent.getTopicName();
            topics.moveTo(topicName);
            topicCreationComplete = topics.getRemainingPartitions() == 0;

            if (topicCreationComplete)
            {
                final boolean found = sourceLogReader.seek(topics.getRequestPosition());
                if (!found)
                {
                    throw new RuntimeException("Could not find request for topic creation");
                }

                request = sourceLogReader.next();
                request.readMetadata(topicEventMetadata);
                request.readValue(topicEvent);

                topicEvent.setState(TopicState.CREATED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (topicCreationComplete)
            {
                targetMetadata.raftTermId(targetStream.getTerm());
                targetMetadata.eventType(EventType.TOPIC_EVENT);

                return writer.metadataWriter(targetMetadata)
                    .valueWriter(topicEvent)
                    .key(request.getKey())
                    .tryWrite();
            }
            else
            {
                return 0;
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            if (topicCreationComplete)
            {
                return responseWriter
                    .topicName(sourceStream.getTopicName())
                    .partitionId(sourceStream.getPartitionId())
                    .position(currentEvent.getPosition())
                    .key(request.getKey())
                    .eventWriter(topicEvent)
                    .tryWriteResponse(topicEventMetadata.getRequestStreamId(), topicEventMetadata.getRequestId());
            }
            else
            {
                return true;
            }
        }
    }

    public class ResolvePendingPartitionsCommand implements Runnable
    {
        protected final LogStreamWriter writer = new LogStreamWriterImpl(targetStream);

        @Override
        public void run()
        {
            if (partitions.isEmpty())
            {
                // no pending partitions
                return;
            }

            final Iterator<Partition> currentPartitions = partitionManager.getKnownPartitions();
            while (currentPartitions.hasNext())
            {
                final Partition nextPartition = currentPartitions.next();
                final DirectBuffer topicName = nextPartition.getTopicName();
                final int partitionId = nextPartition.getPartitionId();

                final long key = partitions.getPartitionKey(topicName, partitionId);

                if (key >= 0)
                {
                    targetMetadata.raftTermId(targetStream.getTerm());
                    targetMetadata.eventType(EventType.PARTITION_EVENT);

                    partitionEvent.reset();
                    partitionEvent.setTopicName(topicName);
                    partitionEvent.setId(partitionId);
                    partitionEvent.setState(PartitionState.CREATE_COMPLETE);

                    // it is ok if writing fails,
                    // we will then try it again with the next command execution (there are no other side effects)
                    writer.key(key)
                        .metadataWriter(targetMetadata)
                        .valueWriter(partitionEvent)
                        .tryWrite();
                }
            }
        }
    }
}
