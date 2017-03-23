package org.camunda.tngp.broker.workflow.processor;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.camunda.tngp.protocol.clientapi.EventType.WORKFLOW_EVENT;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.broker.workflow.data.DeployedWorkflow;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEventType;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEventType;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableProcess;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableStartEvent;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
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

    // data //////////////////////////////////////////

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowDeploymentEvent deploymentEvent = new WorkflowDeploymentEvent();
    protected final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    // internal //////////////////////////////////////

    protected final CommandResponseWriter responseWriter;

    /**
     * An hash index which contains as key the workflow id and as value the corresponding latest version.
     */
    protected final Bytes2LongHashIndex latestWorkflowVersionIndex;

    /**
     * An hash index which contains as key the workflow id and version concatenated
     * and as value the position of the workflow definition.
     */
    protected final Bytes2LongHashIndex workflowPositionIndex;

    protected final WorkflowPositionIndexAccessor workflowPositionIndexAccessor = new WorkflowPositionIndexAccessor();

    protected final ComposedSnapshot composedSnapshot;

    protected LogStreamReader deploymentLogStreamReader;

    protected final BpmnTransformer bpmnTransformer = new BpmnTransformer();

    protected int streamId;
    protected long eventKey;
    protected long eventPosition;

    public WorkflowInstanceStreamProcessor(CommandResponseWriter responseWriter, IndexStore workflowPositionIndexStore, IndexStore workflowVersionIndexStore)
    {
        this.responseWriter = responseWriter;

        this.workflowPositionIndex = new Bytes2LongHashIndex(workflowPositionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_COMPOSITE_KEY);
        this.latestWorkflowVersionIndex = new Bytes2LongHashIndex(workflowVersionIndexStore, Short.MAX_VALUE, 64, SIZE_OF_PROCESS_ID);

        this.composedSnapshot = new ComposedSnapshot(
                new HashIndexSnapshotSupport<>(workflowPositionIndex, workflowPositionIndexStore),
                new HashIndexSnapshotSupport<>(latestWorkflowVersionIndex, workflowVersionIndexStore));
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
        return m -> m.getEventType() == EventType.DEPLOYMENT_EVENT || m.getEventType() == EventType.WORKFLOW_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getLongKey();
        eventPosition = event.getPosition();

        event.readMetadata(sourceEventMetadata);

        EventProcessor eventProcessor = null;
        if (sourceEventMetadata.getEventType() == EventType.DEPLOYMENT_EVENT)
        {
            event.readValue(deploymentEvent);
            if (deploymentEvent.getEventType() == WorkflowDeploymentEventType.DEPLOYMENT_CREATED)
            {
                eventProcessor = deployedWorkflowEventProcessor;
            }
        }
        else if (sourceEventMetadata.getEventType() == EventType.WORKFLOW_EVENT)
        {
            event.readValue(workflowInstanceEvent);

            switch (workflowInstanceEvent.getEventType())
            {
                case CREATE_WORKFLOW_INSTANCE:
                    eventProcessor = createWorkflowInstanceEventProcessor;
                    break;

                case WORKFLOW_INSTANCE_CREATED:
                    eventProcessor = workflowInstanceCreatedEventProcessor;
                    break;

                default:
                    break;
            }
        }
        return eventProcessor;
    }

    @Override
    public void afterEvent()
    {
        sourceEventMetadata.reset();
        deploymentEvent.reset();
        workflowInstanceEvent.reset();
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
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .eventType(WORKFLOW_EVENT);

            // TODO: targetEventMetadata.raftTermId(raftTermId);

            return writer
                    .key(eventKey)
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(workflowInstanceEvent)
                    .tryWrite();
        }
    }

    private final class WorkflowInstanceCreatedEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
            final int version = workflowInstanceEvent.getVersion();

            final ExecutableProcess process = getProcess(bpmnProcessId, version);

            final ExecutableStartEvent startEvent = process.getScopeStartEvent();
            final String activityId = startEvent.getId();

            workflowInstanceEvent
                .setEventType(WorkflowInstanceEventType.EVENT_OCCURRED)
                .setWorkflowInstanceKey(eventKey)
                .setActivityId(activityId);
        }

        protected ExecutableProcess getProcess(final DirectBuffer bpmnProcessId, int version)
        {
            ExecutableProcess process = null;

            final long deploymentEventPosition = workflowPositionIndexAccessor.wrap(bpmnProcessId, version).getEventPosition();

            if (deploymentEventPosition >= 0)
            {
                final boolean found = deploymentLogStreamReader.seek(deploymentEventPosition);

                if (found && deploymentLogStreamReader.hasNext())
                {
                    final LoggedEvent deployedWorkflowEvent = deploymentLogStreamReader.next();

                    deployedWorkflowEvent.readValue(deploymentEvent);

                    // currently, it can only be one
                    process = bpmnTransformer.transform(deploymentEvent.getBpmnXml()).get(0);
                }
            }

            if (process == null)
            {
                throw new RuntimeException("Failed to start workflow instance. No deployment event found.");
            }

            return process;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            targetEventMetadata
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .eventType(WORKFLOW_EVENT);

            // TODO: targetEventMetadata.raftTermId(raftTermId);

            return writer
                    .positionAsKey()
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(workflowInstanceEvent)
                    .tryWrite();
        }

        @Override
        public void updateState()
        {
            // TODO increment workflow instance token count
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

    private final class WorkflowPositionIndexAccessor
    {
        protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[SIZE_OF_COMPOSITE_KEY]);

        public WorkflowPositionIndexAccessor wrap(DirectBuffer bpmnProcessId, int version)
        {
            bpmnProcessId.getBytes(0, buffer, 0, bpmnProcessId.capacity());
            buffer.putInt(bpmnProcessId.capacity(), version, ByteOrder.LITTLE_ENDIAN);

            workflowPositionIndex.get(buffer, 0, buffer.capacity(), -1);

            return this;
        }

        public long getEventPosition()
        {
            return workflowPositionIndex.get(buffer, 0, buffer.capacity(), -1);
        }

        public void putEventPosition(long eventPosition)
        {
            workflowPositionIndex.put(buffer.byteArray(), eventPosition);
        }
    }

}
