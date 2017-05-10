package org.camunda.tngp.broker.workflow.processor;

import static org.agrona.BitUtil.*;
import static org.camunda.tngp.protocol.clientapi.EventType.*;

import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongLruCache;
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
import org.camunda.tngp.broker.workflow.graph.model.BpmnAspect;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableEndEvent;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowElement;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowNode;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableSequenceFlow;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableServiceTask;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableStartEvent;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableWorkflow;
import org.camunda.tngp.broker.workflow.graph.model.metadata.Mapping;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata;
import org.camunda.tngp.broker.workflow.graph.model.metadata.TaskMetadata.TaskHeader;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
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

public class WorkflowInstanceStreamProcessor implements StreamProcessor
{

    private static final int SIZE_OF_PROCESS_ID = BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int SIZE_OF_COMPOSITE_KEY = SIZE_OF_PROCESS_ID + SIZE_OF_INT;

    private static final int MAX_WORKFLOW_INSTANCE_ACTIVITY_COUNT = 64;
    private static final int WORKFLOW_INSTANCE_INDEX_VALUE_SIZE = SIZE_OF_LONG + SIZE_OF_LONG * MAX_WORKFLOW_INSTANCE_ACTIVITY_COUNT;

    private static final int SIZE_OF_ACTIVITY_ID = BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int ACTIVITY_INDEX_VALUE_SIZE = SIZE_OF_LONG + SIZE_OF_ACTIVITY_ID;

    private static final UnsafeBuffer EMPTY_TASK_TYPE = new UnsafeBuffer("".getBytes());

    // processors ////////////////////////////////////
    protected final DeployedWorkflowEventProcessor deployedWorkflowEventProcessor = new DeployedWorkflowEventProcessor();

    protected final CreateWorkflowInstanceEventProcessor createWorkflowInstanceEventProcessor = new CreateWorkflowInstanceEventProcessor();
    protected final WorkflowInstanceCreatedEventProcessor workflowInstanceCreatedEventProcessor = new WorkflowInstanceCreatedEventProcessor();
    protected final CancelWorkflowInstanceProcessor cancelWorkflowInstanceProcessor = new CancelWorkflowInstanceProcessor();

