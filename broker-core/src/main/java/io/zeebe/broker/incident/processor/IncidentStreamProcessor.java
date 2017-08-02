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
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentState;
import io.zeebe.broker.incident.index.IncidentMap;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskHeaders;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.snapshot.ComposedZbMapSnapshot;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;

/**
 * Is responsible for the incident lifecycle.
 */
public class IncidentStreamProcessor implements StreamProcessor
{
    private static final short STATE_CREATED = 1;
    private static final short STATE_RESOLVING = 2;
    private static final short STATE_DELETING = 3;

    private static final long NON_PERSISTENT_INCIDENT = -2L;

    private final Long2LongZbMap activityInstanceMap;
    private final Long2LongZbMap failedTaskMap;

    private final IncidentMap incidentMap;

    private final SnapshotSupport indexSnapshot;

    private final CreateIncidentProcessor createIncidentProcessor = new CreateIncidentProcessor();
    private final ResolveIncidentProcessor resolveIncidentProcessor = new ResolveIncidentProcessor();
    private final ResolveFailedProcessor resolveFailedProcessor = new ResolveFailedProcessor();
    private final DeleteIncidentProcessor deleteIncidentProcessor = new DeleteIncidentProcessor();

    private final PayloadUpdatedProcessor payloadUpdatedProcessor = new PayloadUpdatedProcessor();
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

    private LogStreamReader logStreamReader;
    private LogStream targetStream;

    public IncidentStreamProcessor()
    {
        this.activityInstanceMap = new Long2LongZbMap();
        this.failedTaskMap = new Long2LongZbMap();
        this.incidentMap = new IncidentMap();

        this.indexSnapshot = new ComposedZbMapSnapshot(
            new ZbMapSnapshotSupport<>(activityInstanceMap),
            new ZbMapSnapshotSupport<>(failedTaskMap),
            incidentMap.getSnapshotSupport());
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        logStreamReader = new BufferedLogStreamReader(context.getSourceStream());

        targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        activityInstanceMap.close();
        failedTaskMap.close();
        incidentMap.close();

        logStreamReader.close();
    }

