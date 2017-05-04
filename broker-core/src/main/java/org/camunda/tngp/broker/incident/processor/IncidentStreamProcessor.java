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
package org.camunda.tngp.broker.incident.processor;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.incident.data.IncidentEvent;
import org.camunda.tngp.broker.incident.data.IncidentEventType;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.snapshot.ComposedSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;

public class IncidentStreamProcessor implements StreamProcessor
{
    private static final int INCIDENT_INSTANCE_INDEX_LENGTH = 2 * SIZE_OF_INT + 4 * SIZE_OF_LONG;

    private final Long2BytesHashIndex incidentInstanceIndex;
    private final Long2LongHashIndex sourcePositionIndex;
    private final SnapshotSupport indexSnapshot;

    private final IncidentInstanceIndexAccessor incidentIndexAccessor = new IncidentInstanceIndexAccessor();

    private final CreateIncidentProcessor createIncidentProcessor = new CreateIncidentProcessor();
    private final ResolveIncidentProcessor resolveIncidentProcessor = new ResolveIncidentProcessor();
    private final ResolveFailedProcessor resolveFailedProcessor = new ResolveFailedProcessor();
    private final ResultEventProcessor resultEventProcessor = new ResultEventProcessor();

    private final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    private final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    private final IncidentEvent incidentEvent = new IncidentEvent();
    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    private final TaskEvent taskEvent = new TaskEvent();

    private long eventKey;
    private long eventPosition;
    private long sourceEventPosition;

    private final CommandResponseWriter responseWriter;

    private LogStreamReader logStreamReader;
    private DirectBuffer logStreamTopicName;
    private int logStreamPartitionId;

    public IncidentStreamProcessor(CommandResponseWriter responseWriter, IndexStore instanceIndexStore, IndexStore positionIndexStore)
    {
        this.responseWriter = responseWriter;
        this.incidentInstanceIndex = new Long2BytesHashIndex(instanceIndexStore, Short.MAX_VALUE, 64, INCIDENT_INSTANCE_INDEX_LENGTH);
        this.sourcePositionIndex = new Long2LongHashIndex(positionIndexStore, Short.MAX_VALUE, 64);

        this.indexSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(incidentInstanceIndex, instanceIndexStore),
                new HashIndexSnapshotSupport<>(sourcePositionIndex, positionIndexStore));
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        final LogStream logStream = context.getSourceStream();