    protected final EventProcessor sequenceFlowTakenEventProcessor = new ActiveWorkflowInstanceProcessor(new SequenceFlowTakenEventProcessor());
    protected final EventProcessor activityReadyEventProcessor;
    protected final EventProcessor activityActivatedEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityActivatedEventProcessor());
    protected final EventProcessor taskCompletedEventProcessor = new TaskCompletedEventProcessor();
    protected final EventProcessor activityCompletingEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityCompletingEventProcessor());

    protected final Map<BpmnAspect, EventProcessor> aspectHandlers;
    {
        aspectHandlers = new EnumMap<>(BpmnAspect.class);

        aspectHandlers.put(BpmnAspect.TAKE_SEQUENCE_FLOW, new ActiveWorkflowInstanceProcessor(new TakeSequenceFlowAspectHandler()));
        aspectHandlers.put(BpmnAspect.CONSUME_TOKEN, new ActiveWorkflowInstanceProcessor(new ConsumeTokenAspectHandler()));
    }

    // data //////////////////////////////////////////

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowDeploymentEvent deploymentEvent = new WorkflowDeploymentEvent();
    protected final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    protected final TaskEvent taskEvent = new TaskEvent();

    protected final WorkflowInstanceEvent lookupWorkflowInstanceEvent = new WorkflowInstanceEvent();

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
    protected final Long2BytesHashIndex workflowInstanceIndex;
    protected final WorkflowInstanceIndexAccessor workflowInstanceIndexAccessor = new WorkflowInstanceIndexAccessor();

    protected final Long2BytesHashIndex activityInstanceIndex;
    protected final ActivityInstanceIndexAccessor activityInstanceIndexAccessor = new ActivityInstanceIndexAccessor();

    /**
     * An hash index which contains as key the workflow instance key and as value the
     * last payload.
     */
    private final Long2BytesHashIndex workflowInstancePayloadIndex;

    protected final ComposedSnapshot composedSnapshot;

    protected LogStreamReader logStreamReader;

    protected final BpmnTransformer bpmnTransformer = new BpmnTransformer();

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;
    protected long eventKey;
    protected long eventPosition;

    /**
     * The workflow LRU cache, which contains the latest used workflow's.
     * The cache has a defined capacity, if the capacity is reached a least used workflow
     * will replaced with a new one. If a workflow is not found in the cache, the deployment partitionId
     * is used to find the deployment and the workflow is parsed and added to the cache.
     */
    protected final LongLruCache<ExecutableWorkflow> workflowCache;

    protected final PayloadMappingProcessor payloadMappingProcessor;

    /**
     * The maxiumum payload size.
     */
    protected final int maxPayloadSize;

    protected LogStream targetStream;

    public WorkflowInstanceStreamProcessor(
            CommandResponseWriter responseWriter,
            IndexStore workflowPositionIndexStore,
            IndexStore workflowVersionIndexStore,
            IndexStore workflowInstanceIndexStore,
            IndexStore activityInstanceIndexStore,
            IndexStore workflowInstancePayloadIndexStore,
            int cacheSize,
            int maxPayloadSize)
    {
        this.responseWriter = responseWriter;

        this.workflowPositionIndex = new Bytes2LongHashIndex(workflowPositionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_COMPOSITE_KEY);
        this.latestWorkflowVersionIndex = new Bytes2LongHashIndex(workflowVersionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_PROCESS_ID);
        this.workflowInstanceIndex = new Long2BytesHashIndex(workflowInstanceIndexStore, Short.MAX_VALUE, 256, WORKFLOW_INSTANCE_INDEX_VALUE_SIZE);
        this.activityInstanceIndex = new Long2BytesHashIndex(activityInstanceIndexStore, Short.MAX_VALUE, 256, ACTIVITY_INDEX_VALUE_SIZE);

        this.maxPayloadSize = maxPayloadSize;
        this.payloadMappingProcessor = new PayloadMappingProcessor(maxPayloadSize);
        this.activityReadyEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityReadyEventProcessor(maxPayloadSize));
        this.workflowInstancePayloadIndex = new Long2BytesHashIndex(workflowInstancePayloadIndexStore, Short.MAX_VALUE, 64, maxPayloadSize + SIZE_OF_INT);

        this.composedSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(workflowPositionIndex, workflowPositionIndexStore),
                new HashIndexSnapshotSupport<>(latestWorkflowVersionIndex, workflowVersionIndexStore),
                new HashIndexSnapshotSupport<>(workflowInstanceIndex, workflowInstanceIndexStore),
                new HashIndexSnapshotSupport<>(activityInstanceIndex, activityInstanceIndexStore),
                new HashIndexSnapshotSupport<>(workflowInstancePayloadIndex, workflowInstancePayloadIndexStore));

        workflowCache = new LongLruCache<>(cacheSize, this::lookupWorkflow, (workflow) ->
        { });
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

        this.logStreamReader = new BufferedLogStreamReader(sourceStream);

        this.targetStream = context.getTargetStream();
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

        eventKey = event.getKey();
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

    protected ExecutableWorkflow lookupWorkflow(long position)
    {
        ExecutableWorkflow workflow = null;
        final boolean found = logStreamReader.seek(position);

        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent deployedWorkflowEvent = logStreamReader.next();

            deployedWorkflowEvent.readValue(deploymentEvent);

            // currently, it can only be one
            workflow = bpmnTransformer.transform(deploymentEvent.getBpmnXml()).get(0);
        }
        return workflow;
    }

    protected ExecutableWorkflow getWorkflow(final DirectBuffer bpmnProcessId, int version)
    {
        ExecutableWorkflow workflow = null;

        final long deploymentEventPosition = workflowPositionIndexAccessor.wrap(bpmnProcessId, version).getEventPosition();

        if (deploymentEventPosition >= 0)
        {
            workflow = workflowCache.lookup(deploymentEventPosition);
        }

        if (workflow == null)
        {
            throw new RuntimeException("Failed to start workflow instance. No deployment event found.");
        }

        return workflow;
    }

    protected void lookupWorkflowInstanceEvent(long position, WorkflowInstanceEvent wfEvent)
    {
        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            wfEvent.reset();
            event.readValue(wfEvent);
        }
        else
        {
            throw new IllegalStateException("workflow instance event not found.");
        }
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
                .eventType(WORKFLOW_EVENT)
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
                .protocolVersion(Constants.PROTOCOL_VERSION)
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
                .brokerEventMetadata(sourceEventMetadata)
                .topicName(logStreamTopicName)
                .partitionId(logStreamPartitionId)
                .key(eventKey)
                .eventWriter(workflowInstanceEvent)
                .tryWriteResponse();
    }

    private void cleanIndexForWorkflowInstance(long workflowInstanceKey)
    {
        workflowInstancePayloadIndex.remove(workflowInstanceKey);
        workflowInstanceIndex.remove(workflowInstanceKey);
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
            workflowInstanceIndexAccessor.newWorkflowInstance(eventKey, eventPosition);
        }
    }

    private final class ActivityReadyEventProcessor implements EventProcessor
    {
        private final MutableDirectBuffer tempPayload;

        ActivityReadyEventProcessor(int maxPayload)
        {
            tempPayload = new UnsafeBuffer(new byte[maxPayload + SIZE_OF_INT]);
        }

        @Override
        public void processEvent()
        {
            final ExecutableFlowElement activty = getCurrentActivity();

            if (activty instanceof ExecutableServiceTask)
            {
                final ExecutableServiceTask serviceTask = (ExecutableServiceTask) activty;

                workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_ACTIVATED);

                setWorkflowInstancePayload(serviceTask.getIoMapping().getInputMappings());
            }
            else
            {
                throw new RuntimeException("Currently not supported. An activity must be of type service task.");
            }
        }

        private void setWorkflowInstancePayload(Mapping[] mappings)
        {
            final DirectBuffer sourcePayload = workflowInstanceEvent.getPayload();
            tempPayload.putInt(0, sourcePayload.capacity(), ByteOrder.LITTLE_ENDIAN);
            sourcePayload.getBytes(0, tempPayload, SIZE_OF_INT, sourcePayload.capacity());

            final int resultLen = payloadMappingProcessor.extractPayload(mappings, sourcePayload);
            final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
            workflowInstanceEvent.setPayload(buffer, 0, resultLen);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            workflowInstancePayloadIndex.put(workflowInstanceEvent.getWorkflowInstanceKey(), tempPayload.byteArray());
        }
    }

    private final class ActivityActivatedEventProcessor implements EventProcessor
    {
        private long taskKey;

        @Override
        public void processEvent()
        {
            final ExecutableServiceTask serviceTask = getCurrentActivity();
            final TaskMetadata taskMetadata = serviceTask.getTaskMetadata();

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
            return taskKey = writeTaskEvent(writer.positionAsKey());
        }

        @Override
        public void updateState()
        {
            activityInstanceIndexAccessor.setTaskKey(eventKey, taskKey);
        }
    }

    private final class SequenceFlowTakenEventProcessor implements EventProcessor
    {
        private boolean isActivityReady;
        private long writtenEventPosition;

        @Override
        public void processEvent()
        {
            isActivityReady = false;

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
                isActivityReady = true;
            }
            else
            {
                throw new RuntimeException("Currently not supported. A sequence flow must end in an end event or service task.");
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writtenEventPosition = writeWorkflowEvent(writer.positionAsKey());
        }

        @Override
        public void updateState()
        {
            if (isActivityReady)
            {
                workflowInstanceIndexAccessor.newActivity(workflowInstanceEvent.getWorkflowInstanceKey(), writtenEventPosition);
                activityInstanceIndexAccessor.newActivityInstance(writtenEventPosition, workflowInstanceEvent.getActivityId());
            }
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
        boolean isCompleted;

        @Override
        public void processEvent()
        {
            isCompleted = false;

            final long activeTokenCount = workflowInstanceIndexAccessor
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
                cleanIndexForWorkflowInstance(workflowInstanceEvent.getWorkflowInstanceKey());
            }
            else
            {
                workflowInstanceIndexAccessor.decrementActiveTokenCount(workflowInstanceEvent.getWorkflowInstanceKey());
            }
        }
    }

    private final class DeployedWorkflowEventProcessor implements EventProcessor
    {
        protected final UnsafeBuffer writeBuffer = new UnsafeBuffer(new byte[BpmnTransformer.ID_MAX_LENGTH]);

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
                    .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                    .setActivityId(taskHeaders.getActivityId())
                    .setPayload(taskEvent.getPayload());

                isActivityCompleted = true;
            }
        }

        private boolean isTaskOpen(long activityInstanceKey)
        {
            // task key = -1 when activity is left
            return activityInstanceIndexAccessor.wrapActivityInstanceKey(activityInstanceKey).getTaskKey() == eventKey;
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
                activityInstanceIndexAccessor.setTaskKey(activityInstanceKey, -1L);
            }
        }
    }

    private final class ActivityCompletingEventProcessor implements EventProcessor
    {
        protected final DirectBuffer tempPayload = new UnsafeBuffer(0, 0);

        @Override
        public void processEvent()
        {
            final ExecutableServiceTask serviceTask = getCurrentActivity();

            workflowInstanceEvent.setEventType(WorkflowInstanceEventType.ACTIVITY_COMPLETED);

            setWorkflowInstancePayload(serviceTask.getIoMapping().getOutputMappings());
        }

        private void setWorkflowInstancePayload(Mapping[] mappings)
        {
            final byte payload[] = workflowInstancePayloadIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());
            tempPayload.wrap(payload);
            final Integer payloadLength = tempPayload.getInt(0);
            tempPayload.wrap(payload, SIZE_OF_INT, payloadLength);

            final int resultLen = payloadMappingProcessor.mergePayloads(mappings, workflowInstanceEvent.getPayload(), tempPayload);
            final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
            workflowInstanceEvent.setPayload(buffer, 0, resultLen);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.key(eventKey));
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndexAccessor.removeActivity(workflowInstanceEvent.getWorkflowInstanceKey(), eventKey);
            activityInstanceIndex.remove(eventKey);
        }
    }

    private final class CancelWorkflowInstanceProcessor implements EventProcessor
    {
        private final WorkflowInstanceEvent activityInstanceEvent = new WorkflowInstanceEvent();

        private boolean isCanceled;
        private long activityInstanceKey;

        @Override
        public void processEvent()
        {
            isCanceled = false;

            workflowInstanceIndexAccessor.wrapWorkflowInstanceKey(eventKey);

            if (workflowInstanceIndexAccessor.getTokenCount() > 0)
            {
                lookupWorkflowInstanceEvent(workflowInstanceIndexAccessor.getPosition(), lookupWorkflowInstanceEvent);

                workflowInstanceEvent
                    .setEventType(WorkflowInstanceEventType.WORKFLOW_INSTANCE_CANCELED)
                    .setBpmnProcessId(lookupWorkflowInstanceEvent.getBpmnProcessId())
                    .setVersion(lookupWorkflowInstanceEvent.getVersion())
                    .setWorkflowInstanceKey(eventKey);

                activityInstanceKey = workflowInstanceIndexAccessor.getActivityInstanceKey();

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
            // TODO write events as batch - see camunda-tngp/logstreams#30
            if (activityInstanceKey > 0)
            {
                activityInstanceIndexAccessor.wrapActivityInstanceKey(activityInstanceKey);

                final long taskKey = activityInstanceIndexAccessor.getTaskKey();
                if (taskKey > 0)
                {
                    writeCancelTaskEvent(writer, taskKey);
                }

                writeTerminateActivityInstanceEvent(writer, activityInstanceKey);
            }

            return writeWorkflowEvent(writer.key(eventKey));
        }

        private void writeCancelTaskEvent(LogStreamWriter writer, long taskKey)
        {
            taskEvent.reset();
            taskEvent
                .setEventType(TaskEventType.CANCEL)
                .setType(EMPTY_TASK_TYPE)
                .headers()
                    .setBpmnProcessId(lookupWorkflowInstanceEvent.getBpmnProcessId())
                    .setWorkflowDefinitionVersion(lookupWorkflowInstanceEvent.getVersion())
                    .setWorkflowInstanceKey(eventKey)
                    .setActivityId(activityInstanceIndexAccessor.getActivityId())
                    .setActivityInstanceKey(activityInstanceKey);

            writeTaskEvent(writer.key(taskKey));
        }

        private void writeTerminateActivityInstanceEvent(LogStreamWriter writer, long activityInstanceKey)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .eventType(WORKFLOW_EVENT)
                    .raftTermId(targetStream.getTerm());

            activityInstanceEvent.reset();
            activityInstanceEvent
                .setEventType(WorkflowInstanceEventType.ACTIVITY_TERMINATED)
                .setBpmnProcessId(lookupWorkflowInstanceEvent.getBpmnProcessId())
                .setVersion(lookupWorkflowInstanceEvent.getVersion())
                .setWorkflowInstanceKey(eventKey)
                .setActivityId(activityInstanceIndexAccessor.getActivityId());

            writer
                .key(activityInstanceKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(activityInstanceEvent)
                .tryWrite();
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
                cleanIndexForWorkflowInstance(eventKey);
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
            isActive = workflowInstanceIndexAccessor
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

    private final class WorkflowInstanceIndexAccessor
    {
        private static final int POSITION_OFFSET = 0;
        private static final int TOKEN_COUNT_OFFSET = POSITION_OFFSET + SIZE_OF_LONG;
        private static final int ACTIVITY_INSTANCE_KEY_OFFSET = TOKEN_COUNT_OFFSET + SIZE_OF_INT;

        protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[WORKFLOW_INSTANCE_INDEX_VALUE_SIZE]);

        protected boolean isRead = false;

        public WorkflowInstanceIndexAccessor wrapWorkflowInstanceKey(long key)
        {
            isRead = false;

            final byte[] indexValue = workflowInstanceIndex.get(key);
            if (indexValue != null)
            {
                buffer.wrap(indexValue);
                isRead = true;
            }

            return this;
        }

        public long getPosition()
        {
            return isRead ? buffer.getLong(POSITION_OFFSET, ByteOrder.LITTLE_ENDIAN) : -1L;
        }

        public int getTokenCount()
        {
            return isRead ? buffer.getInt(TOKEN_COUNT_OFFSET, ByteOrder.LITTLE_ENDIAN) : -1;
        }

        public long getActivityInstanceKey()
        {
            return isRead ? buffer.getLong(ACTIVITY_INSTANCE_KEY_OFFSET, ByteOrder.LITTLE_ENDIAN) : -1L;
        }

        public void newWorkflowInstance(long workflowInstanceKey, long position)
        {
            buffer.putLong(POSITION_OFFSET, position, ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(TOKEN_COUNT_OFFSET, 1, ByteOrder.LITTLE_ENDIAN);

            workflowInstanceIndex.put(workflowInstanceKey, buffer.byteArray());
        }

        public void newActivity(long workflowInstanceKey, long activityInstanceKey)
        {
            wrapWorkflowInstanceKey(workflowInstanceKey);

            buffer.putLong(ACTIVITY_INSTANCE_KEY_OFFSET, activityInstanceKey, ByteOrder.LITTLE_ENDIAN);

            workflowInstanceIndex.put(workflowInstanceKey, buffer.byteArray());
        }

        public void removeActivity(long workflowInstanceKey, long activityInstanceKey)
        {
            wrapWorkflowInstanceKey(workflowInstanceKey);

            buffer.putLong(ACTIVITY_INSTANCE_KEY_OFFSET, -1L, ByteOrder.LITTLE_ENDIAN);

            workflowInstanceIndex.put(workflowInstanceKey, buffer.byteArray());
        }

        public void decrementActiveTokenCount(long workflowInstanceKey)
        {
            final int activeTokenCount = getTokenCount();

            buffer.putInt(TOKEN_COUNT_OFFSET, activeTokenCount, ByteOrder.LITTLE_ENDIAN);

            workflowInstanceIndex.put(workflowInstanceKey, buffer.byteArray());
        }
    }

    private final class ActivityInstanceIndexAccessor
    {
        private static final int TASK_KEY_OFFSET = 0;
        private static final int ACTIVITY_ID_LENGTH_OFFSET = TASK_KEY_OFFSET + SIZE_OF_LONG;
        private static final int ACTIVITY_ID_OFFSET = ACTIVITY_ID_LENGTH_OFFSET + SIZE_OF_INT;

        private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[ACTIVITY_INDEX_VALUE_SIZE]);
        private final UnsafeBuffer activityIdBuffer = new UnsafeBuffer(new byte[SIZE_OF_ACTIVITY_ID]);

        private boolean isRead = false;

        public ActivityInstanceIndexAccessor wrapActivityInstanceKey(long key)
        {
            isRead = false;

            final byte[] indexValue = activityInstanceIndex.get(key);
            if (indexValue != null)
            {
                buffer.wrap(indexValue);
                isRead = true;
            }

            return this;
        }

        public long getTaskKey()
        {
            return isRead ? buffer.getLong(TASK_KEY_OFFSET, ByteOrder.LITTLE_ENDIAN) : -1L;
        }

        public DirectBuffer getActivityId()
        {
            if (isRead)
            {
                final int length = buffer.getInt(ACTIVITY_ID_LENGTH_OFFSET, ByteOrder.LITTLE_ENDIAN);

                activityIdBuffer.wrap(buffer, ACTIVITY_ID_OFFSET, length);
            }
            else
            {
                activityIdBuffer.wrap(0, 0);
            }

            return activityIdBuffer;
        }

        public void newActivityInstance(long activityInstanceKey, DirectBuffer activityId)
        {
            buffer.putLong(TASK_KEY_OFFSET, -1L, ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(ACTIVITY_ID_LENGTH_OFFSET, activityId.capacity(), ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(ACTIVITY_ID_OFFSET, activityId, 0, activityId.capacity());

            activityInstanceIndex.put(activityInstanceKey, buffer.byteArray());
        }

        public void setTaskKey(long activityInstanceKey, long taskKey)
        {
            wrapActivityInstanceKey(activityInstanceKey);

            buffer.putLong(TASK_KEY_OFFSET, taskKey, ByteOrder.LITTLE_ENDIAN);

            activityInstanceIndex.put(activityInstanceKey, buffer.byteArray());
        }
    }

}
