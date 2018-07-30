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

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.EMPTY_PAYLOAD;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.job.data.JobHeaders;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.ActivityInstanceMap;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.PayloadCache;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex.WorkflowInstance;
import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.ExecutableIntermediateMessageCatchEvent;
import io.zeebe.broker.workflow.model.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.ExecutableServiceTask;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionException;
import io.zeebe.msgpack.el.JsonConditionInterpreter;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.msgpack.mapping.MappingProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceStreamProcessor implements StreamProcessorLifecycleAware {
  private static final UnsafeBuffer EMPTY_JOB_TYPE = new UnsafeBuffer("".getBytes());

  private Metric workflowInstanceEventCreate;
  private Metric workflowInstanceEventCanceled;
  private Metric workflowInstanceEventCompleted;

  private final WorkflowInstanceIndex workflowInstanceIndex = new WorkflowInstanceIndex();
  private final ActivityInstanceMap activityInstanceMap = new ActivityInstanceMap();
  private final PayloadCache payloadCache;

  private final MappingProcessor payloadMappingProcessor = new MappingProcessor(4096);
  private final JsonConditionInterpreter conditionInterpreter = new JsonConditionInterpreter();

  private TypedStreamReader streamReader;
  private SubscriptionCommandSender subscriptionCommandSender;

  private final ClientTransport managementApiClient;
  private final ClientTransport subscriptionApiClient;
  private final TopologyManager topologyManager;
  private WorkflowCache workflowCache;

  private ActorControl actor;

  public WorkflowInstanceStreamProcessor(
      ClientTransport managementApiClient,
      ClientTransport subscriptionApiClient,
      TopologyManager topologyManager,
      int payloadCacheSize) {
    this.managementApiClient = managementApiClient;
    this.subscriptionApiClient = subscriptionApiClient;
    this.payloadCache = new PayloadCache(payloadCacheSize);
    this.topologyManager = topologyManager;
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {
    final BpmnStepProcessor bpmnAspectProcessor = new BpmnStepProcessor();

    return environment
        .newStreamProcessor()
        .keyGenerator(KeyGenerator.createWorkflowInstanceKeyGenerator())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATE,
            new CreateWorkflowInstanceEventProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATED,
            new WorkflowInstanceCreatedEventProcessor())
        .onRejection(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATE,
            new WorkflowInstanceRejectedEventProcessor())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CANCEL,
            new CancelWorkflowInstanceProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            w -> isActive(w.getWorkflowInstanceKey()),
            new SequenceFlowTakenEventProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ACTIVITY_READY,
            w -> isActive(w.getWorkflowInstanceKey()),
            new ActivityReadyEventProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ACTIVITY_ACTIVATED,
            w -> isActive(w.getWorkflowInstanceKey()),
            new ActivityActivatedEventProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ACTIVITY_COMPLETING,
            w -> isActive(w.getWorkflowInstanceKey()),
            new ActivityCompletingEventProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CATCH_EVENT_ENTERING,
            w -> isActive(w.getWorkflowInstanceKey()),
            new CatchEventEnteringProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CATCH_EVENT_OCCURRING,
            w -> isActive(w.getWorkflowInstanceKey()),
            new CatchEventOccurringProcessor())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.UPDATE_PAYLOAD,
            new UpdatePayloadProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            w -> isActive(w.getWorkflowInstanceKey()),
            bpmnAspectProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.END_EVENT_OCCURRED,
            w -> isActive(w.getWorkflowInstanceKey()),
            bpmnAspectProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            w -> isActive(w.getWorkflowInstanceKey()),
            bpmnAspectProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ACTIVITY_COMPLETED,
            w -> isActive(w.getWorkflowInstanceKey()),
            bpmnAspectProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CATCH_EVENT_OCCURRED,
            w -> isActive(w.getWorkflowInstanceKey()),
            bpmnAspectProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CANCELED,
            (Consumer<WorkflowInstanceRecord>)
                (e) -> workflowInstanceEventCanceled.incrementOrdered())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.COMPLETED,
            (Consumer<WorkflowInstanceRecord>)
                (e) -> workflowInstanceEventCompleted.incrementOrdered())
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor())
        .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription())
        .withStateResource(workflowInstanceIndex.getMap())
        .withStateResource(activityInstanceMap.getMap())
        .withStateResource(payloadCache.getMap())
        .withListener(payloadCache)
        .withListener(this)
        .build();
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {

    this.actor = streamProcessor.getActor();
    final LogStream logStream = streamProcessor.getEnvironment().getStream();
    this.workflowCache =
        new WorkflowCache(managementApiClient, topologyManager, logStream.getTopicName());

    final StreamProcessorContext context = streamProcessor.getStreamProcessorContext();
    final MetricsManager metricsManager = context.getActorScheduler().getMetricsManager();
    final String topicName =
        logStream.getTopicName().getStringWithoutLengthUtf8(0, logStream.getTopicName().capacity());
    final String partitionId = Integer.toString(logStream.getPartitionId());

    this.streamReader = streamProcessor.getEnvironment().buildStreamReader();

    this.subscriptionCommandSender =
        new SubscriptionCommandSender(
            actor,
            managementApiClient,
            subscriptionApiClient,
            topicName,
            logStream.getPartitionId());
    topologyManager.addTopologyPartitionListener(subscriptionCommandSender);

    workflowInstanceEventCreate =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "created")
            .create();

    workflowInstanceEventCanceled =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "canceled")
            .create();

    workflowInstanceEventCompleted =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "completed")
            .create();
  }

  @Override
  public void onClose() {
    workflowCache.close();
    workflowInstanceEventCreate.close();
    workflowInstanceEventCanceled.close();
    workflowInstanceEventCompleted.close();
    streamReader.close();
  }

  private boolean isActive(long workflowInstanceKey) {
    final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceKey);
    return workflowInstance != null && workflowInstance.getTokenCount() > 0;
  }

  private final class CreateWorkflowInstanceEventProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final WorkflowInstanceRecord startEventRecord = new WorkflowInstanceRecord();

    private long requestId;
    private int requestStreamId;

    private long workflowInstanceKey;
    private long startEventKey;

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect,
        EventLifecycleContext ctx) {
      final WorkflowInstanceRecord workflowInstanceCommand = command.getValue();

      this.requestId = command.getMetadata().getRequestId();
      this.requestStreamId = command.getMetadata().getRequestStreamId();

      // keys must be generated here (regardless if workflow can be fetched or not)
      // to avoid inconsistencies on reprocessing (if keys are generated must no depend
      // on the success of the workflow fetch request)
      final KeyGenerator keyGenerator = streamWriter.getKeyGenerator();
      this.workflowInstanceKey = keyGenerator.nextKey();
      this.startEventKey = keyGenerator.nextKey();

      workflowInstanceCommand.setWorkflowInstanceKey(workflowInstanceKey);

      createWorkflowInstance(command, streamWriter, responseWriter, ctx);
    }

    private void addRequestMetadata(RecordMetadata metadata) {
      metadata.requestId(requestId).requestStreamId(requestStreamId);
    }

    private void createWorkflowInstance(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedStreamWriter streamWriter,
        TypedResponseWriter responseWriter,
        EventLifecycleContext ctx) {
      final WorkflowInstanceRecord value = command.getValue();

      final long workflowKey = value.getWorkflowKey();
      final DirectBuffer bpmnProcessId = value.getBpmnProcessId();
      final int version = value.getVersion();

      ActorFuture<ClientResponse> fetchWorkflowFuture = null;

      if (workflowKey <= 0) {
        // by bpmn process id and version
        if (version > 0) {
          final DeployedWorkflow workflowDefinition =
              workflowCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);

          if (workflowDefinition != null) {
            value.setWorkflowKey(workflowDefinition.getKey());
            acceptCommand(command, streamWriter, responseWriter);
          } else {
            fetchWorkflowFuture =
                workflowCache.fetchWorkflowByBpmnProcessIdAndVersion(bpmnProcessId, version);
          }
        }

        // latest by bpmn process id
        else {
          final DeployedWorkflow workflowDefinition =
              workflowCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);

          if (workflowDefinition != null && version != -2) {
            value
                .setWorkflowKey(workflowDefinition.getKey())
                .setVersion(workflowDefinition.getVersion());
            acceptCommand(command, streamWriter, responseWriter);
          } else {
            fetchWorkflowFuture = workflowCache.fetchLatestWorkflowByBpmnProcessId(bpmnProcessId);
          }
        }
      }

      // by key
      else {
        final DeployedWorkflow workflowDefinition = workflowCache.getWorkflowByKey(workflowKey);

        if (workflowDefinition != null) {
          value
              .setVersion(workflowDefinition.getVersion())
              .setBpmnProcessId(workflowDefinition.getWorkflow().getId());
          acceptCommand(command, streamWriter, responseWriter);
        } else {
          fetchWorkflowFuture = workflowCache.fetchWorkflowByKey(workflowKey);
        }
      }

      if (fetchWorkflowFuture != null) {
        final ActorFuture<Void> workflowFetchedFuture = new CompletableActorFuture<>();
        ctx.async(workflowFetchedFuture);

        actor.runOnCompletion(
            fetchWorkflowFuture,
            (response, err) -> {
              if (err != null) {
                rejectCommand(
                    command,
                    streamWriter,
                    responseWriter,
                    RejectionType.PROCESSING_ERROR,
                    "Could not fetch workflow: " + err.getMessage());
              } else {
                final DeployedWorkflow workflowDefinition =
                    workflowCache.addWorkflow(response.getResponseBuffer());

                if (workflowDefinition != null) {
                  value
                      .setBpmnProcessId(workflowDefinition.getWorkflow().getId())
                      .setWorkflowKey(workflowDefinition.getKey())
                      .setVersion(workflowDefinition.getVersion());
                  acceptCommand(command, streamWriter, responseWriter);
                } else {
                  rejectCommand(
                      command,
                      streamWriter,
                      responseWriter,
                      RejectionType.BAD_VALUE,
                      "Workflow is not deployed");
                }
              }

              workflowFetchedFuture.complete(null);
            });
      }
    }

    private void acceptCommand(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedStreamWriter writer,
        TypedResponseWriter responseWriter) {
      final TypedBatchWriter batchWriter = writer.newBatch();
      batchWriter.addFollowUpEvent(
          workflowInstanceKey,
          WorkflowInstanceIntent.CREATED,
          command.getValue(),
          this::addRequestMetadata);
      addStartEventOccured(batchWriter, command.getValue());
    }

    private void rejectCommand(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedStreamWriter writer,
        TypedResponseWriter responseWriter,
        RejectionType rejectionType,
        String rejectionReason) {
      writer.writeRejection(command, rejectionType, rejectionReason, this::addRequestMetadata);
    }

    private void addStartEventOccured(
        TypedBatchWriter batchWriter, WorkflowInstanceRecord createCommand) {
      final ExecutableWorkflow workflow =
          workflowCache.getWorkflowByKey(createCommand.getWorkflowKey()).getWorkflow();
      final ExecutableFlowNode startEvent = workflow.getStartEvent();
      final DirectBuffer activityId = startEvent.getId();

      startEventRecord
          .setActivityId(activityId)
          .setBpmnProcessId(createCommand.getBpmnProcessId())
          .setPayload(createCommand.getPayload())
          .setVersion(createCommand.getVersion())
          .setWorkflowInstanceKey(createCommand.getWorkflowInstanceKey())
          .setWorkflowKey(createCommand.getWorkflowKey());
      batchWriter.addFollowUpEvent(
          startEventKey, WorkflowInstanceIntent.START_EVENT_OCCURRED, startEventRecord);
    }
  }

  private final class WorkflowInstanceCreatedEventProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      workflowInstanceEventCreate.incrementOrdered();
      responseWriter.writeEvent(record);

      workflowInstanceIndex
          .newWorkflowInstance(record.getKey())
          .setPosition(record.getPosition())
          .setActiveTokenCount(1)
          .setActivityInstanceKey(-1L)
          .setWorkflowKey(record.getValue().getWorkflowKey())
          .write();
    }
  }

  private final class WorkflowInstanceRejectedEventProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      responseWriter.writeRejection(record);
    }
  }

  private final class TakeSequenceFlowAspectHandler
      extends FlowElementEventProcessor<ExecutableFlowNode> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableFlowNode currentFlowNode) {

      // the activity has exactly one outgoing sequence flow
      final ExecutableSequenceFlow sequenceFlow = currentFlowNode.getOutgoing().get(0);

      final WorkflowInstanceRecord value = event.getValue();
      value.setActivityId(sequenceFlow.getId());

      streamWriter.writeNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, value);
    }
  }

  private final class ExclusiveSplitAspectHandler
      extends FlowElementEventProcessor<ExecutableExclusiveGateway> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableExclusiveGateway exclusiveGateway) {

      try {
        final WorkflowInstanceRecord value = event.getValue();
        final ExecutableSequenceFlow sequenceFlow =
            getSequenceFlowWithFulfilledCondition(exclusiveGateway, value.getPayload());

        if (sequenceFlow != null) {
          value.setActivityId(sequenceFlow.getId());
          streamWriter.writeNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, value);
        } else {
          final String errorMessage =
              "All conditions evaluated to false and no default flow is set.";
          raiseIncident(event, ErrorType.CONDITION_ERROR, errorMessage, streamWriter);
        }
      } catch (JsonConditionException e) {
        raiseIncident(event, ErrorType.CONDITION_ERROR, e.getMessage(), streamWriter);
      }
    }

    private ExecutableSequenceFlow getSequenceFlowWithFulfilledCondition(
        ExecutableExclusiveGateway exclusiveGateway, DirectBuffer payload) {
      final List<ExecutableSequenceFlow> sequenceFlows =
          exclusiveGateway.getOutgoingWithCondition();
      for (int s = 0; s < sequenceFlows.size(); s++) {
        final ExecutableSequenceFlow sequenceFlow = sequenceFlows.get(s);

        final CompiledJsonCondition compiledCondition = sequenceFlow.getCondition();
        final boolean isFulFilled =
            conditionInterpreter.eval(compiledCondition.getCondition(), payload);

        if (isFulFilled) {
          return sequenceFlow;
        }
      }
      return exclusiveGateway.getDefaultFlow();
    }
  }

  private final class ConsumeTokenAspectHandler
      extends FlowElementEventProcessor<ExecutableFlowElement> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableFlowElement currentFlowNode) {
      final WorkflowInstanceRecord workflowInstanceEvent = event.getValue();

      final WorkflowInstance workflowInstance =
          workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());

      final int activeTokenCount = workflowInstance != null ? workflowInstance.getTokenCount() : 0;
      if (activeTokenCount == 1) {
        final long workflowInstanceKey = workflowInstanceEvent.getWorkflowInstanceKey();

        workflowInstanceEvent.setActivityId("");
        streamWriter.writeFollowUpEvent(
            workflowInstanceKey, WorkflowInstanceIntent.COMPLETED, workflowInstanceEvent);

        workflowInstanceIndex.remove(workflowInstanceKey);
        payloadCache.remove(workflowInstanceKey);
      }
    }
  }

  private final class SequenceFlowTakenEventProcessor
      extends FlowElementEventProcessor<ExecutableSequenceFlow> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableSequenceFlow sequenceFlow) {
      final ExecutableFlowNode targetNode = sequenceFlow.getTarget();

      final WorkflowInstanceRecord value = event.getValue();
      value.setActivityId(targetNode.getId());

      final Intent nextState;

      if (targetNode instanceof ExecutableServiceTask) {
        nextState = WorkflowInstanceIntent.ACTIVITY_READY;
      } else if (targetNode instanceof ExecutableExclusiveGateway) {
        nextState = WorkflowInstanceIntent.GATEWAY_ACTIVATED;
      } else if (targetNode instanceof ExecutableIntermediateMessageCatchEvent) {
        nextState = WorkflowInstanceIntent.CATCH_EVENT_ENTERING;
      } else {
        // TODO: assuming the default is END_EVENT is a bit hacky, but this will go away anyway with
        // https://github.com/zeebe-io/zeebe/issues/1078
        nextState = WorkflowInstanceIntent.END_EVENT_OCCURRED;
      }

      streamWriter.writeNewEvent(nextState, event.getValue());
    }
  }

  private final class ActivityReadyEventProcessor
      extends FlowElementEventProcessor<ExecutableFlowNode> {

    private UnsafeBuffer wfInstancePayload = new UnsafeBuffer(0, 0);

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableFlowNode serviceTask) {

      final WorkflowInstanceRecord activityEvent = event.getValue();
      wfInstancePayload.wrap(activityEvent.getPayload());

      final Mapping[] inputMappings = serviceTask.getInputMappings();

      MappingException mappingException = null;

      // only if we have no default mapping we have to use the mapping processor
      if (inputMappings.length > 0) {
        try {
          final int resultLen =
              payloadMappingProcessor.extract(activityEvent.getPayload(), inputMappings);
          final MutableDirectBuffer mappedPayload = payloadMappingProcessor.getResultBuffer();
          activityEvent.setPayload(mappedPayload, 0, resultLen);
        } catch (MappingException e) {
          mappingException = e;
        }
      }

      if (mappingException == null) {
        streamWriter.writeFollowUpEvent(
            event.getKey(), WorkflowInstanceIntent.ACTIVITY_ACTIVATED, activityEvent);

        payloadCache.addPayload(
            activityEvent.getWorkflowInstanceKey(), event.getPosition(), wfInstancePayload);
      } else {
        raiseIncident(
            event, ErrorType.IO_MAPPING_ERROR, mappingException.getMessage(), streamWriter);
      }

      workflowInstanceIndex
          .get(activityEvent.getWorkflowInstanceKey())
          .setActivityInstanceKey(event.getKey())
          .write();

      activityInstanceMap
          .newActivityInstance(event.getKey())
          .setActivityId(activityEvent.getActivityId())
          .setJobKey(-1L)
          .write();
    }
  }

  private final class ActivityActivatedEventProcessor
      extends FlowElementEventProcessor<ExecutableServiceTask> {
    private final JobRecord jobCommand = new JobRecord();

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableServiceTask serviceTask) {

      final WorkflowInstanceRecord value = event.getValue();

      jobCommand.reset();

      jobCommand
          .setType(serviceTask.getType())
          .setRetries(serviceTask.getRetries())
          .setPayload(value.getPayload())
          .headers()
          .setBpmnProcessId(value.getBpmnProcessId())
          .setWorkflowDefinitionVersion(value.getVersion())
          .setWorkflowKey(value.getWorkflowKey())
          .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
          .setActivityId(serviceTask.getId())
          .setActivityInstanceKey(event.getKey());

      final DirectBuffer headers = serviceTask.getEncodedHeaders();
      jobCommand.setCustomHeaders(headers);

      streamWriter.writeNewCommand(JobIntent.CREATE, jobCommand);
    }
  }

  public final class CatchEventEnteringProcessor
      extends FlowElementEventProcessor<ExecutableIntermediateMessageCatchEvent> {

    private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();

    private WorkflowInstanceRecord workflowInstance;
    private long activityInstanceKey;
    private ExecutableIntermediateMessageCatchEvent catchEvent;
    private DirectBuffer extractedCorrelationKey;

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableIntermediateMessageCatchEvent catchEvent) {

      this.workflowInstance = event.getValue();
      this.activityInstanceKey = event.getKey();
      this.catchEvent = catchEvent;

      if (subscriptionCommandSender.hasPartitionIds()) {
        onPartitionIdsAvailable(event, streamWriter);

      } else {
        // this async fetching will be removed when the partitions are known on startup
        final ActorFuture<Void> onCompleted = new CompletableActorFuture<>();
        ctx.async(onCompleted);

        actor.runOnCompletion(
            subscriptionCommandSender.fetchCreatedTopics(),
            (v, failure) -> {
              if (failure == null) {
                onPartitionIdsAvailable(event, streamWriter);

                onCompleted.complete(null);
              } else {
                onCompleted.completeExceptionally(failure);
              }
            });
      }
    }

    private void onPartitionIdsAvailable(
        TypedRecord<WorkflowInstanceRecord> event, TypedStreamWriter streamWriter) {
      extractedCorrelationKey = extractCorrelationKey();
      sideEffect.accept(this::openMessageSubscription);

      streamWriter.writeFollowUpEvent(
          activityInstanceKey, WorkflowInstanceIntent.CATCH_EVENT_ENTERED, workflowInstance);

      workflowInstanceIndex
          .get(workflowInstance.getWorkflowInstanceKey())
          .setActivityInstanceKey(event.getKey())
          .write();

      activityInstanceMap
          .newActivityInstance(event.getKey())
          .setActivityId(workflowInstance.getActivityId())
          .setJobKey(-1L)
          .write();

      payloadCache.addPayload(
          workflowInstance.getWorkflowInstanceKey(),
          event.getPosition(),
          workflowInstance.getPayload());
    }

    private boolean openMessageSubscription() {
      return subscriptionCommandSender.openMessageSubscription(
          workflowInstance.getWorkflowInstanceKey(),
          activityInstanceKey,
          catchEvent.getMessageName(),
          extractedCorrelationKey);
    }

    private DirectBuffer extractCorrelationKey() {
      final QueryResults results =
          queryProcessor.process(catchEvent.getCorrelationKey(), workflowInstance.getPayload());
      if (results.size() == 1) {
        final QueryResult result = results.getSingleResult();

        if (result.isString()) {
          return result.getString();

        } else if (result.isLong()) {
          return result.getLongAsBuffer();

        } else {
          // the exception will be replaces by an incident - #1018
          throw new RuntimeException("Failed to extract correlation-key: wrong type");
        }
      } else {
        // the exception will be replaces by an incident - #1018
        throw new RuntimeException("Failed to extract correlation-key: no result");
      }
    }
  }

  private final class JobCreatedProcessor implements TypedRecordProcessor<JobRecord> {

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final JobHeaders jobHeaders = record.getValue().headers();
      final long activityInstanceKey = jobHeaders.getActivityInstanceKey();
      if (activityInstanceKey > 0) {
        final WorkflowInstance workflowInstance =
            workflowInstanceIndex.get(jobHeaders.getWorkflowInstanceKey());

        final boolean isActive =
            workflowInstance != null
                && activityInstanceKey == workflowInstance.getActivityInstanceKey();

        if (isActive) {
          activityInstanceMap
              .wrapActivityInstanceKey(activityInstanceKey)
              .setJobKey(record.getKey())
              .write();
        }
      }
    }
  }

  private final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord> {
    private final WorkflowInstanceRecord workflowInstanceEvent = new WorkflowInstanceRecord();

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final JobRecord jobEvent = record.getValue();
      final JobHeaders jobHeaders = jobEvent.headers();
      final long activityInstanceKey = jobHeaders.getActivityInstanceKey();

      if (jobHeaders.getWorkflowInstanceKey() > 0
          && isJobOpen(record.getKey(), activityInstanceKey)) {
        workflowInstanceEvent
            .setBpmnProcessId(jobHeaders.getBpmnProcessId())
            .setVersion(jobHeaders.getWorkflowDefinitionVersion())
            .setWorkflowKey(jobHeaders.getWorkflowKey())
            .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
            .setActivityId(jobHeaders.getActivityId())
            .setPayload(jobEvent.getPayload());

        streamWriter.writeFollowUpEvent(
            activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETING, workflowInstanceEvent);

        activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).setJobKey(-1L).write();
      }
    }

    private boolean isJobOpen(long jobKey, long activityInstanceKey) {
      // job key = -1 when activity is left
      return activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getJobKey() == jobKey;
    }
  }

  private final class ActivityCompletingEventProcessor
      extends FlowElementEventProcessor<ExecutableFlowNode> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableFlowNode serviceTask) {

      final WorkflowInstanceRecord activityEvent = event.getValue();
      final DirectBuffer workflowInstancePayload =
          payloadCache.getPayload(activityEvent.getWorkflowInstanceKey());

      tryToExecuteOutputBehavior(
          streamWriter, event, activityEvent, workflowInstancePayload, serviceTask);
    }

    private void tryToExecuteOutputBehavior(
        TypedStreamWriter streamWriter,
        TypedRecord<WorkflowInstanceRecord> event,
        WorkflowInstanceRecord activityEvent,
        DirectBuffer workflowInstancePayload,
        ExecutableFlowNode serviceTask) {
      final ZeebeOutputBehavior outputBehavior = serviceTask.getOutputBehavior();

      MappingException mappingException = null;

      if (outputBehavior == ZeebeOutputBehavior.none) {
        activityEvent.setPayload(workflowInstancePayload);
      } else {
        if (outputBehavior == ZeebeOutputBehavior.overwrite) {
          workflowInstancePayload = EMPTY_PAYLOAD;
        }

        final Mapping[] outputMappings = serviceTask.getOutputMappings();
        final DirectBuffer jobPayload = activityEvent.getPayload();

        try {
          final int resultLen =
              payloadMappingProcessor.merge(jobPayload, workflowInstancePayload, outputMappings);
          final MutableDirectBuffer mergedPayload = payloadMappingProcessor.getResultBuffer();
          activityEvent.setPayload(mergedPayload, 0, resultLen);

        } catch (MappingException e) {
          mappingException = e;
        }
      }

      if (mappingException == null) {
        streamWriter.writeFollowUpEvent(
            event.getKey(), WorkflowInstanceIntent.ACTIVITY_COMPLETED, activityEvent);

        workflowInstanceIndex
            .get(event.getValue().getWorkflowInstanceKey())
            .setActivityInstanceKey(-1L)
            .write();

        activityInstanceMap.remove(event.getKey());
      } else {
        raiseIncident(
            event, ErrorType.IO_MAPPING_ERROR, mappingException.getMessage(), streamWriter);
      }
    }
  }

  private final class CorrelateWorkflowInstanceSubscription
      implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {
    private final WorkflowInstanceRecord activityInstanceEvent = new WorkflowInstanceRecord();

    private TypedRecord<WorkflowInstanceSubscriptionRecord> record;
    private WorkflowInstanceSubscriptionRecord subscription;
    private TypedStreamWriter streamWriter;

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceSubscriptionRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect,
        EventLifecycleContext ctx) {

      this.record = record;
      this.subscription = record.getValue();
      this.streamWriter = streamWriter;

      final WorkflowInstance workflowInstance =
          workflowInstanceIndex.get(subscription.getWorkflowInstanceKey());

      if (workflowInstance == null
          || workflowInstance.getActivityInstanceKey() != subscription.getActivityInstanceKey()) {
        streamWriter.writeRejection(record, RejectionType.NOT_APPLICABLE, "activity is left");

      } else {
        final long workflowKey = workflowInstance.getWorkflowKey();
        final DeployedWorkflow workflow = workflowCache.getWorkflowByKey(workflowKey);
        if (workflow != null) {
          writeEvents(workflow);
        } else {
          fetchWorkflow(workflowKey, this::writeEvents, ctx);
        }
      }
    }

    private void writeEvents(final DeployedWorkflow workflow) {
      final DirectBuffer activityId =
          activityInstanceMap
              .wrapActivityInstanceKey(subscription.getActivityInstanceKey())
              .getActivityId();

      activityInstanceEvent
          .setWorkflowKey(workflow.getKey())
          .setBpmnProcessId(workflow.getWorkflow().getId())
          .setVersion(workflow.getVersion())
          .setWorkflowInstanceKey(subscription.getWorkflowInstanceKey())
          .setActivityId(activityId)
          .setPayload(subscription.getPayload());

      final TypedBatchWriter batchWriter = streamWriter.newBatch();
      batchWriter.addFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscription);
      batchWriter.addFollowUpEvent(
          subscription.getActivityInstanceKey(),
          WorkflowInstanceIntent.CATCH_EVENT_OCCURRING,
          activityInstanceEvent);
    }
  }

  private final class CatchEventOccurringProcessor
      extends FlowElementEventProcessor<ExecutableIntermediateMessageCatchEvent> {

    @Override
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableIntermediateMessageCatchEvent catchEvent) {

      final WorkflowInstanceRecord activityInstanceEvent = event.getValue();

      final DirectBuffer messagePayload = activityInstanceEvent.getPayload();
      final DirectBuffer activityInstancePayload =
          payloadCache.getPayload(activityInstanceEvent.getWorkflowInstanceKey());

      // currently, you can't define any output mapping - so the payload always merged
      final int payloadLength =
          payloadMappingProcessor.merge(messagePayload, activityInstancePayload);
      activityInstanceEvent.setPayload(payloadMappingProcessor.getResultBuffer(), 0, payloadLength);

      streamWriter.writeFollowUpEvent(
          event.getKey(), WorkflowInstanceIntent.CATCH_EVENT_OCCURRED, activityInstanceEvent);

      workflowInstanceIndex
          .get(activityInstanceEvent.getWorkflowInstanceKey())
          .setActivityInstanceKey(-1L)
          .write();

      activityInstanceMap.remove(event.getKey());
    }
  }

  private final class CancelWorkflowInstanceProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final WorkflowInstanceRecord activityInstanceEvent = new WorkflowInstanceRecord();
    private final JobRecord jobRecord = new JobRecord();

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final WorkflowInstance workflowInstance = workflowInstanceIndex.get(command.getKey());

      final boolean isCanceled = workflowInstance != null && workflowInstance.getTokenCount() > 0;

      if (isCanceled) {
        cancelWorkflowInstance(command, workflowInstance, streamWriter, responseWriter);
      } else {
        final RejectionType rejectionType = RejectionType.NOT_APPLICABLE;
        final String rejectionReason = "Workflow instance is not running";
        streamWriter.writeRejection(command, rejectionType, rejectionReason);
        responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
      }
    }

    private void cancelWorkflowInstance(
        TypedRecord<WorkflowInstanceRecord> command,
        WorkflowInstance workflowInstance,
        TypedStreamWriter writer,
        TypedResponseWriter responseWriter) {
      final TypedRecord<WorkflowInstanceRecord> workflowInstanceEvent =
          streamReader.readValue(workflowInstance.getPosition(), WorkflowInstanceRecord.class);

      workflowInstanceEvent.getValue().setPayload(EMPTY_PAYLOAD);

      final long activityInstanceKey = workflowInstance.getActivityInstanceKey();
      final long jobKey =
          activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey).getJobKey();

      activityInstanceMap.wrapActivityInstanceKey(activityInstanceKey);
      final WorkflowInstanceRecord value = workflowInstanceEvent.getValue();

      final TypedBatchWriter batchWriter = writer.newBatch();

      if (jobKey > 0) {
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

      if (activityInstanceKey > 0) {
        activityInstanceEvent.reset();
        activityInstanceEvent
            .setBpmnProcessId(value.getBpmnProcessId())
            .setVersion(value.getVersion())
            .setWorkflowInstanceKey(command.getKey())
            .setActivityId(activityInstanceMap.getActivityId());

        batchWriter.addFollowUpEvent(
            activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_TERMINATED, activityInstanceEvent);

        activityInstanceMap.remove(activityInstanceKey);
      }

      batchWriter.addFollowUpEvent(command.getKey(), WorkflowInstanceIntent.CANCELED, value);
      responseWriter.writeEventOnCommand(
          command.getKey(), WorkflowInstanceIntent.CANCELED, command);

      workflowInstanceIndex.remove(command.getKey());
      payloadCache.remove(command.getKey());
    }
  }

  private final class UpdatePayloadProcessor implements CommandProcessor<WorkflowInstanceRecord> {

    @Override
    public void onCommand(
        TypedRecord<WorkflowInstanceRecord> command, CommandControl commandControl) {
      final WorkflowInstanceRecord workflowInstanceEvent = command.getValue();

      final WorkflowInstance workflowInstance =
          workflowInstanceIndex.get(workflowInstanceEvent.getWorkflowInstanceKey());
      final boolean isActive = workflowInstance != null && workflowInstance.getTokenCount() > 0;

      if (isActive) {
        payloadCache.addPayload(
            workflowInstanceEvent.getWorkflowInstanceKey(),
            command.getPosition(),
            workflowInstanceEvent.getPayload());
        commandControl.accept(WorkflowInstanceIntent.PAYLOAD_UPDATED);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Workflow instance is not running");
      }
    }
  }

  public void fetchWorkflow(
      long workflowKey, Consumer<DeployedWorkflow> onFetched, EventLifecycleContext ctx) {
    final ActorFuture<ClientResponse> responseFuture =
        workflowCache.fetchWorkflowByKey(workflowKey);
    final ActorFuture<Void> onCompleted = new CompletableActorFuture<>();

    ctx.async(onCompleted);

    actor.runOnCompletion(
        responseFuture,
        (response, err) -> {
          if (err != null) {
            onCompleted.completeExceptionally(
                new RuntimeException("Could not fetch workflow", err));
          } else {
            try {
              final DeployedWorkflow workflow =
                  workflowCache.addWorkflow(response.getResponseBuffer());

              onFetched.accept(workflow);

              onCompleted.complete(null);
            } catch (Exception e) {
              onCompleted.completeExceptionally(
                  new RuntimeException("Error while processing fetched workflow", e));
            }
          }
        });
  }

  private abstract class FlowElementEventProcessor<T extends ExecutableFlowElement>
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final IncidentRecord incidentCommand = new IncidentRecord();

    private TypedRecord<WorkflowInstanceRecord> event;
    private TypedStreamWriter writer;
    protected Consumer<SideEffectProducer> sideEffect;
    protected EventLifecycleContext ctx;

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect,
        EventLifecycleContext ctx) {

      event = record;
      this.writer = streamWriter;
      this.sideEffect = sideEffect;
      this.ctx = ctx;
      final long workflowKey = event.getValue().getWorkflowKey();
      final DeployedWorkflow deployedWorkflow = workflowCache.getWorkflowByKey(workflowKey);

      if (deployedWorkflow == null) {
        fetchWorkflow(workflowKey, this::resolveCurrentFlowNode, ctx);
      } else {
        resolveCurrentFlowNode(deployedWorkflow);
      }
    }

    @SuppressWarnings("unchecked")
    private void resolveCurrentFlowNode(DeployedWorkflow deployedWorkflow) {
      final DirectBuffer currentActivityId = event.getValue().getActivityId();

      final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
      final ExecutableFlowElement flowElement = workflow.getElementById(currentActivityId);

      processFlowElementEvent(event, writer, (T) flowElement);
    }

    abstract void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        T currentFlowNode);

    protected void raiseIncident(
        TypedRecord<WorkflowInstanceRecord> record,
        ErrorType errorType,
        String errorMessage,
        TypedStreamWriter writer) {

      incidentCommand.reset();

      incidentCommand
          .initFromWorkflowInstanceFailure(record)
          .setErrorType(errorType)
          .setErrorMessage(errorMessage);

      if (!record.getMetadata().hasIncidentKey()) {
        writer.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
      } else {
        writer.writeFollowUpEvent(
            record.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentCommand);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private final class BpmnStepProcessor extends FlowElementEventProcessor<ExecutableFlowElement> {

    private final Map<BpmnStep, FlowElementEventProcessor> stepHandlers;

    private FlowElementEventProcessor delegate;

    private BpmnStepProcessor() {
      stepHandlers = new EnumMap<>(BpmnStep.class);

      stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowAspectHandler());
      stepHandlers.put(BpmnStep.CONSUME_TOKEN, new ConsumeTokenAspectHandler());
      stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitAspectHandler());
    }

    @Override
    @SuppressWarnings("unchecked")
    void processFlowElementEvent(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedStreamWriter streamWriter,
        ExecutableFlowElement currentFlowNode) {

      final BpmnStep step =
          currentFlowNode.getStep((WorkflowInstanceIntent) event.getMetadata().getIntent());

      delegate = stepHandlers.get(step);

      delegate.processFlowElementEvent(event, streamWriter, currentFlowNode);
    }
  }
}