        logStreamTopicName = logStream.getTopicName();
        logStreamPartitionId = logStream.getPartitionId();
        logStreamReader = new BufferedLogStreamReader(logStream);
    }

    public static MetadataFilter eventFilter()
    {
        return event -> event.getEventType() == EventType.INCIDENT_EVENT
                || event.getEventType() == EventType.WORKFLOW_EVENT
                || event.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getKey();
        eventPosition = event.getPosition();
        sourceEventPosition = event.getSourceEventPosition();

        sourceEventMetadata.reset();
        event.readMetadata(sourceEventMetadata);

        EventProcessor eventProcessor = null;
        switch (sourceEventMetadata.getEventType())
        {
            case INCIDENT_EVENT:
                eventProcessor = onIncidentEvent(event);
                break;

            case WORKFLOW_EVENT:
            case TASK_EVENT:
                eventProcessor = resultEventProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    private EventProcessor onIncidentEvent(LoggedEvent event)
    {
        incidentEvent.reset();
        event.readValue(incidentEvent);

        switch (incidentEvent.getEventType())
        {
            case CREATE:
                return createIncidentProcessor;

            case RESOLVE:
                return resolveIncidentProcessor;

            case RESOLVE_FAILED:
                return resolveFailedProcessor;

            default:
                return null;
        }
    }

    private long writeIncidentEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata.eventType(EventType.INCIDENT_EVENT)
            .protocolVersion(Constants.PROTOCOL_VERSION);
        // TODO: targetEventMetadata.raftTermId(raftTermId);

        return writer
                .metadataWriter(targetEventMetadata)
                .valueWriter(incidentEvent)
                .tryWrite();
    }

    private LoggedEvent findEvent(long position)
    {
        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            return logStreamReader.next();
        }
        else
        {
            throw new RuntimeException("event not found");
        }
    }

    private final class CreateIncidentProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            incidentEvent.setEventType(IncidentEventType.CREATED);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeIncidentEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            incidentIndexAccessor.created(eventKey, eventPosition, incidentEvent.getFailureEventPosition());
        }
    }

    private final class ResolveIncidentProcessor implements EventProcessor
    {
        private UnpackedObject resolveEvent;
        private long failureEventKey;
        private long writtenEventPosition;

        @Override
        public void processEvent()
        {
            resolveEvent = null;

            incidentIndexAccessor.wrapIncidentKey(eventKey);

            if (incidentIndexAccessor.getState() == IncidentEventType.CREATED.id() || incidentIndexAccessor.getState() == IncidentEventType.RESOLVE_FAILED.id())
            {
                final LoggedEvent failureEvent = findEvent(incidentIndexAccessor.getFailureEventPosition());
                failureEventKey = failureEvent.getKey();

                targetEventMetadata.reset();
                failureEvent.readMetadata(targetEventMetadata);

                switch (targetEventMetadata.getEventType())
                {
                    case WORKFLOW_EVENT:
                        resolveEvent = resolveWorkflowInstanceEvent(failureEvent);
                        break;

                    case TASK_EVENT:
                        resolveEvent = resolveTaskEvent(failureEvent);
                        break;

                    default:
                        throw new IllegalStateException(String.format("Unexpected failure event of type '%s'.", targetEventMetadata.getEventType()));
                }

                targetEventMetadata.incidentKey(eventKey);
            }
            else
            {
                incidentEvent.setEventType(IncidentEventType.RESOLVE_REJECTED);
            }
        }

        private WorkflowInstanceEvent resolveWorkflowInstanceEvent(LoggedEvent failureEvent)
        {
            workflowInstanceEvent.reset();
            failureEvent.readValue(workflowInstanceEvent);

            return workflowInstanceEvent.setPayload(incidentEvent.getPayload());
        }

        private TaskEvent resolveTaskEvent(LoggedEvent failureEvent)
        {
            taskEvent.reset();
            failureEvent.readValue(taskEvent);

            return taskEvent.setPayload(incidentEvent.getPayload());
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (resolveEvent == null)
            {
                success = responseWriter
                    .brokerEventMetadata(sourceEventMetadata)
                    .topicName(logStreamTopicName)
                    .partitionId(logStreamPartitionId)
                    .key(eventKey)
                    .eventWriter(incidentEvent)
                    .tryWriteResponse();
            }
            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (resolveEvent != null)
            {
                // metadata is read before
                targetEventMetadata.protocolVersion(Constants.PROTOCOL_VERSION);
                // TODO: targetEventMetadata.raftTermId(raftTermId);

                writtenEventPosition = writer
                        .key(failureEventKey)
                        .metadataWriter(targetEventMetadata)
                        .valueWriter(resolveEvent)
                        .tryWrite();
            }
            else
            {
                writtenEventPosition = writeIncidentEvent(writer.key(eventKey));
            }
            return writtenEventPosition;
        }

        @Override
        public void updateState()
        {
            if (resolveEvent != null)
            {
                incidentIndexAccessor.resolve(eventKey, sourceEventMetadata.getReqChannelId(), sourceEventMetadata.getReqConnectionId(), sourceEventMetadata.getReqRequestId());
                sourcePositionIndex.put(writtenEventPosition, eventKey);
            }
        }
    }

    private final class ResolveFailedProcessor implements EventProcessor
    {
        private boolean isFailed;

        @Override
        public void processEvent()
        {
            incidentIndexAccessor.wrapIncidentKey(eventKey);

            isFailed = incidentIndexAccessor.getState() == IncidentEventType.RESOLVE.id();
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isFailed)
            {
                targetEventMetadata.reset()
                    .reqChannelId(incidentIndexAccessor.getChannelId())
                    .reqConnectionId(incidentIndexAccessor.getConnectionId())
                    .reqRequestId(incidentIndexAccessor.getRequestId());

                success = responseWriter
                    .brokerEventMetadata(targetEventMetadata)
                    .topicName(logStreamTopicName)
                    .partitionId(logStreamPartitionId)
                    .key(eventKey)
                    .eventWriter(incidentEvent)
                    .tryWriteResponse();
            }
            return success;
        }

        @Override
        public void updateState()
        {
            if (isFailed)
            {
                incidentIndexAccessor.setState(eventKey, IncidentEventType.RESOLVE_FAILED);
            }
        }
    }

    private final class ResultEventProcessor implements EventProcessor
    {
        private boolean isResolved;
        private long incidentKey;

        @Override
        public void processEvent()
        {
            isResolved = false;

            incidentKey = sourcePositionIndex.get(sourceEventPosition, -1L);

            if (incidentKey > 0)
            {
                incidentIndexAccessor.wrapIncidentKey(incidentKey);

                if (incidentIndexAccessor.getState() == IncidentEventType.RESOLVE.id())
                {
                    final LoggedEvent incidentCreateEvent = findEvent(incidentIndexAccessor.getIncidentEventPosition());

                    incidentEvent.reset();
                    incidentCreateEvent.readValue(incidentEvent);

                    incidentEvent.setEventType(IncidentEventType.RESOLVED);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident index");
                }
            }
            else
            {
                // ignore event
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isResolved)
            {
                targetEventMetadata.reset()
                    .reqChannelId(incidentIndexAccessor.getChannelId())
                    .reqConnectionId(incidentIndexAccessor.getConnectionId())
                    .reqRequestId(incidentIndexAccessor.getRequestId());

                success = responseWriter
                    .brokerEventMetadata(targetEventMetadata)
                    .topicName(logStreamTopicName)
                    .partitionId(logStreamPartitionId)
                    .key(incidentKey)
                    .eventWriter(incidentEvent)
                    .tryWriteResponse();
            }
            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            long position = 0;

            if (isResolved)
            {
                position = writeIncidentEvent(writer.key(incidentKey));
            }
            return position;
        }

        @Override
        public void updateState()
        {
            if (isResolved)
            {
                incidentInstanceIndex.remove(incidentKey);
                sourcePositionIndex.remove(sourceEventPosition, -1L);
            }
        }
    }

    private final class IncidentInstanceIndexAccessor
    {
        private static final int MISSING_VALUE = -1;

        private static final int STATE_OFFSET = 0;
        private static final int INCIDENT_EVENT_POSITION_OFFSET = STATE_OFFSET + SIZE_OF_INT;
        private static final int FAILURE_EVENT_POSITION_OFFSET = INCIDENT_EVENT_POSITION_OFFSET + SIZE_OF_LONG;
        private static final int CHANNEL_ID_OFFSET = FAILURE_EVENT_POSITION_OFFSET + SIZE_OF_LONG;
        private static final int CONNECTION_ID_OFFSET = CHANNEL_ID_OFFSET + SIZE_OF_INT;
        private static final int REQUEST_ID_OFFSET = CONNECTION_ID_OFFSET + SIZE_OF_LONG;

        private final UnsafeBuffer indexValueReadBuffer = new UnsafeBuffer(new byte[INCIDENT_INSTANCE_INDEX_LENGTH]);

        protected boolean isRead = false;

        public void wrapIncidentKey(long key)
        {
            isRead = false;

            final byte[] indexValue = incidentInstanceIndex.get(key);
            if (indexValue != null)
            {
                indexValueReadBuffer.wrap(indexValue);
                isRead = true;
            }
        }

        public int getState()
        {
            return isRead ? indexValueReadBuffer.getInt(STATE_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public long getIncidentEventPosition()
        {
            return isRead ? indexValueReadBuffer.getLong(INCIDENT_EVENT_POSITION_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public long getFailureEventPosition()
        {
            return isRead ? indexValueReadBuffer.getLong(FAILURE_EVENT_POSITION_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public int getChannelId()
        {
            return isRead ? indexValueReadBuffer.getInt(CHANNEL_ID_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public long getConnectionId()
        {
            return isRead ? indexValueReadBuffer.getLong(CONNECTION_ID_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public long getRequestId()
        {
            return isRead ? indexValueReadBuffer.getLong(REQUEST_ID_OFFSET, ByteOrder.LITTLE_ENDIAN) : MISSING_VALUE;
        }

        public void created(long incidentKey, long incidentEventPosition, long failureEventPosition)
        {
            indexValueReadBuffer.putInt(STATE_OFFSET, IncidentEventType.CREATED.id(), ByteOrder.LITTLE_ENDIAN);
            indexValueReadBuffer.putLong(INCIDENT_EVENT_POSITION_OFFSET, incidentEventPosition, ByteOrder.LITTLE_ENDIAN);
            indexValueReadBuffer.putLong(FAILURE_EVENT_POSITION_OFFSET, failureEventPosition, ByteOrder.LITTLE_ENDIAN);

            incidentInstanceIndex.put(incidentKey, indexValueReadBuffer.byteArray());
        }

        public void resolve(long incidentKey, int channelId, long connectionId, long requestId)
        {
            wrapIncidentKey(incidentKey);

            indexValueReadBuffer.putInt(STATE_OFFSET, IncidentEventType.RESOLVE.id(), ByteOrder.LITTLE_ENDIAN);
            indexValueReadBuffer.putInt(CHANNEL_ID_OFFSET, channelId, ByteOrder.LITTLE_ENDIAN);
            indexValueReadBuffer.putLong(CONNECTION_ID_OFFSET, connectionId, ByteOrder.LITTLE_ENDIAN);
            indexValueReadBuffer.putLong(REQUEST_ID_OFFSET, requestId, ByteOrder.LITTLE_ENDIAN);

            incidentInstanceIndex.put(incidentKey, indexValueReadBuffer.byteArray());
        }

        public void setState(long incidentKey, IncidentEventType state)
        {
            wrapIncidentKey(incidentKey);

            indexValueReadBuffer.putInt(STATE_OFFSET, state.id(), ByteOrder.LITTLE_ENDIAN);

            incidentInstanceIndex.put(incidentKey, indexValueReadBuffer.byteArray());
        }
    }

}
