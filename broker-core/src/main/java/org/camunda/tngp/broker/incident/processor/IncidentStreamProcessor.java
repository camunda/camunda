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

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.incident.data.ErrorType;
import org.camunda.tngp.broker.incident.data.IncidentEvent;
import org.camunda.tngp.broker.incident.data.IncidentEventType;
import org.camunda.tngp.broker.incident.index.IncidentIndex;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.task.data.TaskEvent;
import org.camunda.tngp.broker.task.data.TaskHeaders;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.*;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.snapshot.ComposedSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;

public class IncidentStreamProcessor implements StreamProcessor
{
    private static final short STATE_WORKFLOW_INCIDENT_CREATED = 1;
    private static final short STATE_TASK_INCIDENT_CREATED = 2;
    private static final short STATE_INCIDENT_RESOLVING = 3;
    private static final short STATE_INCIDENT_RESOLVE_FAILED = 4;

    private final Long2LongHashIndex activityInstanceIndex;
    private final Long2LongHashIndex failedTaskIndex;

    private final IncidentIndex incidentIndex;

    private final SnapshotSupport indexSnapshot;

    private final CreateIncidentProcessor createIncidentProcessor = new CreateIncidentProcessor();
    private final ResolveIncidentProcessor resolveIncidentProcessor = new ResolveIncidentProcessor();
    private final ResolveFailedProcessor resolveFailedProcessor = new ResolveFailedProcessor();
    private final DeleteIncidentProcessor deleteIncidentProcessor = new DeleteIncidentProcessor();

    private final ActivityIncidentResolvedProcessor activityIncidentResolvedProcessor = new ActivityIncidentResolvedProcessor();
    private final ActivityTerminatedProcessor activityTerminatedProcessor = new ActivityTerminatedProcessor();

    private final TaskFailedProcessor taskFailedProcessor = new TaskFailedProcessor();
    private final TaskIncidentResolvedProcessor taskIncidentResolvedProcessor = new TaskIncidentResolvedProcessor();

    private final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    private final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    private final IncidentEvent incidentEvent = new IncidentEvent();
    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    private final TaskEvent taskEvent = new TaskEvent();

    private long eventKey;
    private long eventPosition;

    private final CommandResponseWriter responseWriter;

    private LogStreamReader logStreamReader;
    private DirectBuffer logStreamTopicName;
    private int logStreamPartitionId;
    private LogStream targetStream;

    public IncidentStreamProcessor(CommandResponseWriter responseWriter, IndexStore instanceIndexStore, IndexStore activityIndexStore, IndexStore taskIndexStore)
    {
        this.responseWriter = responseWriter;

        this.activityInstanceIndex = new Long2LongHashIndex(activityIndexStore, Short.MAX_VALUE, 64);
        this.failedTaskIndex = new Long2LongHashIndex(taskIndexStore, Short.MAX_VALUE, 64);
        this.incidentIndex = new IncidentIndex(instanceIndexStore);

        this.indexSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(activityInstanceIndex, activityIndexStore),
                new HashIndexSnapshotSupport<>(failedTaskIndex, taskIndexStore),
                incidentIndex.getSnapshotSupport());
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

        targetStream = context.getTargetStream();
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
        incidentIndex.reset();

        eventKey = event.getKey();
        eventPosition = event.getPosition();

        sourceEventMetadata.reset();
        event.readMetadata(sourceEventMetadata);

