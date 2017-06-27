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
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.Constants;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentEventType;
import io.zeebe.broker.incident.index.IncidentIndex;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskHeaders;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.hashindex.Long2LongHashIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.protocol.clientapi.EventType;

/**
 * Is responsible for the incident lifecycle.
 */
public class IncidentStreamProcessor implements StreamProcessor
{
    private static final short STATE_CREATED = 1;
    private static final short STATE_RESOLVING = 2;
    private static final short STATE_DELETING = 3;

    private static final long NON_PERSISTENT_INCIDENT = -2L;

    private final Long2LongHashIndex activityInstanceIndex;
    private final Long2LongHashIndex failedTaskIndex;

    private final IncidentIndex incidentIndex;

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
        this.activityInstanceIndex = new Long2LongHashIndex(Short.MAX_VALUE, 64);
        this.failedTaskIndex = new Long2LongHashIndex(Short.MAX_VALUE, 64);
        this.incidentIndex = new IncidentIndex();

        this.indexSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(activityInstanceIndex),
                new HashIndexSnapshotSupport<>(failedTaskIndex),
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
        logStreamReader = new BufferedLogStreamReader(context.getSourceStream());

        targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        activityInstanceIndex.close();
        failedTaskIndex.close();
        incidentIndex.close();
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
        private boolean isCreated;
        private boolean isTaskIncident;

        @Override
        public void processEvent()
        {
            isTaskIncident = incidentEvent.getTaskKey() > 0;
            // ensure that the task is not resolved yet
            isCreated = isTaskIncident ? failedTaskIndex.get(incidentEvent.getTaskKey(), -1L) == NON_PERSISTENT_INCIDENT : true;

            if (isCreated)
            {
                incidentEvent.setEventType(IncidentEventType.CREATED);
            }
            else
            {
                incidentEvent.setEventType(IncidentEventType.CREATE_REJECTED);
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
                incidentIndex
                    .newIncident(eventKey)
                    .setState(STATE_CREATED)
                    .setIncidentEventPosition(eventPosition)
                    .setFailureEventPosition(incidentEvent.getFailureEventPosition())
                    .write();

                if (isTaskIncident)
                {
                    failedTaskIndex.put(incidentEvent.getTaskKey(), eventKey);
                }
                else
                {
                    activityInstanceIndex.put(incidentEvent.getActivityInstanceKey(), eventKey);
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

            incidentKey = activityInstanceIndex.get(eventKey, -1L);

            if (incidentKey > 0 && incidentIndex.wrapIncidentKey(incidentKey).getState() == STATE_CREATED)
            {
                incidentEvent.reset();
                incidentEvent
                    .setEventType(IncidentEventType.RESOLVE)
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

            incidentIndex.wrapIncidentKey(eventKey);

            if (incidentIndex.getState() == STATE_CREATED)
            {
                // re-write the failure event with new payload
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
                    .setState(STATE_RESOLVING)
                    .write();
            }
        }
    }

    private final class ResolveFailedProcessor implements EventProcessor
    {
        private boolean isFailed;

        // TODO event is ignored by recovery

        @Override
        public void processEvent()
        {
            incidentIndex.wrapIncidentKey(eventKey);

            isFailed = incidentIndex.getState() == STATE_RESOLVING;
        }

        @Override
        public void updateState()
        {
            if (isFailed)
            {
                incidentIndex
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

                if (incidentIndex.getState() == STATE_RESOLVING)
                {
                    // incident is resolved when read next activity lifecycle event
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

                if (incidentIndex.getState() == STATE_CREATED || incidentIndex.getState() == STATE_RESOLVING)
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
                incidentIndex.setState(STATE_DELETING).write();
                activityInstanceIndex.remove(eventKey, -1L);
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
                    .setEventType(IncidentEventType.CREATE)
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
                failedTaskIndex.put(eventKey, NON_PERSISTENT_INCIDENT);
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

                if (incidentIndex.getState() == STATE_CREATED)
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
            if (isResolved || incidentKey == NON_PERSISTENT_INCIDENT)
            {
                failedTaskIndex.remove(eventKey, -1L);
            }
        }
    }

}
