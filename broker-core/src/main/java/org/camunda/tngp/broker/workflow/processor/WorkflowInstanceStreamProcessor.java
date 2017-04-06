package org.camunda.tngp.broker.workflow.processor;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.protocol.clientapi.EventType.WORKFLOW_EVENT;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.taskqueue.data.TaskHeaders;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.broker.workflow.data.DeployedWorkflow;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEventType;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableEndEvent;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowElement;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowNode;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableSequenceFlow;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableServiceTask;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableStartEvent;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableWorkflow;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata.TaskHeader;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.snapshot.ComposedSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;

public class WorkflowInstanceStreamProcessor implements StreamProcessor
{

    public static final int SIZE_OF_PROCESS_ID = BpmnProcessIdRule.PROCESS_ID_MAX_LENGTH * SIZE_OF_CHAR;
    public static final int SIZE_OF_COMPOSITE_KEY = SIZE_OF_PROCESS_ID + SIZE_OF_INT;

    // processors ////////////////////////////////////
    protected final DeployedWorkflowEventProcessor deployedWorkflowEventProcessor = new DeployedWorkflowEventProcessor();

    protected final CreateWorkflowInstanceEventProcessor createWorkflowInstanceEventProcessor = new CreateWorkflowInstanceEventProcessor();
    protected final WorkflowInstanceCreatedEventProcessor workflowInstanceCreatedEventProcessor = new WorkflowInstanceCreatedEventProcessor();
    protected final ActivityCompletedEventProcessor activityCompletedEventProcessor = new ActivityCompletedEventProcessor();
    protected final SequenceFlowTakenEventProcessor sequenceFlowTakenEventProcessor = new SequenceFlowTakenEventProcessor();
    protected final EndEventProcessor endEventProcessor = new EndEventProcessor();
    protected final ActivityActivatedEventProcessor activityActivatedEventProcessor = new ActivityActivatedEventProcessor();

    protected final TaskCompletedEventProcessor taskCompletedEventProcessor = new TaskCompletedEventProcessor();

    // data //////////////////////////////////////////

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowDeploymentEvent deploymentEvent = new WorkflowDeploymentEvent();
    protected final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    protected final TaskEvent taskEvent = new TaskEvent();

    // internal //////////////////////////////////////

    protected final CommandResponseWriter responseWriter;

    /**
     * An hash index which contains as key the BPMN process id and as value the corresponding latest definition version.
     */
    protected final Bytes2LongHashIndex latestWorkflowVersionIndex;

    /**
     * An hash index which contains as key the BPMN process id and definition version concatenated
     * and as value the position of the deployment event.
     */
    protected final Bytes2LongHashIndex workflowPositionIndex;
    protected final WorkflowPositionIndexAccessor workflowPositionIndexAccessor = new WorkflowPositionIndexAccessor();

    /**
     * An hash index which contains as key the workflow instance key and as value the active token count.
     */
    protected final Long2LongHashIndex workflowInstanceTokenCountIndex;
    protected final WorkflowInstanceTokenCountIndexAccessor workflowInstanceIndexAccessor = new WorkflowInstanceTokenCountIndexAccessor();

    protected final ComposedSnapshot composedSnapshot;

    protected LogStreamReader deploymentLogStreamReader;

    protected final BpmnTransformer bpmnTransformer = new BpmnTransformer();

    protected int streamId;
    protected long eventKey;
    protected long eventPosition;

    public WorkflowInstanceStreamProcessor(
            CommandResponseWriter responseWriter,
            IndexStore workflowPositionIndexStore,
            IndexStore workflowVersionIndexStore,
            IndexStore workflowInstanceTokenCountIndexStore)
    {
        this.responseWriter = responseWriter;

        this.workflowPositionIndex = new Bytes2LongHashIndex(workflowPositionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_COMPOSITE_KEY);
        this.latestWorkflowVersionIndex = new Bytes2LongHashIndex(workflowVersionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_PROCESS_ID);
        this.workflowInstanceTokenCountIndex = new Long2LongHashIndex(workflowInstanceTokenCountIndexStore, Short.MAX_VALUE, 256);

        this.composedSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(workflowPositionIndex, workflowPositionIndexStore),
                new HashIndexSnapshotSupport<>(latestWorkflowVersionIndex, workflowVersionIndexStore),
                new HashIndexSnapshotSupport<>(workflowInstanceTokenCountIndex, workflowInstanceTokenCountIndexStore));
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return composedSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.streamId = context.getSourceStream().getId();