        EventProcessor eventProcessor = null;
        switch (sourceEventMetadata.getEventType())
        {
            case INCIDENT_EVENT:
                eventProcessor = onIncidentEvent(event);
                break;

            case WORKFLOW_EVENT:
                eventProcessor = onWorkflowInstanceEvent(event);
                break;

            case TASK_EVENT:
                eventProcessor = onTaskEvent(event);
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

            case DELETE:
                return deleteIncidentProcessor;

            default:
                return null;
        }
    }

    private EventProcessor onWorkflowInstanceEvent(LoggedEvent event)
    {
        workflowInstanceEvent.reset();
        event.readValue(workflowInstanceEvent);

        switch (workflowInstanceEvent.getEventType())
        {
            case ACTIVITY_ACTIVATED:
            case ACTIVITY_COMPLETED:
                return activityIncidentResolvedProcessor;

            case ACTIVITY_TERMINATED:
                return activityTerminatedProcessor;

            default:
                return null;
        }
    }

    private EventProcessor onTaskEvent(LoggedEvent event)
    {
        taskEvent.reset();
        event.readValue(taskEvent);

        switch (taskEvent.getEventType())
        {
            case FAILED:
                return taskFailedProcessor;

            case RETRIES_UPDATED:
            case CANCELED:
                return taskIncidentResolvedProcessor;

            default:
                return null;
        }
    }

    private long writeIncidentEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata.eventType(EventType.INCIDENT_EVENT)
            .protocolVersion(Constants.PROTOCOL_VERSION)
            .raftTermId(targetStream.getTerm());

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
            incidentIndex
                .newIncident(eventKey)
                .setState(STATE_WORKFLOW_INCIDENT_CREATED)
                .setIncidentEventPosition(eventPosition)
                .setFailureEventPosition(incidentEvent.getFailureEventPosition())
                .setChannelId(-1)
                .setConnectionId(-1L)
                .setRequestId(-1L)
                .write();

            activityInstanceIndex.put(incidentEvent.getActivityInstanceKey(), eventKey);
        }
    }

    private final class ResolveIncidentProcessor implements EventProcessor
    {
        private boolean isResolved;
        private LoggedEvent failureEvent;

        @Override
        public void processEvent()
        {
            isResolved = false;

            incidentIndex.wrapIncidentKey(eventKey);

            if (incidentIndex.getState() == STATE_WORKFLOW_INCIDENT_CREATED || incidentIndex.getState() == STATE_INCIDENT_RESOLVE_FAILED)
            {
                failureEvent = findEvent(incidentIndex.getFailureEventPosition());

                workflowInstanceEvent.reset();
                failureEvent.readValue(workflowInstanceEvent);

                workflowInstanceEvent.setPayload(incidentEvent.getPayload());

                isResolved = true;
            }
            else
            {
                incidentEvent.setEventType(IncidentEventType.RESOLVE_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (!isResolved)
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
            long position = 0L;

            if (isResolved)
            {
                targetEventMetadata.reset();

                failureEvent.readMetadata(targetEventMetadata);

                targetEventMetadata
                    .incidentKey(eventKey)
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .raftTermId(targetStream.getTerm());

                position = writer
                        .key(failureEvent.getKey())
                        .metadataWriter(targetEventMetadata)
                        .valueWriter(workflowInstanceEvent)
                        .tryWrite();
            }
            else
            {
                position = writeIncidentEvent(writer.key(eventKey));
            }
            return position;
        }

        @Override
        public void updateState()
        {
            if (isResolved)
            {
                incidentIndex
                    .setState(STATE_INCIDENT_RESOLVING)
                    .setChannelId(sourceEventMetadata.getReqChannelId())
                    .setConnectionId(sourceEventMetadata.getReqConnectionId())
                    .setRequestId(sourceEventMetadata.getReqRequestId())
                    .write();
            }
        }
    }

    private final class ResolveFailedProcessor implements EventProcessor
    {
        private boolean isFailed;

        @Override
        public void processEvent()
        {
            incidentIndex.wrapIncidentKey(eventKey);

            isFailed = incidentIndex.getState() == STATE_INCIDENT_RESOLVING;
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isFailed)
            {
                targetEventMetadata.reset()
                    .reqChannelId(incidentIndex.getChannelId())
                    .reqConnectionId(incidentIndex.getConnectionId())
                    .reqRequestId(incidentIndex.getRequestId());

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
                incidentIndex
                    .setState(STATE_INCIDENT_RESOLVE_FAILED)
                    .write();
            }
        }
    }

    private final class DeleteIncidentProcessor implements EventProcessor
    {
        private boolean isDeleted;

        @Override
        public void processEvent()
        {
            isDeleted = false;

            incidentIndex.wrapIncidentKey(eventKey);

            final long incidentEventPosition = incidentIndex.getIncidentEventPosition();

            if (incidentEventPosition > 0)
            {
                final LoggedEvent incidentCreateEvent = findEvent(incidentEventPosition);

                incidentEvent.reset();
                incidentCreateEvent.readValue(incidentEvent);

                incidentEvent.setEventType(IncidentEventType.DELETED);
                isDeleted = true;
            }
            else
            {
                incidentEvent.setEventType(IncidentEventType.DELETE_REJECTED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeIncidentEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            if (isDeleted)
            {
                incidentIndex.remove(eventKey);
            }
        }
    }

    private final class ActivityIncidentResolvedProcessor implements EventProcessor
    {
        private boolean isResolved;
        private long incidentKey;

        @Override
        public void processEvent()
        {
            isResolved = false;

            incidentKey = activityInstanceIndex.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentIndex.wrapIncidentKey(incidentKey);

                if (incidentIndex.getState() == STATE_INCIDENT_RESOLVING)
                {
                    final LoggedEvent incidentCreateEvent = findEvent(incidentIndex.getIncidentEventPosition());

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
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isResolved)
            {
                targetEventMetadata.reset()
                    .reqChannelId(incidentIndex.getChannelId())
                    .reqConnectionId(incidentIndex.getConnectionId())
                    .reqRequestId(incidentIndex.getRequestId());

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
            return isResolved ? writeIncidentEvent(writer.key(incidentKey)) : 0L;
        }

        @Override
        public void updateState()
        {
            if (isResolved)
            {
                incidentIndex.remove(incidentKey);
                activityInstanceIndex.remove(incidentEvent.getActivityInstanceKey(), -1L);
            }
        }
    }

    private final class ActivityTerminatedProcessor implements EventProcessor
    {
        private boolean isTerminated;
        private long incidentKey;

        @Override
        public void processEvent()
        {
            incidentKey = activityInstanceIndex.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentIndex.wrapIncidentKey(incidentKey);

                if (incidentIndex.getState() > 0)
                {
                    incidentEvent.setEventType(IncidentEventType.DELETE);

                    isTerminated = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident index");
                }
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return isTerminated ? writeIncidentEvent(writer.key(incidentKey)) : 0L;
        }

        @Override
        public void updateState()
        {
            if (isTerminated)
            {
                activityInstanceIndex.remove(eventKey, -1L);
            }
        }
    }

    private final class TaskFailedProcessor implements EventProcessor
    {
        private boolean hasRetries;
        private long incidentPosition;

        @Override
        public void processEvent()
        {
            hasRetries = taskEvent.getRetries() > 0;

            if (!hasRetries)
            {
                final TaskHeaders taskHeaders = taskEvent.headers();

                incidentEvent.reset();
                incidentEvent
                    .setEventType(IncidentEventType.CREATED)
                    .setErrorType(ErrorType.TASK_NO_RETRIES)
                    .setErrorMessage("No more retries left.")
                    .setFailureEventPosition(eventPosition)
                    .setBpmnProcessId(taskHeaders.getBpmnProcessId())
                    .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                    .setActivityId(taskHeaders.getActivityId())
                    .setActivityInstanceKey(taskHeaders.getActivityInstanceKey())
                    .setTaskKey(eventKey);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            incidentPosition = 0;

            if (!hasRetries)
            {
                incidentPosition = writeIncidentEvent(writer.positionAsKey());
            }
            return incidentPosition;
        }

        @Override
        public void updateState()
        {
            if (!hasRetries)
            {
                incidentIndex
                    .newIncident(incidentPosition)
                    .setState(STATE_TASK_INCIDENT_CREATED)
                    .setIncidentEventPosition(incidentPosition)
                    .setFailureEventPosition(eventPosition)
                    .setChannelId(-1)
                    .setConnectionId(-1L)
                    .setRequestId(-1L)
                    .write();

                failedTaskIndex.put(eventKey, incidentPosition);
            }
        }
    }

    private final class TaskIncidentResolvedProcessor implements EventProcessor
    {
        private boolean isResolved;
        private long incidentKey;

        @Override
        public void processEvent()
        {
            isResolved = false;

            incidentKey = failedTaskIndex.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentIndex.wrapIncidentKey(incidentKey);

                if (incidentIndex.getState() == STATE_TASK_INCIDENT_CREATED)
                {
                    incidentEvent.setEventType(IncidentEventType.DELETE);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident index");
                }
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return isResolved ? writeIncidentEvent(writer.key(incidentKey)) : 0L;
        }

        @Override
        public void updateState()
        {
            if (isResolved)
            {
                failedTaskIndex.remove(eventKey, -1L);
            }
        }
    }

}
