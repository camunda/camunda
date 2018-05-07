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

import static io.zeebe.broker.util.PayloadUtil.isNilPayload;
import static io.zeebe.broker.util.PayloadUtil.isValidPayload;
import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.protocol.clientapi.EventType.WORKFLOW_INSTANCE_EVENT;

import java.util.*;
import java.util.function.Consumer;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.incident.IncidentEventWriter;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskHeaders;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.broker.workflow.map.*;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex.WorkflowInstance;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.model.bpmn.BpmnAspect;
import io.zeebe.model.bpmn.instance.*;
import io.zeebe.msgpack.el.*;
import io.zeebe.msgpack.mapping.*;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceStreamProcessor implements StreamProcessor
{
    private static final UnsafeBuffer EMPTY_TASK_TYPE = new UnsafeBuffer("".getBytes());

    // processors ////////////////////////////////////

    protected final CreateWorkflowInstanceEventProcessor createWorkflowInstanceEventProcessor = new CreateWorkflowInstanceEventProcessor();
    protected final WorkflowInstanceCreatedEventProcessor workflowInstanceCreatedEventProcessor = new WorkflowInstanceCreatedEventProcessor();
    protected final WorkflowInstanceRejectedEventProcessor workflowInstanceRejectedEventProcessor = new WorkflowInstanceRejectedEventProcessor();
    protected final CancelWorkflowInstanceProcessor cancelWorkflowInstanceProcessor = new CancelWorkflowInstanceProcessor();

    protected final EventProcessor updatePayloadProcessor = new UpdatePayloadProcessor();

    protected final EventProcessor sequenceFlowTakenEventProcessor = new ActiveWorkflowInstanceProcessor(new SequenceFlowTakenEventProcessor());
    protected final EventProcessor activityReadyEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityReadyEventProcessor());
    protected final EventProcessor activityActivatedEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityActivatedEventProcessor());
    protected final EventProcessor activityCompletingEventProcessor = new ActiveWorkflowInstanceProcessor(new ActivityCompletingEventProcessor());
    protected final EventProcessor bpmnAspectEventProcessor = new BpmnAspectEventProcessor();

    protected final EventProcessor taskCompletedEventProcessor = new TaskCompletedEventProcessor();
    protected final EventProcessor taskCreatedEventProcessor = new TaskCreatedProcessor();

    protected Metric workflowInstanceEventCreate;
    protected Metric workflowInstanceEventCanceled;
    protected Metric workflowInstanceEventCompleted;

    // data //////////////////////////////////////////

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    protected final TaskEvent taskEvent = new TaskEvent();

    // internal //////////////////////////////////////

    protected final CommandResponseWriter responseWriter;

    protected final WorkflowInstanceIndex workflowInstanceIndex;
    protected final ActivityInstanceMap activityInstanceMap;
    protected final PayloadCache payloadCache;

    protected WorkflowCache workflowDeploymentCache;

    protected final ComposedSnapshot composedSnapshot;

    protected LogStreamReader logStreamReader;
    protected LogStreamBatchWriter logStreamBatchWriter;
    protected IncidentEventWriter incidentEventWriter;

    protected int logStreamPartitionId;
    protected int streamProcessorId;
    protected long eventKey;
    protected long eventPosition;

    protected final MappingProcessor payloadMappingProcessor;
    protected final JsonConditionInterpreter conditionInterpreter = new JsonConditionInterpreter();

    protected LogStream logStream;

    private ClientTransport managementApiClient;
    private TopologyManager topologyManager;

    private ActorControl actor;

    public WorkflowInstanceStreamProcessor(
            CommandResponseWriter responseWriter,
            ClientTransport managementApiClient,
            TopologyManager topologyManager,
            int payloadCacheSize)
    {
        this.responseWriter = responseWriter;
        this.managementApiClient = managementApiClient;
        this.topologyManager = topologyManager;
        this.logStreamReader = new BufferedLogStreamReader();

        this.payloadCache = new PayloadCache(payloadCacheSize, logStreamReader);

        this.workflowInstanceIndex = new WorkflowInstanceIndex();
        this.activityInstanceMap = new ActivityInstanceMap();

        this.payloadMappingProcessor = new MappingProcessor(4096);

        this.composedSnapshot = new ComposedSnapshot(
            workflowInstanceIndex.getSnapshotSupport(),
            activityInstanceMap.getSnapshotSupport(),
            payloadCache.getSnapshotSupport());
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return composedSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.actor = context.getActorControl();

        final LogStream logstream = context.getLogStream();
        this.logStreamPartitionId = logstream.getPartitionId();
        this.streamProcessorId = context.getId();

        this.logStreamReader.wrap(logstream);
        this.logStreamBatchWriter = new LogStreamBatchWriterImpl(logstream);
        this.incidentEventWriter = new IncidentEventWriter(sourceEventMetadata, workflowInstanceEvent);

        this.logStream = logstream;

        this.workflowDeploymentCache = new WorkflowCache(managementApiClient,
            topologyManager,
            logstream.getTopicName());

        final MetricsManager metricsManager = context.getActorScheduler().getMetricsManager();
        final String topicName = logstream.getTopicName().getStringWithoutLengthUtf8(0, logstream.getTopicName().capacity());
        final String partitionId = Integer.toString(logstream.getPartitionId());

        workflowInstanceEventCreate = metricsManager.newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "created")
            .create();

        workflowInstanceEventCanceled = metricsManager.newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "canceled")
            .create();

        workflowInstanceEventCompleted = metricsManager.newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "completed")
            .create();
    }

    @Override
    public void onClose()
    {
        workflowInstanceIndex.close();
        activityInstanceMap.close();
        payloadCache.close();
        logStreamReader.close();

        workflowInstanceEventCreate.close();
        workflowInstanceEventCanceled.close();
        workflowInstanceEventCompleted.close();
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == EventType.WORKFLOW_INSTANCE_EVENT
            || m.getEventType() == EventType.TASK_EVENT;
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

            default:
                break;
        }

        return eventProcessor;
    }

    protected void reset()
    {
        activityInstanceMap.reset();
    }

    protected EventProcessor onWorkflowInstanceEvent(LoggedEvent event)
    {
        workflowInstanceEvent.reset();
        event.readValue(workflowInstanceEvent);

        EventProcessor eventProcessor = null;
        switch (workflowInstanceEvent.getState())
        {
            case CREATE_WORKFLOW_INSTANCE:
                eventProcessor = createWorkflowInstanceEventProcessor;
                break;

            case WORKFLOW_INSTANCE_CREATED:
                eventProcessor = workflowInstanceCreatedEventProcessor;
                workflowInstanceEventCreate.incrementOrdered();
                break;

            case WORKFLOW_INSTANCE_REJECTED:
                eventProcessor = workflowInstanceRejectedEventProcessor;
                break;

            case CANCEL_WORKFLOW_INSTANCE:
                eventProcessor = cancelWorkflowInstanceProcessor;
                break;

            case WORKFLOW_INSTANCE_CANCELED:
                workflowInstanceEventCanceled.incrementOrdered();
                break;

            case WORKFLOW_INSTANCE_COMPLETED:
                workflowInstanceEventCompleted.incrementOrdered();
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
            case GATEWAY_ACTIVATED:
            case ACTIVITY_COMPLETED:
            {
                eventProcessor = bpmnAspectEventProcessor;
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

        switch (taskEvent.getState())
        {
            case CREATED:
                return taskCreatedEventProcessor;

            case COMPLETED:
                return taskCompletedEventProcessor;

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

    protected long writeWorkflowEvent(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(WORKFLOW_INSTANCE_EVENT);

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
                .eventType(TASK_EVENT);

        // don't forget to set the key or use positionAsKey
        return writer
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .tryWrite();
    }

    protected boolean sendWorkflowInstanceResponse()
    {
        return responseWriter
                .partitionId(logStreamPartitionId)
                .position(eventPosition)
                .key(eventKey)
                .eventWriter(workflowInstanceEvent)
                .tryWriteResponse(sourceEventMetadata.getRequestStreamId(), sourceEventMetadata.getRequestId());
    }

    private final class CreateWorkflowInstanceEventProcessor implements EventProcessor
    {
        @Override
        public void processEvent(EventLifecycleContext ctx)
        {
            workflowInstanceEvent.setWorkflowInstanceKey(eventKey);

            final DirectBuffer payload = workflowInstanceEvent.getPayload();

            if (!(isNilPayload(payload) || isValidPayload(payload)))
            {
                workflowInstanceEvent.setState(WorkflowInstanceState.WORKFLOW_INSTANCE_REJECTED);
            }
            else
            {
                resolveWorkflowDefinition(ctx);
            }
        }

        private void resolveWorkflowDefinition(EventLifecycleContext ctx)
        {
            final long workflowKey = workflowInstanceEvent.getWorkflowKey();
            final DirectBuffer bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
            final int version = workflowInstanceEvent.getVersion();

            ActorFuture<ClientResponse> fetchWorkflowFuture = null;

            if (workflowKey <= 0)
            {
                // by bpmn process id and version
                if (version > 0)
                {
                    final DeployedWorkflow deployedWorkflow = workflowDeploymentCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);

                    if (deployedWorkflow != null)
                    {
                        workflowInstanceEvent
                            .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED)
                            .setWorkflowKey(deployedWorkflow.getKey());
                    }
                    else
                    {
                        fetchWorkflowFuture = workflowDeploymentCache.fetchWorkflowByBpmnProcessIdAndVersion(bpmnProcessId, version);
                    }
                }

                // latest by bpmn process id
                else
                {
                    final DeployedWorkflow deployedWorkflow = workflowDeploymentCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);

                    if (deployedWorkflow != null && version != -2)
                    {
                        workflowInstanceEvent
                            .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED)
                            .setVersion(deployedWorkflow.getVersion())
                            .setWorkflowKey(deployedWorkflow.getKey());
                    }
                    else
                    {
                        fetchWorkflowFuture = workflowDeploymentCache.fetchLatestWorkflowByBpmnProcessId(bpmnProcessId);
                    }
                }
            }

            // by key
            else
            {
                final DeployedWorkflow deployedWorkflow = workflowDeploymentCache.getWorkflowByKey(workflowKey);

                if (deployedWorkflow != null)
                {
                    workflowInstanceEvent
                        .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED)
                        .setVersion(deployedWorkflow.getVersion())
                        .setBpmnProcessId(deployedWorkflow.getWorkflow().getBpmnProcessId());
                }
                else
                {
                    fetchWorkflowFuture = workflowDeploymentCache.fetchWorkflowByKey(workflowKey);
                }
            }

            if (fetchWorkflowFuture != null)
            {
                final ActorFuture<Void> workflowFetchedFuture = new CompletableActorFuture<>();
                ctx.async(workflowFetchedFuture);

                actor.runOnCompletion(fetchWorkflowFuture, (response, err) ->
                {
                    if (err != null)
                    {
                        workflowInstanceEvent
                            .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_REJECTED);
                    }
                    else
                    {
                        try
                        {
                            final DeployedWorkflow workflowDefinition = workflowDeploymentCache.addWorkflow(response.getResponseBuffer());

                            if (workflowDefinition != null)
                            {
                                workflowInstanceEvent
                                    .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED)
                                    .setBpmnProcessId(workflowDefinition.getWorkflow().getBpmnProcessId())
                                    .setWorkflowKey(workflowDefinition.getKey())
                                    .setVersion(workflowDefinition.getVersion());
                            }
                            else
                            {
                                // workflow not deployed
                                workflowInstanceEvent
                                    .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_REJECTED);
                            }
                        }
                        finally
                        {
                            response.close();
                        }
                    }

                    workflowFetchedFuture.complete(null);
                });
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            logStreamBatchWriter.reset();

            logStreamBatchWriter
                .producerId(streamProcessorId)
                .sourceEvent(logStreamPartitionId, eventPosition);

            writeWorkflowInstanceEvent();

            if (workflowInstanceEvent.getState() == WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED)
            {
                writeStartEventOccured();
            }

            return logStreamBatchWriter.tryWrite();
        }

        private void writeWorkflowInstanceEvent()
        {
            final LogEntryBuilder workflowInstanceCompletedBuilder = logStreamBatchWriter.event();

            // carry-over request metadata since response will be sent on event
            targetEventMetadata.reset()
                .requestId(sourceEventMetadata.getRequestId())
                .requestStreamId(sourceEventMetadata.getRequestStreamId())
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(WORKFLOW_INSTANCE_EVENT);

            workflowInstanceCompletedBuilder
                .key(eventKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowInstanceEvent)
                .done();
        }

        private void writeStartEventOccured()
        {
            final Workflow workflow = workflowDeploymentCache.getWorkflowByKey(workflowInstanceEvent.getWorkflowKey()).getWorkflow();
            final StartEvent startEvent = workflow.getInitialStartEvent();
            final DirectBuffer activityId = startEvent.getIdAsBuffer();

            workflowInstanceEvent
                .setState(WorkflowInstanceState.START_EVENT_OCCURRED)
                .setWorkflowInstanceKey(eventKey)
                .setActivityId(activityId);

            final LogEntryBuilder startEventOccuredBuilder = logStreamBatchWriter.event();

            targetEventMetadata.reset()
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(WORKFLOW_INSTANCE_EVENT);

            startEventOccuredBuilder
                .positionAsKey()
                .metadataWriter(targetEventMetadata)
                .valueWriter(workflowInstanceEvent)
                .done();
        }
    }

    private final class WorkflowInstanceCreatedEventProcessor implements EventProcessor
    {
        @Override
        public boolean executeSideEffects()
        {
            return sendWorkflowInstanceResponse();
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndex
                .newWorkflowInstance(eventKey)
                .setPosition(eventPosition)
                .setActiveTokenCount(1)
                .setActivityInstanceKey(-1L)
                .setWorkflowKey(workflowInstanceEvent.getWorkflowKey())
                .write();
        }
    }

    private final class WorkflowInstanceRejectedEventProcessor implements EventProcessor
    {
        @Override
        public boolean executeSideEffects()
        {
            return sendWorkflowInstanceResponse();
        }
    }

    @SuppressWarnings("rawtypes")
    private final class BpmnAspectEventProcessor extends FlowElementEventProcessor<FlowElement>
    {
        private FlowElementEventProcessor delegate;

        protected final Map<BpmnAspect, FlowElementEventProcessor> aspectHandlers;

        private BpmnAspectEventProcessor()
        {
            aspectHandlers = new EnumMap<>(BpmnAspect.class);

            aspectHandlers.put(BpmnAspect.TAKE_SEQUENCE_FLOW, new ActiveWorkflowInstanceProcessor(new TakeSequenceFlowAspectHandler()));
            aspectHandlers.put(BpmnAspect.CONSUME_TOKEN, new ActiveWorkflowInstanceProcessor(new ConsumeTokenAspectHandler()));
            aspectHandlers.put(BpmnAspect.EXCLUSIVE_SPLIT, new ActiveWorkflowInstanceProcessor(new ExclusiveSplitAspectHandler()));
        }

        @Override
        @SuppressWarnings("unchecked")
        void processFlowElementEvent(FlowElement currentFlowNode)
        {
            final BpmnAspect bpmnAspect = currentFlowNode.getBpmnAspect();

            delegate = aspectHandlers.get(bpmnAspect);

            delegate.processFlowElementEvent(currentFlowNode);
        }

        @Override
        public boolean executeSideEffects()
        {
            return delegate.executeSideEffects();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return delegate.writeEvent(writer);
        }

        @Override
        public void updateState()
        {
            delegate.updateState();
        }
    }

    private abstract class FlowElementEventProcessor<T extends FlowElement> implements EventProcessor
    {
        @Override
        public void processEvent(EventLifecycleContext ctx)
        {
            final long workflowKey = workflowInstanceEvent.getWorkflowKey();
            final DeployedWorkflow deployedWorkflow = workflowDeploymentCache.getWorkflowByKey(workflowKey);

            if (deployedWorkflow == null)
            {
                fetchWorkflow(workflowKey, this::resolveCurrentFlowNode, ctx);
            }
            else
            {
                resolveCurrentFlowNode(deployedWorkflow);
            }
        }

        @SuppressWarnings("unchecked")
        private void resolveCurrentFlowNode(DeployedWorkflow deployedWorkflow)
        {
            final DirectBuffer currentActivityId = workflowInstanceEvent.getActivityId();

            final Workflow workflow = deployedWorkflow.getWorkflow();
            final FlowElement flowElement = workflow.findFlowElementById(currentActivityId);

            processFlowElementEvent((T) flowElement);
        }

        abstract void processFlowElementEvent(T currentFlowNode);
    }

    private final class TakeSequenceFlowAspectHandler extends FlowElementEventProcessor<FlowNode>
    {
        @Override
        void processFlowElementEvent(FlowNode currentFlowNode)
        {
            // the activity has exactly one outgoing sequence flow
            final SequenceFlow sequenceFlow = currentFlowNode.getOutgoingSequenceFlows().get(0);

            workflowInstanceEvent
                .setState(WorkflowInstanceState.SEQUENCE_FLOW_TAKEN)
                .setActivityId(sequenceFlow.getIdAsBuffer());
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }
    }

    private final class ExclusiveSplitAspectHandler extends FlowElementEventProcessor<ExclusiveGateway>
    {
        private boolean hasIncident;

        @Override
        void processFlowElementEvent(ExclusiveGateway exclusiveGateway)
        {
            try
            {
                final SequenceFlow sequenceFlow = getSequenceFlowWithFulfilledCondition(exclusiveGateway);

                if (sequenceFlow != null)
                {
                    workflowInstanceEvent
                        .setState(WorkflowInstanceState.SEQUENCE_FLOW_TAKEN)
                        .setActivityId(sequenceFlow.getIdAsBuffer());

                    hasIncident = false;
                }
                else
                {
                    incidentEventWriter
                        .reset()
                        .errorType(ErrorType.CONDITION_ERROR)
                        .errorMessage("All conditions evaluated to false and no default flow is set.");

                    hasIncident = true;
                }
            }
            catch (JsonConditionException e)
            {
                incidentEventWriter
                    .reset()
                    .errorType(ErrorType.CONDITION_ERROR)
                    .errorMessage(e.getMessage());

                hasIncident = true;
            }
        }

        private SequenceFlow getSequenceFlowWithFulfilledCondition(ExclusiveGateway exclusiveGateway)
        {
            final List<SequenceFlow> sequenceFlows = exclusiveGateway.getOutgoingSequenceFlowsWithConditions();
            for (int s = 0; s < sequenceFlows.size(); s++)
            {
                final SequenceFlow sequenceFlow = sequenceFlows.get(s);

                final CompiledJsonCondition compiledCondition = sequenceFlow.getCondition();
                final boolean isFulFilled = conditionInterpreter.eval(compiledCondition.getCondition(), workflowInstanceEvent.getPayload());

                if (isFulFilled)
                {
                    return sequenceFlow;
                }
            }
            return exclusiveGateway.getDefaultFlow();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (!hasIncident)
            {
                return writeWorkflowEvent(writer.positionAsKey());
            }
            else
            {
                return incidentEventWriter
                    .failureEventPosition(eventPosition)
                    .activityInstanceKey(eventKey)
                    .tryWrite(writer);
            }
        }
    }

    private final class ConsumeTokenAspectHandler extends FlowElementEventProcessor<FlowElement>
    {
        private boolean isCompleted;
        private int activeTokenCount;


        @Override
        void processFlowElementEvent(FlowElement currentFlowNode)
        {
            isCompleted = false;

            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());

            activeTokenCount = workflowInstance != null ? workflowInstance.getTokenCount() : 0;
            if (activeTokenCount == 1)
            {
                workflowInstanceEvent
                    .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_COMPLETED)
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
        }
    }

    private final class SequenceFlowTakenEventProcessor extends FlowElementEventProcessor<SequenceFlow>
    {
        @Override
        void processFlowElementEvent(SequenceFlow sequenceFlow)
        {
            final FlowNode targetNode = sequenceFlow.getTargetNode();

            workflowInstanceEvent.setActivityId(targetNode.getIdAsBuffer());

            if (targetNode instanceof EndEvent)
            {
                workflowInstanceEvent.setState(WorkflowInstanceState.END_EVENT_OCCURRED);
            }
            else if (targetNode instanceof ServiceTask)
            {
                workflowInstanceEvent.setState(WorkflowInstanceState.ACTIVITY_READY);
            }
            else if (targetNode instanceof ExclusiveGateway)
            {
                workflowInstanceEvent.setState(WorkflowInstanceState.GATEWAY_ACTIVATED);
            }
            else
            {
                throw new RuntimeException(String.format("Flow node of type '%s' is not supported.", targetNode));
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeWorkflowEvent(writer.positionAsKey());
        }
    }

    private final class ActivityReadyEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        private final DirectBuffer sourcePayload = new UnsafeBuffer(0, 0);

        private boolean hasIncident;

        @Override
        void processFlowElementEvent(ServiceTask serviceTask)
        {
            hasIncident = false;

            workflowInstanceEvent.setState(WorkflowInstanceState.ACTIVITY_ACTIVATED);

            setWorkflowInstancePayload(serviceTask.getInputOutputMapping().getInputMappings());
        }

        private void setWorkflowInstancePayload(Mapping[] mappings)
        {
            sourcePayload.wrap(workflowInstanceEvent.getPayload());
            // only if we have no default mapping we have to use the mapping processor
            if (mappings.length > 0)
            {
                try
                {
                    final int resultLen = payloadMappingProcessor.extract(sourcePayload, mappings);
                    final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
                    workflowInstanceEvent.setPayload(buffer, 0, resultLen);
                }
                catch (MappingException e)
                {
                    incidentEventWriter
                        .reset()
                        .errorType(ErrorType.IO_MAPPING_ERROR)
                        .errorMessage(e.getMessage());

                    hasIncident = true;
                }
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (!hasIncident)
            {
                return writeWorkflowEvent(writer.key(eventKey));
            }
            else
            {
                return incidentEventWriter
                        .failureEventPosition(eventPosition)
                        .activityInstanceKey(eventKey)
                        .tryWrite(writer);
            }
        }

        @Override
        public void updateState()
        {
            workflowInstanceIndex
                .get(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityInstanceKey(eventKey)
                .write();

            activityInstanceMap
                .newActivityInstance(eventKey)
                .setActivityId(workflowInstanceEvent.getActivityId())
                .setTaskKey(-1L)
                .write();

            if (!hasIncident && !isNilPayload(sourcePayload))
            {
                payloadCache.addPayload(workflowInstanceEvent.getWorkflowInstanceKey(), eventPosition, sourcePayload);
            }
        }
    }

    private final class ActivityActivatedEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        @Override
        void processFlowElementEvent(ServiceTask serviceTask)
        {
            final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();

            taskEvent.reset();

            taskEvent
                .setState(TaskState.CREATE)
                .setType(taskDefinition.getTypeAsBuffer())
                .setRetries(taskDefinition.getRetries())
                .setPayload(workflowInstanceEvent.getPayload());

            setTaskHeaders(serviceTask);
        }

        private void setTaskHeaders(ServiceTask serviceTask)
        {
            taskEvent.headers()
                .setBpmnProcessId(workflowInstanceEvent.getBpmnProcessId())
                .setWorkflowDefinitionVersion(workflowInstanceEvent.getVersion())
                .setWorkflowKey(workflowInstanceEvent.getWorkflowKey())
                .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityId(serviceTask.getIdAsBuffer())
                .setActivityInstanceKey(eventKey);

            final io.zeebe.model.bpmn.instance.TaskHeaders customHeaders = serviceTask.getTaskHeaders();
            if (!customHeaders.isEmpty())
            {
                taskEvent.setCustomHeaders(customHeaders.asMsgpackEncoded());
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
                final WorkflowInstance workflowInstance = workflowInstanceIndex.get(taskHeaders.getWorkflowInstanceKey());

                isActive = workflowInstance != null && activityInstanceKey == workflowInstance.getActivityInstanceKey();
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
                    .setState(WorkflowInstanceState.ACTIVITY_COMPLETING)
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

    private final class ActivityCompletingEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        private boolean hasIncident;

        @Override
        void processFlowElementEvent(ServiceTask serviceTask)
        {
            hasIncident = false;

            workflowInstanceEvent.setState(WorkflowInstanceState.ACTIVITY_COMPLETED);
            setWorkflowInstancePayload(serviceTask.getInputOutputMapping().getOutputMappings());
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
                    incidentEventWriter
                        .reset()
                        .errorType(ErrorType.IO_MAPPING_ERROR)
                        .errorMessage("Task was completed without an payload - processing of output mapping failed!");

                    hasIncident = true;
                }
                else
                {
                    mergePayload(mappings, workflowInstancePayload, taskPayload);
                }
            }
            else if (isNilPayload)
            {
                // no payload from task complete
                workflowInstanceEvent.setPayload(workflowInstancePayload, 0, workflowInstancePayload.capacity());
            }
        }

        private void mergePayload(Mapping[] mappings, final DirectBuffer workflowInstancePayload, final DirectBuffer taskPayload)
        {
            try
            {
                final int resultLen = payloadMappingProcessor.merge(taskPayload, workflowInstancePayload, mappings);
                final MutableDirectBuffer buffer = payloadMappingProcessor.getResultBuffer();
                workflowInstanceEvent.setPayload(buffer, 0, resultLen);
            }
            catch (MappingException e)
            {
                incidentEventWriter
                    .reset()
                    .errorType(ErrorType.IO_MAPPING_ERROR)
                    .errorMessage(e.getMessage());

                hasIncident = true;
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (!hasIncident)
            {
                return writeWorkflowEvent(writer.key(eventKey));
            }
            else
            {
                return incidentEventWriter
                        .failureEventPosition(eventPosition)
                        .activityInstanceKey(eventKey)
                        .tryWrite(writer);
            }
        }

        @Override
        public void updateState()
        {
            if (!hasIncident)
            {
                workflowInstanceIndex
                    .get(workflowInstanceEvent.getWorkflowInstanceKey())
                    .setActivityInstanceKey(-1L)
                    .write();

                activityInstanceMap.remove(eventKey);
            }
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

            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(eventKey);

            if (workflowInstance != null && workflowInstance.getTokenCount() > 0)
            {
                lookupWorkflowInstanceEvent(workflowInstance.getPosition());

                workflowInstanceEvent
                    .setState(WorkflowInstanceState.WORKFLOW_INSTANCE_CANCELED)
                    .setPayload(WorkflowInstanceEvent.NO_PAYLOAD);

                activityInstanceKey = workflowInstance.getActivityInstanceKey();
                taskKey = activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getTaskKey();

                isCanceled = true;
            }
            else
            {
                workflowInstanceEvent.setState(WorkflowInstanceState.CANCEL_WORKFLOW_INSTANCE_REJECTED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            logStreamBatchWriter
                .producerId(streamProcessorId)
                .sourceEvent(logStreamPartitionId, eventPosition);

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
                .eventType(TASK_EVENT);

            taskEvent.reset();
            taskEvent
                .setState(TaskState.CANCEL)
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
                    .eventType(WORKFLOW_INSTANCE_EVENT);

            activityInstanceEvent.reset();
            activityInstanceEvent
                .setState(WorkflowInstanceState.ACTIVITY_TERMINATED)
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

            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());
            final boolean isActive = workflowInstance != null && workflowInstance.getTokenCount() > 0;

            WorkflowInstanceState workflowInstanceEventType = WorkflowInstanceState.UPDATE_PAYLOAD_REJECTED;
            if (isActive && isValidPayload(workflowInstanceEvent.getPayload()))
            {
                workflowInstanceEventType = WorkflowInstanceState.PAYLOAD_UPDATED;
                isUpdated = true;
            }
            workflowInstanceEvent.setState(workflowInstanceEventType);
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final class ActiveWorkflowInstanceProcessor extends FlowElementEventProcessor<FlowElement>
    {
        private final FlowElementEventProcessor processor;

        private boolean isActive;

        ActiveWorkflowInstanceProcessor(FlowElementEventProcessor processor)
        {
            this.processor = processor;
        }

        @Override
        void processFlowElementEvent(FlowElement currentFlowNode)
        {
            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());
            isActive = workflowInstance != null && workflowInstance.getTokenCount() > 0;

            if (isActive)
            {
                processor.processFlowElementEvent(currentFlowNode);
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

    public void fetchWorkflow(long workflowKey, Consumer<DeployedWorkflow> onFetched, EventLifecycleContext ctx)
    {
        final ActorFuture<ClientResponse> responseFuture = workflowDeploymentCache.fetchWorkflowByKey(workflowKey);
        final ActorFuture<Void> onCompleted = new CompletableActorFuture<>();

        ctx.async(onCompleted);

        actor.runOnCompletion(responseFuture, (response, err) ->
        {
            if (err != null)
            {
                onCompleted.completeExceptionally(new RuntimeException("Could not fetch workflow", err));
            }
            else
            {
                try
                {
                    final DeployedWorkflow workflow = workflowDeploymentCache.addWorkflow(response.getResponseBuffer());

                    onFetched.accept(workflow);

                    onCompleted.complete(null);
                }
                catch (Exception e)
                {
                    onCompleted.completeExceptionally(new RuntimeException("Error while processing fetched workflow", e));
                }
                finally
                {
                    response.close();
                }
            }
        });
    }

}
