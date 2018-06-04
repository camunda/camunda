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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.job.data.JobHeaders;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.ActivityInstanceMap;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.PayloadCache;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex.WorkflowInstance;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.model.bpmn.BpmnAspect;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TaskDefinition;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionException;
import io.zeebe.msgpack.el.JsonConditionInterpreter;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.msgpack.mapping.MappingProcessor;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class WorkflowInstanceStreamProcessor implements StreamProcessorLifecycleAware
{
    private static final UnsafeBuffer EMPTY_JOB_TYPE = new UnsafeBuffer("".getBytes());

    private Metric workflowInstanceEventCreate;
    private Metric workflowInstanceEventCanceled;
    private Metric workflowInstanceEventCompleted;

    private final WorkflowInstanceIndex workflowInstanceIndex = new WorkflowInstanceIndex();
    private final ActivityInstanceMap activityInstanceMap = new ActivityInstanceMap();
    private final PayloadCache payloadCache;

    private final MappingProcessor payloadMappingProcessor = new MappingProcessor(4096);
    private final JsonConditionInterpreter conditionInterpreter = new JsonConditionInterpreter();

    private ClientTransport managementApiClient;
    private TopologyManager topologyManager;
    private WorkflowCache workflowCache;

    private ActorControl actor;

    public WorkflowInstanceStreamProcessor(
        ClientTransport managementApiClient,
        TopologyManager topologyManager,
        int payloadCacheSize)
    {
        this.managementApiClient = managementApiClient;
        this.payloadCache = new PayloadCache(payloadCacheSize);
        this.topologyManager = topologyManager;
    }

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment)
    {
        final BpmnAspectEventProcessor bpmnAspectProcessor = new BpmnAspectEventProcessor();

        return environment.newStreamProcessor()
            .onCommand(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE, new CreateWorkflowInstanceEventProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATED, new WorkflowInstanceCreatedEventProcessor())
            .onRejection(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE, new WorkflowInstanceRejectedEventProcessor())
            .onCommand(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL, new CancelWorkflowInstanceProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, w -> isActive(w.getWorkflowInstanceKey()), new SequenceFlowTakenEventProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ACTIVITY_READY, w -> isActive(w.getWorkflowInstanceKey()), new ActivityReadyEventProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ACTIVITY_ACTIVATED, w -> isActive(w.getWorkflowInstanceKey()), new ActivityActivatedEventProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ACTIVITY_COMPLETING, w -> isActive(w.getWorkflowInstanceKey()), new ActivityCompletingEventProcessor())
            .onCommand(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD, new UpdatePayloadProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.START_EVENT_OCCURRED, w -> isActive(w.getWorkflowInstanceKey()), bpmnAspectProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.END_EVENT_OCCURRED, w -> isActive(w.getWorkflowInstanceKey()), bpmnAspectProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.GATEWAY_ACTIVATED, w -> isActive(w.getWorkflowInstanceKey()), bpmnAspectProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ACTIVITY_COMPLETED, w -> isActive(w.getWorkflowInstanceKey()), bpmnAspectProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCELED, (Consumer<WorkflowInstanceRecord>) (e) -> workflowInstanceEventCanceled.incrementOrdered())
            .onEvent(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.COMPLETED, (Consumer<WorkflowInstanceRecord>) (e) -> workflowInstanceEventCompleted.incrementOrdered())

            .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor())
            .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor())

            .withStateResource(workflowInstanceIndex.getMap())
            .withStateResource(activityInstanceMap.getMap())
            .withStateResource(payloadCache.getMap())

            .withListener(payloadCache)
            .withListener(this)
            .build();
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {

        this.actor = streamProcessor.getActor();
        final LogStream logStream = streamProcessor.getEnvironment().getStream();
        this.workflowCache = new WorkflowCache(managementApiClient,
            topologyManager,
            logStream.getTopicName());

        final StreamProcessorContext context = streamProcessor.getStreamProcessorContext();
        final MetricsManager metricsManager = context.getActorScheduler().getMetricsManager();
        final String topicName = logStream.getTopicName().getStringWithoutLengthUtf8(0, logStream.getTopicName().capacity());
        final String partitionId = Integer.toString(logStream.getPartitionId());

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
        workflowCache.close();
        workflowInstanceEventCreate.close();
        workflowInstanceEventCanceled.close();
        workflowInstanceEventCompleted.close();
    }

    private boolean isActive(long workflowInstanceKey)
    {
        final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceKey);
        return workflowInstance != null && workflowInstance.getTokenCount() > 0;
    }

    private final class CreateWorkflowInstanceEventProcessor implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        private boolean accepted;
        private final WorkflowInstanceRecord startEventRecord = new WorkflowInstanceRecord();

        private long requestId;
        private int requestStreamId;

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceRecord> command, EventLifecycleContext ctx)
        {
            accepted = false;
            final WorkflowInstanceRecord workflowInstanceCommand = command.getValue();

            this.requestId = command.getMetadata().getRequestId();
            this.requestStreamId = command.getMetadata().getRequestStreamId();

            workflowInstanceCommand.setWorkflowInstanceKey(command.getKey());

            final DirectBuffer payload = workflowInstanceCommand.getPayload();

            if (isNilPayload(payload) || isValidPayload(payload))
            {
                accepted = true;
                resolveWorkflowDefinition(workflowInstanceCommand, ctx);
            }
        }

        private void addRequestMetadata(RecordMetadata metadata)
        {
            metadata.requestId(requestId).requestStreamId(requestStreamId);
        }

        private void resolveWorkflowDefinition(WorkflowInstanceRecord command, EventLifecycleContext ctx)
        {
            final long workflowKey = command.getWorkflowKey();
            final DirectBuffer bpmnProcessId = command.getBpmnProcessId();
            final int version = command.getVersion();

            ActorFuture<ClientResponse> fetchWorkflowFuture = null;

            if (workflowKey <= 0)
            {
                // by bpmn process id and version
                if (version > 0)
                {
                    final DeployedWorkflow workflowDefinition = workflowCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);

                    if (workflowDefinition != null)
                    {
                        command.setWorkflowKey(workflowDefinition.getKey());
                        accepted = true;
                    }
                    else
                    {
                        fetchWorkflowFuture = workflowCache.fetchWorkflowByBpmnProcessIdAndVersion(bpmnProcessId, version);
                    }
                }

                // latest by bpmn process id
                else
                {
                    final DeployedWorkflow workflowDefinition = workflowCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);

                    if (workflowDefinition != null && version != -2)
                    {
                        command.setWorkflowKey(workflowDefinition.getKey())
                            .setVersion(workflowDefinition.getVersion());
                        accepted = true;
                    }
                    else
                    {
                        fetchWorkflowFuture = workflowCache.fetchLatestWorkflowByBpmnProcessId(bpmnProcessId);
                    }
                }
            }

            // by key
            else
            {
                final DeployedWorkflow workflowDefinition = workflowCache.getWorkflowByKey(workflowKey);

                if (workflowDefinition != null)
                {
                    command
                        .setVersion(workflowDefinition.getVersion())
                        .setBpmnProcessId(workflowDefinition.getWorkflow().getBpmnProcessId());
                    accepted = true;
                }
                else
                {
                    fetchWorkflowFuture = workflowCache.fetchWorkflowByKey(workflowKey);
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
                        accepted = false;
                    }
                    else
                    {
                        final DeployedWorkflow workflowDefinition = workflowCache.addWorkflow(response.getResponseBuffer());

                        if (workflowDefinition != null)
                        {
                            command
                                .setBpmnProcessId(workflowDefinition.getWorkflow().getBpmnProcessId())
                                .setWorkflowKey(workflowDefinition.getKey())
                                .setVersion(workflowDefinition.getVersion());
                            accepted = true;
                        }
                        else
                        {
                            // workflow not deployed
                            accepted = false;
                        }
                    }

                    workflowFetchedFuture.complete(null);
                });
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> command, TypedStreamWriter writer)
        {

            if (accepted)
            {
                final TypedBatchWriter batchWriter = writer.newBatch();
                batchWriter.addFollowUpEvent(
                        command.getKey(),
                        WorkflowInstanceIntent.CREATED,
                        command.getValue(),
                        this::addRequestMetadata);
                addStartEventOccured(batchWriter, command.getValue());
                return batchWriter.write();
            }
            else
            {
                return writer.writeRejection(command, this::addRequestMetadata);
            }
        }

        private void addStartEventOccured(TypedBatchWriter batchWriter, WorkflowInstanceRecord createCommand)
        {
            final Workflow workflow = workflowCache.getWorkflowByKey(createCommand.getWorkflowKey()).getWorkflow();
            final StartEvent startEvent = workflow.getInitialStartEvent();
            final DirectBuffer activityId = startEvent.getIdAsBuffer();

            startEventRecord.setActivityId(activityId)
                .setBpmnProcessId(createCommand.getBpmnProcessId())
                .setPayload(createCommand.getPayload())
                .setVersion(createCommand.getVersion())
                .setWorkflowInstanceKey(createCommand.getWorkflowInstanceKey())
                .setWorkflowKey(createCommand.getWorkflowKey());
            batchWriter.addNewEvent(WorkflowInstanceIntent.START_EVENT_OCCURRED, startEventRecord);
        }
    }

    private final class WorkflowInstanceCreatedEventProcessor implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        @Override
        public boolean executeSideEffects(TypedRecord<WorkflowInstanceRecord> record, TypedResponseWriter responseWriter)
        {
            workflowInstanceEventCreate.incrementOrdered();
            return responseWriter.writeEvent(WorkflowInstanceIntent.CREATED, record);
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            workflowInstanceIndex
                .newWorkflowInstance(record.getKey())
                .setPosition(record.getPosition())
                .setActiveTokenCount(1)
                .setActivityInstanceKey(-1L)
                .setWorkflowKey(record.getValue().getWorkflowKey())
                .write();
        }
    }

    private final class WorkflowInstanceRejectedEventProcessor implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        @Override
        public boolean executeSideEffects(TypedRecord<WorkflowInstanceRecord> record, TypedResponseWriter responseWriter)
        {
            return responseWriter.writeRejection(record);
        }
    }

    private final class TakeSequenceFlowAspectHandler extends FlowElementEventProcessor<FlowNode>
    {
        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, FlowNode currentFlowNode)
        {
            // the activity has exactly one outgoing sequence flow
            final SequenceFlow sequenceFlow = currentFlowNode.getOutgoingSequenceFlows().get(0);

            event.getValue().setActivityId(sequenceFlow.getIdAsBuffer());
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            return writer.writeNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, record.getValue());
        }
    }

    private final class ExclusiveSplitAspectHandler extends FlowElementEventProcessor<ExclusiveGateway>
    {
        private boolean createsIncident;
        private boolean isResolvingIncident;
        private final IncidentRecord incidentCommand = new IncidentRecord();

        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, ExclusiveGateway exclusiveGateway)
        {
            try
            {
                isResolvingIncident = event.getMetadata().hasIncidentKey();

                final WorkflowInstanceRecord value = event.getValue();
                final SequenceFlow sequenceFlow = getSequenceFlowWithFulfilledCondition(exclusiveGateway, value.getPayload());

                if (sequenceFlow != null)
                {
                    value.setActivityId(sequenceFlow.getIdAsBuffer());

                    createsIncident = false;
                }
                else
                {
                    incidentCommand.reset();
                    incidentCommand
                        .initFromWorkflowInstanceFailure(event)
                        .setErrorType(ErrorType.CONDITION_ERROR)
                        .setErrorMessage("All conditions evaluated to false and no default flow is set.");

                    createsIncident = true;
                }
            }
            catch (JsonConditionException e)
            {
                incidentCommand.reset();

                incidentCommand
                    .initFromWorkflowInstanceFailure(event)
                    .setErrorType(ErrorType.CONDITION_ERROR)
                    .setErrorMessage(e.getMessage());

                createsIncident = true;
            }
        }

        private SequenceFlow getSequenceFlowWithFulfilledCondition(ExclusiveGateway exclusiveGateway, DirectBuffer payload)
        {
            final List<SequenceFlow> sequenceFlows = exclusiveGateway.getOutgoingSequenceFlowsWithConditions();
            for (int s = 0; s < sequenceFlows.size(); s++)
            {
                final SequenceFlow sequenceFlow = sequenceFlows.get(s);

                final CompiledJsonCondition compiledCondition = sequenceFlow.getCondition();
                final boolean isFulFilled = conditionInterpreter.eval(compiledCondition.getCondition(), payload);

                if (isFulFilled)
                {
                    return sequenceFlow;
                }
            }
            return exclusiveGateway.getDefaultFlow();
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            if (!createsIncident)
            {
                return writer.writeNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, record.getValue());
            }
            else
            {
                if (!isResolvingIncident)
                {
                    return writer.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
                }
                else
                {
                    return writer.writeFollowUpEvent(record.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentCommand);
                }
            }
        }
    }

    private final class ConsumeTokenAspectHandler extends FlowElementEventProcessor<FlowElement>
    {
        private boolean isCompleted;
        private int activeTokenCount;


        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, FlowElement currentFlowNode)
        {
            final WorkflowInstanceRecord workflowInstanceEvent = event.getValue();

            final WorkflowInstance workflowInstance = workflowInstanceIndex
                    .get(workflowInstanceEvent.getWorkflowInstanceKey());

            activeTokenCount = workflowInstance != null ? workflowInstance.getTokenCount() : 0;
            isCompleted = activeTokenCount == 1;
            if (isCompleted)
            {
                workflowInstanceEvent.setActivityId("");
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            if (isCompleted)
            {
                return writer.writeFollowUpEvent(record.getValue().getWorkflowInstanceKey(), WorkflowInstanceIntent.COMPLETED, record.getValue());
            }
            else
            {
                return 0L;
            }
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            if (isCompleted)
            {
                final long workflowInstanceKey = record.getValue().getWorkflowInstanceKey();
                workflowInstanceIndex.remove(workflowInstanceKey);
                payloadCache.remove(workflowInstanceKey);
            }
        }
    }

    private final class SequenceFlowTakenEventProcessor extends FlowElementEventProcessor<SequenceFlow>
    {
        private Intent nextState;

        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, SequenceFlow sequenceFlow)
        {
            final FlowNode targetNode = sequenceFlow.getTargetNode();

            final WorkflowInstanceRecord value = event.getValue();
            value.setActivityId(targetNode.getIdAsBuffer());

            if (targetNode instanceof EndEvent)
            {
                nextState = WorkflowInstanceIntent.END_EVENT_OCCURRED;
            }
            else if (targetNode instanceof ServiceTask)
            {
                nextState = WorkflowInstanceIntent.ACTIVITY_READY;
            }
            else if (targetNode instanceof ExclusiveGateway)
            {
                nextState = WorkflowInstanceIntent.GATEWAY_ACTIVATED;
            }
            else
            {
                throw new RuntimeException(String.format("Flow node of type '%s' is not supported.", targetNode));
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            return writer.writeNewEvent(nextState, record.getValue());
        }
    }

    private final class ActivityReadyEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        private final IncidentRecord incidentCommand = new IncidentRecord();

        private boolean createsIncident;
        private boolean isResolvingIncident;
        private UnsafeBuffer wfInstancePayload = new UnsafeBuffer(0, 0);

        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, ServiceTask serviceTask)
        {
            createsIncident = false;
            isResolvingIncident = event.getMetadata().hasIncidentKey();

            final WorkflowInstanceRecord activityEvent = event.getValue();
            wfInstancePayload.wrap(activityEvent.getPayload());

            final Mapping[] inputMappings = serviceTask.getInputOutputMapping().getInputMappings();

            // only if we have no default mapping we have to use the mapping processor
            if (inputMappings.length > 0)
            {
                try
                {
                    final int resultLen = payloadMappingProcessor.extract(activityEvent.getPayload(), inputMappings);
                    final MutableDirectBuffer mappedPayload = payloadMappingProcessor.getResultBuffer();
                    activityEvent.setPayload(mappedPayload, 0, resultLen);
                }
                catch (MappingException e)
                {
                    incidentCommand.reset();

                    incidentCommand
                        .initFromWorkflowInstanceFailure(event)
                        .setErrorType(ErrorType.IO_MAPPING_ERROR)
                        .setErrorMessage(e.getMessage());

                    createsIncident = true;
                }
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            if (!createsIncident)
            {
                return writer.writeFollowUpEvent(record.getKey(), WorkflowInstanceIntent.ACTIVITY_ACTIVATED, record.getValue());
            }
            else
            {
                if (!isResolvingIncident)
                {
                    return writer.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
                }
                else
                {
                    return writer.writeFollowUpEvent(record.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentCommand);
                }
            }
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            final WorkflowInstanceRecord workflowInstanceEvent = record.getValue();

            workflowInstanceIndex
                .get(workflowInstanceEvent.getWorkflowInstanceKey())
                .setActivityInstanceKey(record.getKey())
                .write();

            activityInstanceMap
                .newActivityInstance(record.getKey())
                .setActivityId(workflowInstanceEvent.getActivityId())
                .setJobKey(-1L)
                .write();

            if (!createsIncident && !isNilPayload(workflowInstanceEvent.getPayload()))
            {
                payloadCache.addPayload(
                        workflowInstanceEvent.getWorkflowInstanceKey(),
                        record.getPosition(),
                        wfInstancePayload);
            }
        }
    }

    private final class ActivityActivatedEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        private final JobRecord jobCommand = new JobRecord();

        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, ServiceTask serviceTask)
        {
            final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();

            final WorkflowInstanceRecord value = event.getValue();

            jobCommand.reset();

            jobCommand
                .setType(taskDefinition.getTypeAsBuffer())
                .setRetries(taskDefinition.getRetries())
                .setPayload(value.getPayload())
                .headers()
                    .setBpmnProcessId(value.getBpmnProcessId())
                    .setWorkflowDefinitionVersion(value.getVersion())
                    .setWorkflowKey(value.getWorkflowKey())
                    .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
                    .setActivityId(serviceTask.getIdAsBuffer())
                    .setActivityInstanceKey(event.getKey());

            final io.zeebe.model.bpmn.instance.TaskHeaders customHeaders = serviceTask.getTaskHeaders();

            if (!customHeaders.isEmpty())
            {
                jobCommand.setCustomHeaders(customHeaders.asMsgpackEncoded());
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            return writer.writeNewCommand(JobIntent.CREATE, jobCommand);
        }
    }

    private final class JobCreatedProcessor implements TypedRecordProcessor<JobRecord>
    {
        private boolean isActive;

        @Override
        public void processRecord(TypedRecord<JobRecord> record)
        {
            isActive = false;

            final JobHeaders jobHeaders = record.getValue().headers();
            final long activityInstanceKey = jobHeaders.getActivityInstanceKey();
            if (activityInstanceKey > 0)
            {
                final WorkflowInstance workflowInstance = workflowInstanceIndex.get(jobHeaders.getWorkflowInstanceKey());

                isActive = workflowInstance != null && activityInstanceKey == workflowInstance.getActivityInstanceKey();
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> record)
        {
            if (isActive)
            {
                final long activityInstanceKey = record.getValue()
                    .headers()
                    .getActivityInstanceKey();

                activityInstanceMap
                    .wrapActivityInstanceKey(activityInstanceKey)
                    .setJobKey(record.getKey())
                    .write();
            }
        }
    }

    private final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord>
    {
        private final WorkflowInstanceRecord workflowInstanceEvent = new WorkflowInstanceRecord();

        private boolean activityCompleted;
        private long activityInstanceKey;

        @Override
        public void processRecord(TypedRecord<JobRecord> record)
        {
            activityCompleted = false;

            final JobRecord jobEvent = record.getValue();
            final JobHeaders jobHeaders = jobEvent.headers();
            activityInstanceKey = jobHeaders.getActivityInstanceKey();

            if (jobHeaders.getWorkflowInstanceKey() > 0 && isJobOpen(record.getKey(), activityInstanceKey))
            {
                workflowInstanceEvent
                    .setBpmnProcessId(jobHeaders.getBpmnProcessId())
                    .setVersion(jobHeaders.getWorkflowDefinitionVersion())
                    .setWorkflowKey(jobHeaders.getWorkflowKey())
                    .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
                    .setActivityId(jobHeaders.getActivityId())
                    .setPayload(jobEvent.getPayload());

                activityCompleted = true;
            }
        }

        private boolean isJobOpen(long jobKey, long activityInstanceKey)
        {
            // job key = -1 when activity is left
            return activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getJobKey() == jobKey;
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> record, TypedStreamWriter writer)
        {
            return activityCompleted ?
                    writer.writeFollowUpEvent(activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETING, workflowInstanceEvent) :
                    0L;
        }

        @Override
        public void updateState(TypedRecord<JobRecord> record)
        {
            if (activityCompleted)
            {
                activityInstanceMap
                    .wrapActivityInstanceKey(activityInstanceKey)
                    .setJobKey(-1L)
                    .write();
            }
        }
    }

    private final class ActivityCompletingEventProcessor extends FlowElementEventProcessor<ServiceTask>
    {
        private final IncidentRecord incidentCommand = new IncidentRecord();
        private boolean hasIncident;
        private boolean isResolvingIncident;

        @Override
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, ServiceTask serviceTask)
        {
            hasIncident = false;
            isResolvingIncident = event.getMetadata().hasIncidentKey();

            final WorkflowInstanceRecord activityEvent = event.getValue();

            final Mapping[] outputMappings = serviceTask.getInputOutputMapping().getOutputMappings();

            final DirectBuffer workflowInstancePayload = payloadCache.getPayload(activityEvent.getWorkflowInstanceKey());
            final DirectBuffer taskPayload = activityEvent.getPayload();
            final boolean isNilPayload = isNilPayload(taskPayload);

            if (outputMappings != null && outputMappings.length > 0 && isNilPayload)
            {
                incidentCommand.reset();
                incidentCommand
                    .initFromWorkflowInstanceFailure(event)
                    .setErrorType(ErrorType.IO_MAPPING_ERROR)
                    .setErrorMessage("Could not apply output mappings: Task was completed without payload");

                hasIncident = true;
            }
            else
            {
                try
                {
                    final int resultLen = payloadMappingProcessor.merge(taskPayload, workflowInstancePayload, outputMappings);
                    final MutableDirectBuffer mergedPayload = payloadMappingProcessor.getResultBuffer();
                    activityEvent.setPayload(mergedPayload, 0, resultLen);
                }
                catch (MappingException e)
                {
                    incidentCommand.reset();
                    incidentCommand
                        .initFromWorkflowInstanceFailure(event)
                        .setErrorType(ErrorType.IO_MAPPING_ERROR)
                        .setErrorMessage(e.getMessage());

                    hasIncident = true;
                }
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            if (!hasIncident)
            {
                return writer.writeFollowUpEvent(record.getKey(), WorkflowInstanceIntent.ACTIVITY_COMPLETED, record.getValue());
            }
            else
            {
                if (!isResolvingIncident)
                {
                    return writer.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
                }
                else
                {
                    return writer.writeFollowUpEvent(record.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentCommand);
                }
            }
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            if (!hasIncident)
            {
                workflowInstanceIndex
                    .get(record.getValue().getWorkflowInstanceKey())
                    .setActivityInstanceKey(-1L)
                    .write();

                activityInstanceMap.remove(record.getKey());
            }
        }
    }

    private final class CancelWorkflowInstanceProcessor implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        private final WorkflowInstanceRecord activityInstanceEvent = new WorkflowInstanceRecord();
        private final JobRecord jobRecord = new JobRecord();

        private boolean isCanceled;
        private long activityInstanceKey;
        private long jobKey;

        private TypedStreamReader reader;
        private TypedRecord<WorkflowInstanceRecord> workflowInstanceEvent;

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = streamProcessor.getEnvironment().buildStreamReader();
        }

        @Override
        public void onClose()
        {
            reader.close();
        }

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceRecord> command)
        {
            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(command.getKey());

            isCanceled = workflowInstance != null && workflowInstance.getTokenCount() > 0;

            if (isCanceled)
            {
                workflowInstanceEvent = reader.readValue(workflowInstance.getPosition(), WorkflowInstanceRecord.class);

                workflowInstanceEvent.getValue().setPayload(WorkflowInstanceRecord.NO_PAYLOAD);

                activityInstanceKey = workflowInstance.getActivityInstanceKey();
                jobKey = activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getJobKey();
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> command, TypedStreamWriter writer)
        {
            if (isCanceled)
            {
                activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey);
                final WorkflowInstanceRecord value = workflowInstanceEvent.getValue();

                final TypedBatchWriter batchWriter = writer.newBatch();

                if (jobKey > 0)
                {
                    jobRecord.reset();
                    jobRecord
                        .setType(EMPTY_JOB_TYPE)
                        .headers()
                            .setBpmnProcessId(value.getBpmnProcessId())
                            .setWorkflowDefinitionVersion(value.getVersion())
                            .setWorkflowInstanceKey(command.getKey())
                            .setActivityId(activityInstanceMap.getActivityId())
                            .setActivityInstanceKey(activityInstanceKey);

                    batchWriter.addFollowUpCommand(jobKey, JobIntent.CANCEL, jobRecord);
                }

                if (activityInstanceKey > 0)
                {
                    activityInstanceEvent.reset();
                    activityInstanceEvent
                        .setBpmnProcessId(value.getBpmnProcessId())
                        .setVersion(value.getVersion())
                        .setWorkflowInstanceKey(command.getKey())
                        .setActivityId(activityInstanceMap.getActivityId());

                    batchWriter.addFollowUpEvent(activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_TERMINATED, activityInstanceEvent);
                }

                batchWriter.addFollowUpEvent(command.getKey(), WorkflowInstanceIntent.CANCELED, value);

                return batchWriter.write();
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<WorkflowInstanceRecord> record, TypedResponseWriter responseWriter)
        {
            if (isCanceled)
            {
                return responseWriter.writeEvent(WorkflowInstanceIntent.CANCELED, record);
            }
            else
            {
                return responseWriter.writeRejection(record);
            }
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            if (isCanceled)
            {
                workflowInstanceIndex.remove(record.getKey());
                payloadCache.remove(record.getKey());
                activityInstanceMap.remove(activityInstanceKey);
            }
        }
    }

    private final class UpdatePayloadProcessor implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        private boolean isUpdated;

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceRecord> command)
        {
            final WorkflowInstanceRecord workflowInstanceEvent = command.getValue();

            final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());
            final boolean isActive = workflowInstance != null && workflowInstance.getTokenCount() > 0;

            isUpdated = isActive && isValidPayload(workflowInstanceEvent.getPayload());
        }

        @Override
        public boolean executeSideEffects(TypedRecord<WorkflowInstanceRecord> command, TypedResponseWriter responseWriter)
        {
            if (isUpdated)
            {
                return responseWriter.writeEvent(WorkflowInstanceIntent.PAYLOAD_UPDATED, command);
            }
            else
            {
                return responseWriter.writeRejection(command);
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> command, TypedStreamWriter writer)
        {
            if (isUpdated)
            {
                return writer.writeFollowUpEvent(command.getKey(), WorkflowInstanceIntent.PAYLOAD_UPDATED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> command)
        {
            if (isUpdated)
            {
                final WorkflowInstanceRecord workflowInstanceEvent = command.getValue();
                payloadCache.addPayload(workflowInstanceEvent.getWorkflowInstanceKey(), command.getPosition(), workflowInstanceEvent.getPayload());
            }
        }
    }

    public void fetchWorkflow(long workflowKey, Consumer<DeployedWorkflow> onFetched, EventLifecycleContext ctx)
    {
        final ActorFuture<ClientResponse> responseFuture = workflowCache.fetchWorkflowByKey(workflowKey);
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
                    final DeployedWorkflow workflow = workflowCache.addWorkflow(response.getResponseBuffer());

                    onFetched.accept(workflow);

                    onCompleted.complete(null);
                }
                catch (Exception e)
                {
                    onCompleted.completeExceptionally(new RuntimeException("Error while processing fetched workflow", e));
                }
            }
        });
    }

    private abstract class FlowElementEventProcessor<T extends FlowElement> implements TypedRecordProcessor<WorkflowInstanceRecord>
    {
        private TypedRecord<WorkflowInstanceRecord> event;

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceRecord> record, EventLifecycleContext ctx)
        {
            event = record;
            final long workflowKey = event.getValue().getWorkflowKey();
            final DeployedWorkflow deployedWorkflow = workflowCache.getWorkflowByKey(workflowKey);

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
            final DirectBuffer currentActivityId = event.getValue().getActivityId();

            final Workflow workflow = deployedWorkflow.getWorkflow();
            final FlowElement flowElement = workflow.findFlowElementById(currentActivityId);

            processFlowElementEvent(event, (T) flowElement);
        }

        abstract void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, T currentFlowNode);
    }

    @SuppressWarnings("rawtypes")
    private final class BpmnAspectEventProcessor extends FlowElementEventProcessor<FlowElement>
    {
        private FlowElementEventProcessor delegate;

        protected final Map<BpmnAspect, FlowElementEventProcessor> aspectHandlers;

        private BpmnAspectEventProcessor()
        {
            aspectHandlers = new EnumMap<>(BpmnAspect.class);

            aspectHandlers.put(BpmnAspect.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowAspectHandler());
            aspectHandlers.put(BpmnAspect.CONSUME_TOKEN, new ConsumeTokenAspectHandler());
            aspectHandlers.put(BpmnAspect.EXCLUSIVE_SPLIT, new ExclusiveSplitAspectHandler());
        }

        @Override
        @SuppressWarnings("unchecked")
        void processFlowElementEvent(TypedRecord<WorkflowInstanceRecord> event, FlowElement currentFlowNode)
        {
            final BpmnAspect bpmnAspect = currentFlowNode.getBpmnAspect();

            delegate = aspectHandlers.get(bpmnAspect);

            delegate.processFlowElementEvent(event, currentFlowNode);
        }

        @Override
        public boolean executeSideEffects(TypedRecord<WorkflowInstanceRecord> record, TypedResponseWriter responseWriter)
        {
            return delegate.executeSideEffects(record, responseWriter);
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceRecord> record, TypedStreamWriter writer)
        {
            return delegate.writeRecord(record, writer);
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceRecord> record)
        {
            delegate.updateState(record);
        }
    }
}
