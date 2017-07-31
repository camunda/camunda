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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.broker.util.payload.PayloadUtil.isNilPayload;
import static io.zeebe.broker.util.payload.PayloadUtil.isValidPayload;
import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.protocol.clientapi.EventType.WORKFLOW_INSTANCE_EVENT;

import java.util.EnumMap;
import java.util.Map;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.task.data.*;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.workflow.data.*;
import io.zeebe.broker.workflow.graph.model.*;
import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata;
import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata.TaskHeader;
import io.zeebe.broker.workflow.map.*;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.snapshot.ComposedZbMapSnapshot;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.msgpack.mapping.*;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.actor.Actor;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceStreamProcessor implements StreamProcessor
{
    private static final UnsafeBuffer EMPTY_TASK_TYPE = new UnsafeBuffer("".getBytes());

    // processors ////////////////////////////////////
    protected final WorkflowCreatedEventProcessor workflowCreatedEventProcessor = new WorkflowCreatedEventProcessor();

    protected final CreateWorkflowInstanceEventProcessor createWorkflowInstanceEventProcessor = new CreateWorkflowInstanceEventProcessor();
    protected final WorkflowInstanceCreatedEventProcessor workflowInstanceCreatedEventProcessor = new WorkflowInstanceCreatedEventProcessor();
    protected final CancelWorkflowInstanceProcessor cancelWorkflowInstanceProcessor = new CancelWorkflowInstanceProcessor();

    protected final UpdatePayloadProcessor updatePayloadProcessor = new UpdatePayloadProcessor();

    protected final EventProcessor sequenceFlowTakenEventProcessor = new ActiveWorkflowInstanceProcessor(new SequenceFlowTakenEventProcessor());
    protected final EventProcessor activityReadyEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityReadyEventProcessor());
    protected final EventProcessor activityActivatedEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityActivatedEventProcessor());
    protected final EventProcessor activityCompletingEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityCompletingEventProcessor());

    protected final EventProcessor taskCompletedEventProcessor = new TaskCompletedEventProcessor();
    protected final EventProcessor taskCreatedEventProcessor = new TaskCreatedProcessor();

    protected final Map<BpmnAspect, EventProcessor> aspectHandlers;
    {
        aspectHandlers = new EnumMap<>(BpmnAspect.class);

        aspectHandlers.put(BpmnAspect.TAKE_SEQUENCE_FLOW, new ActiveWorkflowInstanceProcessor(new TakeSequenceFlowAspectHandler()));
        aspectHandlers.put(BpmnAspect.CONSUME_TOKEN, new ActiveWorkflowInstanceProcessor(new ConsumeTokenAspectHandler()));
    }

    // data //////////////////////////////////////////

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowEvent workflowEvent = new WorkflowEvent();
    protected final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    protected final TaskEvent taskEvent = new TaskEvent();

    // internal //////////////////////////////////////

    protected final CommandResponseWriter responseWriter;

    protected final WorkflowInstanceIndex workflowInstanceIndex;
    protected final ActivityInstanceMap activityInstanceMap;
    protected final WorkflowDeploymentCache workflowDeploymentCache;
    protected final PayloadCache payloadCache;

    protected final ComposedZbMapSnapshot composedSnapshot;

    protected LogStreamReader logStreamReader;
    protected LogStreamBatchWriter logStreamBatchWriter;

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;
    protected int streamProcessorId;
    protected long eventKey;
    protected long eventPosition;

    protected final MappingProcessor payloadMappingProcessor;

    protected LogStream targetStream;

    public WorkflowInstanceStreamProcessor(
            CommandResponseWriter responseWriter,
            int deploymentCacheSize,
            int payloadCacheSize)
    {
        this.responseWriter = responseWriter;
        this.logStreamReader = new BufferedLogStreamReader();

        this.workflowDeploymentCache = new WorkflowDeploymentCache(deploymentCacheSize, logStreamReader);
        this.payloadCache = new PayloadCache(payloadCacheSize, logStreamReader);

        this.workflowInstanceIndex = new WorkflowInstanceIndex();
        this.activityInstanceMap = new ActivityInstanceMap();

        this.payloadMappingProcessor = new MappingProcessor(4096);

        this.composedSnapshot = new ComposedZbMapSnapshot(
            workflowInstanceIndex.getSnapshotSupport(),
            activityInstanceMap.getSnapshotSupport(),
            workflowDeploymentCache.getSnapshotSupport(),
            payloadCache.getSnapshotSupport());

    }

    @Override
    public int getPriority(long now)
    {
        return Actor.PRIORITY_HIGH;
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return composedSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        final LogStream sourceStream = context.getSourceStream();
        this.logStreamTopicName = sourceStream.getTopicName();
        this.logStreamPartitionId = sourceStream.getPartitionId();
        this.streamProcessorId = context.getId();

        this.logStreamReader.wrap(sourceStream);
        this.logStreamBatchWriter = new LogStreamBatchWriterImpl(context.getTargetStream());

        this.targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        workflowInstanceIndex.close();
        activityInstanceMap.close();
        workflowDeploymentCache.close();
        payloadCache.close();
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == EventType.WORKFLOW_INSTANCE_EVENT
                || m.getEventType() == EventType.TASK_EVENT
                || m.getEventType() == EventType.WORKFLOW_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        reset();

        eventKey = event.getKey();
        eventPosition = event.getPosition();

        sourceEventMetadata.reset();
        event.readMetadata(sourceEventMetadata);

        EventProcessor eventProcessor = null;
        switch (sourceEventMetadata.getEventType())
        {
            case WORKFLOW_INSTANCE_EVENT:
                eventProcessor = onWorkflowInstanceEvent(event);
                break;

            case TASK_EVENT:
                eventProcessor = onTaskEvent(event);
                break;

            case WORKFLOW_EVENT:
                eventProcessor = onWorkflowEvent(event);
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected void reset()
    {
        workflowInstanceIndex.reset();
        activityInstanceMap.reset();
    }

    protected EventProcessor onWorkflowInstanceEvent(LoggedEvent event)
    {
        workflowInstanceEvent.reset();
        event.readValue(workflowInstanceEvent);

        EventProcessor eventProcessor = null;
        switch (workflowInstanceEvent.getEventType())
        {
            case CREATE_WORKFLOW_INSTANCE:
                eventProcessor = createWorkflowInstanceEventProcessor;
                break;

            case WORKFLOW_INSTANCE_CREATED:
                eventProcessor = workflowInstanceCreatedEventProcessor;
                break;

            case CANCEL_WORKFLOW_INSTANCE:
                eventProcessor = cancelWorkflowInstanceProcessor;
                break;

            case SEQUENCE_FLOW_TAKEN:
                eventProcessor = sequenceFlowTakenEventProcessor;
                break;

            case ACTIVITY_READY:
                eventProcessor = activityReadyEventProcessor;
                break;

            case ACTIVITY_ACTIVATED:
                eventProcessor = activityActivatedEventProcessor;
                break;

            case ACTIVITY_COMPLETING:
                eventProcessor = activityCompletingEventProcessor;
                break;

            case START_EVENT_OCCURRED:
            case END_EVENT_OCCURRED:
            case ACTIVITY_COMPLETED:
            {
                final ExecutableFlowNode currentActivity = getCurrentActivity();
                eventProcessor = aspectHandlers.get(currentActivity.getBpmnAspect());
                break;
            }

            case UPDATE_PAYLOAD:
                eventProcessor = updatePayloadProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected EventProcessor onTaskEvent(LoggedEvent event)
    {
        taskEvent.reset();
        event.readValue(taskEvent);

        switch (taskEvent.getEventType())
        {
            case CREATED:
                return taskCreatedEventProcessor;

            case COMPLETED:
                return taskCompletedEventProcessor;

            default:
                return null;
        }
    }

    protected EventProcessor onWorkflowEvent(LoggedEvent event)
    {
        workflowEvent.reset();
        event.readValue(workflowEvent);

        switch (workflowEvent.getEventType())
        {
            case CREATED:
                return workflowCreatedEventProcessor;

            default:
                return null;
        }
    }

    protected void lookupWorkflowInstanceEvent(long position)
    {
        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            workflowInstanceEvent.reset();
            event.readValue(workflowInstanceEvent);
        }
        else
        {
            throw new IllegalStateException("workflow instance event not found.");
        }
    }

    protected <T extends ExecutableFlowElement> T getCurrentActivity()
    {
        final long workflowKey = workflowInstanceEvent.getWorkflowKey();
        final ExecutableWorkflow workflow = workflowDeploymentCache.getWorkflow(workflowKey);

        if (workflow != null)
        {
            final DirectBuffer currentActivityId = workflowInstanceEvent.getActivityId();

            return workflow.getChildById(currentActivityId);
        }
        else
        {
            throw new RuntimeException("No workflow found for key: " + workflowKey);
        }
    }

    protected long writeWorkflowEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(WORKFLOW_INSTANCE_EVENT)
                .raftTermId(targetStream.getTerm());

        // don't forget to set the key or use positionAsKey
        return writer
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowInstanceEvent)
                .tryWrite();
    }

    protected long writeTaskEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(TASK_EVENT)
                .raftTermId(targetStream.getTerm());

        // don't forget to set the key or use positionAsKey
        return writer
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .tryWrite();
    }

    protected boolean sendWorkflowInstanceResponse()
    {
        return responseWriter
                .topicName(logStreamTopicName)
                .partitionId(logStreamPartitionId)
                .key(eventKey)
                .eventWriter(workflowInstanceEvent)
                .tryWriteResponse(sourceEventMetadata.getRequestStreamId(), sourceEventMetadata.getRequestId());
    }

    private final class WorkflowCreatedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            if (eventKey != eventPosition)
            {
                throw new RuntimeException("The workflow event position is not equal to the key, but the implementation based on it.");
            }
        }

        @Override
        public void updateState()
        {
            final int version = workflowEvent.getVersion();
            final DirectBuffer bpmnProcessId = workflowEvent.getBpmnProcessId();

            workflowDeploymentCache.addDeployedWorkflow(eventKey, bpmnProcessId, version);
        }
    }

    private final class CreateWorkflowInstanceEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            WorkflowInstanceEventType newEventType = WorkflowInstanceEventType.WORKFLOW_INSTANCE_REJECTED;

            long workflowKey = workflowInstanceEvent.getWorkflowKey();
            final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
            final int version = workflowInstanceEvent.getVersion();

            if (workflowKey <= 0)
            {
                if (version > 0)
                {
                    workflowKey = workflowDeploymentCache.getWorkflowKeyByIdAndVersion(bpmnProcessId, version);
                }
                else
                {
                    workflowKey = workflowDeploymentCache.getWorkflowKeyByIdAndLatestVersion(bpmnProcessId);
                }
            }

            if (workflowKey > 0)
            {
                final ExecutableWorkflow workflow = workflowDeploymentCache.getWorkflow(workflowKey);
                final DirectBuffer payload = workflowInstanceEvent.getPayload();

                if (workflow != null && (isNilPayload(payload) || isValidPayload(payload)))
                {
                    workflowInstanceEvent
                        .setWorkflowKey(workflowKey)
                        .setBpmnProcessId(workflow.getId())
                        .setVersion(workflow.getVersion());

                    newEventType = WorkflowInstanceEventType.WORKFLOW_INSTANCE_CREATED;
                }
            }

            workflowInstanceEvent
                    .setEventType(newEventType)
                    .setWorkflowInstanceKey(eventKey);
        }

        @Override
        public boolean executeSideEffects()
        {
            return sendWorkflowInstanceResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }
    }

    private final class WorkflowInstanceCreatedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final long workflowKey = workflowInstanceEvent.getWorkflowKey();
            final ExecutableWorkflow workflow = workflowDeploymentCache.getWorkflow(workflowKey);

            if (workflow != null)
            {
                final ExecutableStartEvent startEvent = workflow.getScopeStartEvent();
                final DirectBuffer activityId = startEvent.getId();

                workflowInstanceEvent
                    .setEventType(WorkflowInstanceEventType.START_EVENT_OCCURRED)
                    .setWorkflowInstanceKey(eventKey)
                    .setActivityId(activityId);
            }
            else
            {
                throw new RuntimeException("No workflow found for key: " + workflowKey);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndex
                .newWorkflowInstance(eventKey)
                .setPosition(eventPosition)
                .setActiveTokenCount(1)
                .setActivityKey(-1L)
                .write();
        }
    }

    private final class TakeSequenceFlowAspectHandler implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final ExecutableFlowNode currentActivity = getCurrentActivity();

            // the activity has exactly one outgoing sequence flow
            final ExecutableSequenceFlow sequenceFlow = currentActivity.getOutgoingSequenceFlows()[0];

            workflowInstanceEvent
                .setEventType(WorkflowInstanceEventType.SEQUENCE_FLOW_TAKEN)
                .setActivityId(sequenceFlow.getId());
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }
    }

    private final class ConsumeTokenAspectHandler implements EventProcessor
    {
        private boolean isCompleted;
        private int activeTokenCount;

        @Override
        public void processEvent()
        {
            isCompleted = false;

            activeTokenCount = workflowInstanceIndex
                    .wrapWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                    .getTokenCount();
            if (activeTokenCount == 1)
            {
                workflowInstanceEvent
                    .setEventType(WorkflowInstanceEventType.WORKFLOW_INSTANCE_COMPLETED)
                    .setActivityId("");

                isCompleted = true;
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            long position = 0L;

            if (isCompleted)
            {
                position = writeWorkflowEvent(
                        writer.key(workflowInstanceEvent.getWorkflowInstanceKey()));
            }
            return position;
        }

        @Override
        public void updateState()
        {
            if (isCompleted)
            {
                workflowInstanceIndex.remove(workflowInstanceEvent.getWorkflowInstanceKey());
                payloadCache.remove(workflowInstanceEvent.getWorkflowInstanceKey());
            }
            else
            {
                workflowInstanceIndex
                    .setActiveTokenCount(activeTokenCount - 1)
                    .write();
            }
        }
    }

    private final class SequenceFlowTakenEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final ExecutableSequenceFlow sequenceFlow = getCurrentActivity();
            final ExecutableFlowNode targetNode = sequenceFlow.getTargetNode();

            workflowInstanceEvent.setActivityId(targetNode.getId());

            if (targetNode instanceof ExecutableEndEvent)
            {
                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.END_EVENT_OCCURRED);
            }
            else if (targetNode instanceof ExecutableServiceTask)
            {
                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_READY);
            }
            else
            {
                throw new RuntimeException("Currently not supported. A sequence flow must end in an end event or service task.");
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }
    }

    private final class ActivityReadyEventProcessor implements EventProcessor
    {
        private final DirectBuffer sourcePayload = new UnsafeBuffer(0, 0);

        @Override
        public void processEvent()
        {
            final ExecutableFlowElement activty = getCurrentActivity();

            if (activty instanceof ExecutableServiceTask)
            {
                final ExecutableServiceTask serviceTask = (ExecutableServiceTask) activty;

                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_ACTIVATED);

                try
                {
                    setWorkflowInstancePayload(serviceTask.getIoMapping().getInputMappings());
                }
                catch (Exception e)
                {
                    // update the map in any case because further processors based on it (#311 should improve behavior)
                    updateState();
                    // re-throw the exception to create the incident
                    throw e;
                }
            }
            else
            {
                throw new RuntimeException("Currently not supported. An activity must be of type service task.");
            }
        }

        private void setWorkflowInstancePayload(Mapping[] mappings)
        {
            sourcePayload.wrap(workflowInstanceEvent.getPayload());
            // only if we have no default mapping we have to use the mapping processor
            if (mappings.length > 0)
            {
                final int resultLen = payloadMappingProcessor.extract(sourcePayload, mappings);
                final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
                workflowInstanceEvent.setPayload(buffer, 0, resultLen);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndex
                .wrapWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityKey(eventKey)
                .write();

            activityInstanceMap
                .newActivityInstance(eventKey)
                .setActivityId(workflowInstanceEvent.getActivityId())
                .setTaskKey(-1L)
                .write();

            if (!isNilPayload(sourcePayload))
            {
                payloadCache.addPayload(workflowInstanceEvent.getWorkflowInstanceKey(), eventPosition, sourcePayload);
            }
        }
    }

    private final class ActivityActivatedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final ExecutableServiceTask serviceTask = getCurrentActivity();
            final TaskMetadata taskMetadata = serviceTask.getTaskMetadata();

            taskEvent.reset();

            taskEvent
                .setEventType(TaskEventType.CREATE)
                .setType(taskMetadata.getTaskType())
                .setRetries(taskMetadata.getRetries())
                .setPayload(workflowInstanceEvent.getPayload());

            setTaskHeaders(serviceTask, taskMetadata);
        }

        private void setTaskHeaders(ExecutableServiceTask serviceTask, TaskMetadata taskMetadata)
        {
            final TaskHeaders taskHeaders = taskEvent.headers()
                .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                .setWorkflowDefinitionVersion(workflowInstanceEvent.getVersion())
                .setWorkflowKey(workflowInstanceEvent.getWorkflowKey())
                .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityId(serviceTask.getId())
                .setActivityInstanceKey(eventKey);

            final TaskHeader[] customHeaders = taskMetadata.getHeaders();
            for (int i = 0; i < customHeaders.length; i++)
            {
                final TaskHeader customHeader = customHeaders[i];

                taskHeaders.customHeaders().add()
                    .setKey(customHeader.getKey())
                    .setValue(customHeader.getValue());
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeTaskEvent(writer.positionAsKey());
        }
    }

    private final class TaskCreatedProcessor implements EventProcessor
    {
        private boolean isActive;

        @Override
        public void processEvent()
        {
            isActive = false;

            final TaskHeaders taskHeaders = taskEvent.headers();
            final long activityInstanceKey = taskHeaders.getActivityInstanceKey();
            if (activityInstanceKey > 0)
            {
                final long currentActivityInstanceKey = workflowInstanceIndex.wrapWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey()).getActivityInstanceKey();

                isActive = activityInstanceKey == currentActivityInstanceKey;
            }
        }

        @Override
        public void updateState()
        {
            if (isActive)
            {
                activityInstanceMap
                    .wrapActivityInstanceKey(taskEvent.headers().getActivityInstanceKey())
                    .setTaskKey(eventKey)
                    .write();
            }
        }
    }

    private final class TaskCompletedEventProcessor implements EventProcessor
    {
        private boolean isActivityCompleted;
        private long activityInstanceKey;

        @Override
        public void processEvent()
        {
            isActivityCompleted = false;

            final TaskHeaders taskHeaders = taskEvent.headers();
            activityInstanceKey = taskHeaders.getActivityInstanceKey();

            if (taskHeaders.getWorkflowInstanceKey() > 0 && isTaskOpen(activityInstanceKey))
            {
                workflowInstanceEvent
                    .setEventType(WorkflowInstanceEventType.ACTIVITY_COMPLETING)
                    .setBpmnProcessId(taskHeaders.getBpmnProcessId())
                    .setVersion(taskHeaders.getWorkflowDefinitionVersion())
                    .setWorkflowKey(taskHeaders.getWorkflowKey())
                    .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                    .setActivityId(taskHeaders.getActivityId())
                    .setPayload(taskEvent.getPayload());

                isActivityCompleted = true;
            }
        }

        private boolean isTaskOpen(long activityInstanceKey)
        {
            // task key = -1 when activity is left
            return activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getTaskKey() == eventKey;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return isActivityCompleted ? writeWorkflowEvent(writer.key(activityInstanceKey)) : 0L;
        }

        @Override
        public void updateState()
        {
            if (isActivityCompleted)
            {
                activityInstanceMap
                    .setTaskKey(-1L)
                    .write();
            }
        }
    }

    private final class ActivityCompletingEventProcessor implements EventProcessor
    {
        public static final String INCIDENT_ERROR_MSG_MISSING_TASK_PAYLOAD_ON_OUT_MAPPING = "Task was completed without an payload - processing of output mapping failed!";

        @Override
        public void processEvent()
        {
            final ExecutableServiceTask serviceTask = getCurrentActivity();

            workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_COMPLETED);

            setWorkflowInstancePayload(serviceTask.getIoMapping().getOutputMappings());
        }

        private void setWorkflowInstancePayload(Mapping[] mappings)
        {
            final DirectBuffer workflowInstancePayload = payloadCache.getPayload(workflowInstanceEvent.getWorkflowInstanceKey());
            final DirectBuffer taskPayload = workflowInstanceEvent.getPayload();
            final boolean isNilPayload = isNilPayload(taskPayload);
            if (mappings.length > 0)
            {
                if (isNilPayload)
                {
                    throw new MappingException(INCIDENT_ERROR_MSG_MISSING_TASK_PAYLOAD_ON_OUT_MAPPING);
                }
                final int resultLen = payloadMappingProcessor.merge(taskPayload, workflowInstancePayload, mappings);
                final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
                workflowInstanceEvent.setPayload(buffer, 0, resultLen);
            }
            else if (isNilPayload)
            {
                // no payload from task complete
                workflowInstanceEvent.setPayload(workflowInstancePayload, 0, workflowInstancePayload.capacity());
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndex
                .wrapWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityKey(-1L)
                .write();

            activityInstanceMap.remove(eventKey);
        }
    }

    private final class CancelWorkflowInstanceProcessor implements EventProcessor
    {
        private final WorkflowInstanceEvent activityInstanceEvent = new WorkflowInstanceEvent();

        private boolean isCanceled;
        private long activityInstanceKey;
        private long taskKey;

        @Override
        public void processEvent()
        {
            isCanceled = false;

            workflowInstanceIndex.wrapWorkflowInstanceKey(eventKey);

            if (workflowInstanceIndex.getTokenCount() > 0)
            {
                lookupWorkflowInstanceEvent(workflowInstanceIndex.getPosition());

                workflowInstanceEvent
                    .setEventType(WorkflowInstanceEventType.WORKFLOW_INSTANCE_CANCELED)
                    .setPayload(WorkflowInstanceEvent.NO_PAYLOAD);

                activityInstanceKey = workflowInstanceIndex.getActivityInstanceKey();
                taskKey = activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getTaskKey();

                isCanceled = true;
            }
            else
            {
                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.CANCEL_WORKFLOW_INSTANCE_REJECTED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            logStreamBatchWriter
                .producerId(streamProcessorId)
                .sourceEvent(logStreamTopicName, logStreamPartitionId, eventPosition);

            if (taskKey > 0)
            {
                writeCancelTaskEvent(logStreamBatchWriter.event(), taskKey);
            }

            if (activityInstanceKey > 0)
            {
                writeTerminateActivityInstanceEvent(logStreamBatchWriter.event(), activityInstanceKey);
            }

            writeWorklowInstanceEvent(logStreamBatchWriter.event());

            return logStreamBatchWriter.tryWrite();
        }

        private void writeWorklowInstanceEvent(LogEntryBuilder logEntryBuilder)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Protocol.PROTOCOL_VERSION)
                    .raftTermId(targetStream.getTerm())
                    .eventType(WORKFLOW_INSTANCE_EVENT);

            logEntryBuilder
                .key(eventKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowInstanceEvent)
                .done();
        }

        private void writeCancelTaskEvent(LogEntryBuilder logEntryBuilder, long taskKey)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .raftTermId(targetStream.getTerm())
                .eventType(TASK_EVENT);

            taskEvent.reset();
            taskEvent
                .setEventType(TaskEventType.CANCEL)
                .setType(EMPTY_TASK_TYPE)
                .headers()
                    .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                    .setWorkflowDefinitionVersion(workflowInstanceEvent.getVersion())
                    .setWorkflowInstanceKey(eventKey)
                    .setActivityId(activityInstanceMap.getActivityId())
                    .setActivityInstanceKey(activityInstanceKey);

            logEntryBuilder
                .key(taskKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .done();
        }

        private void writeTerminateActivityInstanceEvent(LogEntryBuilder logEntryBuilder, long activityInstanceKey)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Protocol.PROTOCOL_VERSION)
                    .raftTermId(targetStream.getTerm())
                    .eventType(WORKFLOW_INSTANCE_EVENT);

            activityInstanceEvent.reset();
            activityInstanceEvent
                .setEventType(WorkflowInstanceEventType.ACTIVITY_TERMINATED)
                .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                .setVersion(workflowInstanceEvent.getVersion())
                .setWorkflowInstanceKey(eventKey)
                .setActivityId(activityInstanceMap.getActivityId());

            logEntryBuilder
                .key(activityInstanceKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(activityInstanceEvent)
                .done();
        }

        @Override
        public boolean executeSideEffects()
        {
            return sendWorkflowInstanceResponse();
        }

        @Override
        public void updateState()
        {
            if (isCanceled)
            {
                workflowInstanceIndex.remove(eventKey);
                payloadCache.remove(eventKey);
                activityInstanceMap.remove(activityInstanceKey);
            }
        }
    }

    private final class UpdatePayloadProcessor implements EventProcessor
    {
        private boolean isUpdated;

        @Override
        public void processEvent()
        {
            isUpdated = false;

            final long currentActivityInstanceKey = workflowInstanceIndex.wrapWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey()).getActivityInstanceKey();

            // the map contains the activity when it is ready, activated or completing
            // in this cases, the payload can be updated and it is taken for the next workflow instance event
            WorkflowInstanceEventType workflowInstanceEventType = WorkflowInstanceEventType.UPDATE_PAYLOAD_REJECTED;
            if (currentActivityInstanceKey > 0 && currentActivityInstanceKey == eventKey)
            {
                final DirectBuffer payload = workflowInstanceEvent.getPayload();
                if (isValidPayload(payload))
                {
                    workflowInstanceEventType = WorkflowInstanceEventType.PAYLOAD_UPDATED;
                    isUpdated = true;
                }
            }
            workflowInstanceEvent.setEventType(workflowInstanceEventType);
        }

        @Override
        public boolean executeSideEffects()
        {
            return sendWorkflowInstanceResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            if (isUpdated)
            {
                payloadCache.addPayload(workflowInstanceEvent.getWorkflowInstanceKey(), eventPosition, workflowInstanceEvent.getPayload());
            }
        }
    }

    private final class ActiveWorkflowInstanceProcessor implements EventProcessor
    {
        private final EventProcessor processor;

        private boolean isActive;

        ActiveWorkflowInstanceProcessor(EventProcessor processor)
        {
            this.processor = processor;
        }

        @Override
        public void processEvent()
        {
            isActive = workflowInstanceIndex
                    .wrapWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                    .getTokenCount() > 0;

            if (isActive)
            {
                processor.processEvent();
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            return isActive ? processor.executeSideEffects() : true;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return isActive ? processor.writeEvent(writer) : 0L;
        }

        @Override
        public void updateState()
        {
            if (isActive)
            {
                processor.updateState();
            }
        }
    }

}