    public static MetadataFilter eventFilter()
    {
        return event -> event.getEventType() == EventType.INCIDENT_EVENT
                || event.getEventType() == EventType.WORKFLOW_INSTANCE_EVENT
                || event.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        incidentMap.reset();

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

            case WORKFLOW_INSTANCE_EVENT:
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

        switch (incidentEvent.getState())
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

        switch (workflowInstanceEvent.getState())
        {
            case PAYLOAD_UPDATED:
                return payloadUpdatedProcessor;

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

        switch (taskEvent.getState())
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
            .protocolVersion(Protocol.PROTOCOL_VERSION)
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
        private boolean isCreated;
        private boolean isTaskIncident;

        @Override
        public void processEvent()
        {
            isTaskIncident = incidentEvent.getTaskKey() > 0;
            // ensure that the task is not resolved yet
            isCreated = isTaskIncident ? failedTaskMap.get(incidentEvent.getTaskKey(), -1L) == NON_PERSISTENT_INCIDENT : true;

            if (isCreated)
            {
                incidentEvent.setState(IncidentState.CREATED);
            }
            else
            {
                incidentEvent.setState(IncidentState.CREATE_REJECTED);
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
            if (isCreated)
            {
                incidentMap
                    .newIncident(eventKey)
                    .setState(STATE_CREATED)
                    .setIncidentEventPosition(eventPosition)
                    .setFailureEventPosition(incidentEvent.getFailureEventPosition())
                    .write();

                if (isTaskIncident)
                {
                    failedTaskMap.put(incidentEvent.getTaskKey(), eventKey);
                }
                else
                {
                    activityInstanceMap.put(incidentEvent.getActivityInstanceKey(), eventKey);
                }
            }
        }
    }

    private final class PayloadUpdatedProcessor implements EventProcessor
    {
        private boolean isResolving;
        private long incidentKey;

        @Override
        public void processEvent()
        {
            isResolving = false;

            incidentKey = activityInstanceMap.get(eventKey, -1L);

            if (incidentKey > 0 && incidentMap.wrapIncidentKey(incidentKey).getState() == STATE_CREATED)
            {
                incidentEvent.reset();
                incidentEvent
                    .setState(IncidentState.RESOLVE)
                    .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                    .setActivityInstanceKey(eventKey)
                    .setPayload(workflowInstanceEvent.getPayload());

                isResolving = true;
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return isResolving ? writeIncidentEvent(writer.key(incidentKey)) : 0L;
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

            incidentMap.wrapIncidentKey(eventKey);

            if (incidentMap.getState() == STATE_CREATED)
            {
                // re-write the failure event with new payload
                failureEvent = findEvent(incidentMap.getFailureEventPosition());

                workflowInstanceEvent.reset();
                failureEvent.readValue(workflowInstanceEvent);

                workflowInstanceEvent.setPayload(incidentEvent.getPayload());

                isResolved = true;
            }
            else
            {
                incidentEvent.setState(IncidentState.RESOLVE_REJECTED);
            }
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
                    .protocolVersion(Protocol.PROTOCOL_VERSION)
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
                incidentMap
                    .setState(STATE_RESOLVING)
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
            incidentMap.wrapIncidentKey(eventKey);

            isFailed = incidentMap.getState() == STATE_RESOLVING;
        }

        @Override
        public void updateState()
        {
            if (isFailed)
            {
                incidentMap
                    .setState(STATE_CREATED)
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

            incidentMap.wrapIncidentKey(eventKey);

            final long incidentEventPosition = incidentMap.getIncidentEventPosition();

            if (incidentEventPosition > 0)
            {
                final LoggedEvent incidentCreateEvent = findEvent(incidentEventPosition);

                incidentEvent.reset();
                incidentCreateEvent.readValue(incidentEvent);

                incidentEvent.setState(IncidentState.DELETED);
                isDeleted = true;
            }
            else
            {
                incidentEvent.setState(IncidentState.DELETE_REJECTED);
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
                incidentMap.remove(eventKey);
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

            incidentKey = activityInstanceMap.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_RESOLVING)
                {
                    // incident is resolved when read next activity lifecycle event
                    final LoggedEvent incidentCreateEvent = findEvent(incidentMap.getIncidentEventPosition());

                    incidentEvent.reset();
                    incidentCreateEvent.readValue(incidentEvent);

                    incidentEvent.setState(IncidentState.RESOLVED);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
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
                incidentMap.remove(incidentKey);
                activityInstanceMap.remove(incidentEvent.getActivityInstanceKey(), -1L);
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
            incidentKey = activityInstanceMap.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED || incidentMap.getState() == STATE_RESOLVING)
                {
                    incidentEvent.setState(IncidentState.DELETE);

                    isTerminated = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
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
                incidentMap.setState(STATE_DELETING).write();
                activityInstanceMap.remove(eventKey, -1L);
            }
        }
    }

    private final class TaskFailedProcessor implements EventProcessor
    {
        private boolean hasRetries;

        @Override
        public void processEvent()
        {
            hasRetries = taskEvent.getRetries() > 0;

            if (!hasRetries)
            {
                final TaskHeaders taskHeaders = taskEvent.headers();

                incidentEvent.reset();
                incidentEvent
                    .setState(IncidentState.CREATE)
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
            return hasRetries ? 0L : writeIncidentEvent(writer.positionAsKey());
        }

        @Override
        public void updateState()
        {
            if (!hasRetries)
            {
                failedTaskMap.put(eventKey, NON_PERSISTENT_INCIDENT);
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

            incidentKey = failedTaskMap.get(eventKey, -1L);

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED)
                {
                    incidentEvent.setState(IncidentState.DELETE);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
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
            if (isResolved || incidentKey == NON_PERSISTENT_INCIDENT)
            {
                failedTaskMap.remove(eventKey, -1L);
            }
        }
    }

}