        this.deploymentLogStreamReader = new BufferedLogStreamReader(context.getSourceStream());
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == EventType.DEPLOYMENT_EVENT
                || m.getEventType() == EventType.WORKFLOW_EVENT
                || m.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        sourceEventMetadata.reset();
        deploymentEvent.reset();
        workflowInstanceEvent.reset();
        taskEvent.reset();

        eventKey = event.getLongKey();
        eventPosition = event.getPosition();

        event.readMetadata(sourceEventMetadata);

        EventProcessor eventProcessor = null;

        switch (sourceEventMetadata.getEventType())
        {
            case DEPLOYMENT_EVENT:
                eventProcessor = onDeploymentEvent(event);
                break;

            case WORKFLOW_EVENT:
                eventProcessor = onWorkflowEvent(event);
                break;

            case TASK_EVENT:
                eventProcessor = onTaskEvent(event);
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected EventProcessor onDeploymentEvent(LoggedEvent event)
    {
        EventProcessor eventProcessor = null;

        event.readValue(deploymentEvent);

        switch (deploymentEvent.getEventType())
        {
            case DEPLOYMENT_CREATED:
                eventProcessor = deployedWorkflowEventProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected EventProcessor onWorkflowEvent(LoggedEvent event)
    {
        EventProcessor eventProcessor = null;

        event.readValue(workflowInstanceEvent);

        switch (workflowInstanceEvent.getEventType())
        {
            case CREATE_WORKFLOW_INSTANCE:
                eventProcessor = createWorkflowInstanceEventProcessor;
                break;

            case WORKFLOW_INSTANCE_CREATED:
                eventProcessor = workflowInstanceCreatedEventProcessor;
                break;

            case START_EVENT_OCCURRED:
            case ACTIVITY_COMPLETED:
                eventProcessor = activityCompletedEventProcessor;
                break;

            case SEQUENCE_FLOW_TAKEN:
                eventProcessor = sequenceFlowTakenEventProcessor;
                break;

            case ACTIVITY_ACTIVATED:
                eventProcessor = activityActivatedEventProcessor;
                break;

            case END_EVENT_OCCURRED:
                eventProcessor = endEventProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected EventProcessor onTaskEvent(LoggedEvent event)
    {
        EventProcessor eventProcessor = null;

        event.readValue(taskEvent);

        switch (taskEvent.getEventType())
        {
            case COMPLETED:
                eventProcessor = taskCompletedEventProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    protected ExecutableWorkflow getWorkflow(final DirectBuffer bpmnProcessId, int version)
    {
        ExecutableWorkflow workflow = null;

        final long deploymentEventPosition = workflowPositionIndexAccessor.wrap(bpmnProcessId, version).getEventPosition();

        if (deploymentEventPosition >= 0)
        {
            final boolean found = deploymentLogStreamReader.seek(deploymentEventPosition);

            if (found && deploymentLogStreamReader.hasNext())
            {
                final LoggedEvent deployedWorkflowEvent = deploymentLogStreamReader.next();

                deployedWorkflowEvent.readValue(deploymentEvent);

                // currently, it can only be one
                workflow = bpmnTransformer.transform(deploymentEvent.getBpmnXml()).get(0);
            }
        }

        if (workflow == null)
        {
            throw new RuntimeException("Failed to start workflow instance. No deployment event found.");
        }

        return workflow;
    }

    protected <T extends ExecutableFlowElement> T getCurrentActivity()
    {
        final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
        final int version = workflowInstanceEvent.getVersion();

        final ExecutableWorkflow workflow = getWorkflow(bpmnProcessId, version);

        final DirectBuffer currentActivityId = workflowInstanceEvent.getActivityId();

        return workflow.getChildById(currentActivityId);
    }

    protected long writeWorkflowEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
                .protocolVersion(Constants.PROTOCOL_VERSION)
                .eventType(WORKFLOW_EVENT);

        // TODO: targetEventMetadata.raftTermId(raftTermId);

        // don't forget to set the key or use positionAsKey
        return writer
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowInstanceEvent)
                .tryWrite();
    }

    private final class CreateWorkflowInstanceEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();

            int version = workflowInstanceEvent.getVersion();
            if (version == -1)
            {
                version = (int) latestWorkflowVersionIndex.get(bpmnProcessId, 0, bpmnProcessId.capacity(), -1);
            }

            final long eventPosition = workflowPositionIndexAccessor.wrap(bpmnProcessId, version).getEventPosition();

            final WorkflowInstanceEventType newEventType =
                    eventPosition < 0
                    ? WorkflowInstanceEventType.WORKFLOW_INSTANCE_REJECTED
                    : WorkflowInstanceEventType.WORKFLOW_INSTANCE_CREATED;

            workflowInstanceEvent
                    .setEventType(newEventType)
                    .setWorkflowInstanceKey(eventKey)
                    .setVersion(version);
        }

        @Override
        public boolean executeSideEffects()
        {
            return responseWriter
                    .brokerEventMetadata(sourceEventMetadata)
                    .topicId(streamId)
                    .longKey(eventKey)
                    .eventWriter(workflowInstanceEvent)
                    .tryWriteResponse();
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
            final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
            final int version = workflowInstanceEvent.getVersion();

            final ExecutableWorkflow workflow = getWorkflow(bpmnProcessId, version);

            final ExecutableStartEvent startEvent = workflow.getScopeStartEvent();
            final DirectBuffer activityId = startEvent.getId();

            workflowInstanceEvent
                .setEventType(WorkflowInstanceEventType.START_EVENT_OCCURRED)
                .setWorkflowInstanceKey(eventKey)
                .setActivityId(activityId);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }

        @Override
        public void updateState()
        {
            // a new token is created
            workflowInstanceTokenCountIndex.put(eventKey, 1L);
        }

    }

    private final class ActivityCompletedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final ExecutableFlowNode currentActivity = getCurrentActivity();

            // currently, the activity has exactly one outgoing sequence flow
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

    private final class SequenceFlowTakenEventProcessor implements EventProcessor
    {
        boolean isTokenConsumed;

        @Override
        public void processEvent()
        {
            isTokenConsumed = false;

            final ExecutableSequenceFlow sequenceFlow = getCurrentActivity();
            final ExecutableFlowNode targetNode = sequenceFlow.getTargetNode();

            workflowInstanceEvent.setActivityId(targetNode.getId());

            if (targetNode instanceof ExecutableEndEvent)
            {
                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.END_EVENT_OCCURRED);

                isTokenConsumed = true;
            }
            else if (targetNode instanceof ExecutableServiceTask)
            {
                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_ACTIVATED);
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

        @Override
        public void updateState()
        {
            if (isTokenConsumed)
            {
                workflowInstanceIndexAccessor.decrementActiveTokenCount(workflowInstanceEvent.getWorkflowInstanceKey());
            }
        }
    }

    private final class EndEventProcessor implements EventProcessor
    {
        boolean isCompleted;

        @Override
        public void processEvent()
        {
            isCompleted = false;

            final long activeTokenCount = workflowInstanceIndexAccessor.getActiveTokenCount(workflowInstanceEvent.getWorkflowInstanceKey());
            if (activeTokenCount == 0)
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
                workflowInstanceIndexAccessor.remove(workflowInstanceEvent.getWorkflowInstanceKey());
            }
        }
    }

    private final class ActivityActivatedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final ExecutableFlowElement activty = getCurrentActivity();

            if (activty instanceof ExecutableServiceTask)
            {
                final ExecutableServiceTask serviceTask = (ExecutableServiceTask) activty;
                final TaskMetadata taskMetadata = serviceTask.getTaskMetadata();

                final TaskHeaders taskHeaders = taskEvent.getHeaders()
                    .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                    .setWorkflowDefinitionVersion(workflowInstanceEvent.getVersion())
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

                taskEvent
                    .setEventType(TaskEventType.CREATE)
                    .setType(taskMetadata.getTaskType())
                    .setRetries(taskMetadata.getRetries())
                    .setHeaders(taskHeaders);
            }
            else
            {
                throw new RuntimeException("Currently not supported. An activity must be of type service task.");
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .eventType(TASK_EVENT);

            // TODO: targetEventMetadata.raftTermId(raftTermId);

            return writer
                    .positionAsKey()
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(taskEvent)
                    .tryWrite();
        }
    }

    private final class DeployedWorkflowEventProcessor implements EventProcessor
    {
        protected final UnsafeBuffer writeBuffer = new UnsafeBuffer(new byte[BpmnProcessIdRule.PROCESS_ID_MAX_LENGTH]);

        @Override
        public void processEvent()
        {
            // deployment
            final ArrayValueIterator<DeployedWorkflow> deployedWorkflowArrayValueIterator = deploymentEvent.deployedWorkflows();

            while (deployedWorkflowArrayValueIterator.hasNext())
            {
                final DeployedWorkflow deployedWorkflow = deployedWorkflowArrayValueIterator.next();

                final DirectBuffer bpmnProcessId = deployedWorkflow.getBpmnProcessId();
                bpmnProcessId.getBytes(0, writeBuffer, 0, bpmnProcessId.capacity());

                final int version = deployedWorkflow.getVersion();

                latestWorkflowVersionIndex.put(writeBuffer.byteArray(), version);

                workflowPositionIndexAccessor.wrap(bpmnProcessId, version).putEventPosition(eventPosition);
            }
        }
    }

    private final class TaskCompletedEventProcessor implements EventProcessor
    {
        private TaskHeaders taskHeaders;

        @Override
        public void processEvent()
        {
            taskHeaders = taskEvent.getHeaders();

            // assuming that the workflow instance is still in this activity
            workflowInstanceEvent
                .setEventType(WorkflowInstanceEventType.ACTIVITY_COMPLETED)
                .setBpmnProcessId(taskHeaders.getBpmnProcessId())
                .setVersion(taskHeaders.getWorkflowDefinitionVersion())
                .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                .setActivityId(taskHeaders.getActivityId());
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(
                    writer.key(taskHeaders.getActivityInstanceKey()));
        }
    }

    private final class WorkflowPositionIndexAccessor
    {
        protected static final long MISSING_POSITION = -1L;

        protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[SIZE_OF_COMPOSITE_KEY]);

        public WorkflowPositionIndexAccessor wrap(DirectBuffer bpmnProcessId, int version)
        {
            bpmnProcessId.getBytes(0, buffer, 0, bpmnProcessId.capacity());
            buffer.putInt(bpmnProcessId.capacity(), version, ByteOrder.LITTLE_ENDIAN);

            return this;
        }

        public long getEventPosition()
        {
            return workflowPositionIndex.get(buffer, 0, buffer.capacity(), MISSING_POSITION);
        }

        public void putEventPosition(long eventPosition)
        {
            workflowPositionIndex.put(buffer.byteArray(), eventPosition);
        }
    }

    private final class WorkflowInstanceTokenCountIndexAccessor
    {
        protected static final long MISSING_VALUE = -1L;

        public long getActiveTokenCount(long workflowInstanceKey)
        {
            final long count = workflowInstanceTokenCountIndex.get(workflowInstanceKey, MISSING_VALUE);

            if (count != MISSING_VALUE)
            {
                return count;
            }
            else
            {
                throw new RuntimeException("No index found for workflow instance.");
            }
        }

        public void remove(long workflowInstanceKey)
        {
            workflowInstanceTokenCountIndex.remove(workflowInstanceKey, MISSING_VALUE);
        }

        public void decrementActiveTokenCount(long workflowInstanceKey)
        {
            final long activeTokenCount = getActiveTokenCount(workflowInstanceKey);

            workflowInstanceTokenCountIndex.put(workflowInstanceKey, activeTokenCount - 1);
        }
    }

}
