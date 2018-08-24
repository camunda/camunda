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
import io.zeebe.broker.subscription.message.processor.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.broker.subscription.message.processor.PendingWorkflowInstanceSubscriptionChecker;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public class WorkflowInstanceStreamProcessor implements StreamProcessorLifecycleAware {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private Metric workflowInstanceEventCreate;

  private final ElementInstanceIndex scopeInstances = new ElementInstanceIndex();

  private TypedStreamReader streamReader;
  private WorkflowInstanceSubscriptionDataStore subscriptionStore =
      new WorkflowInstanceSubscriptionDataStore();

  private final TopologyManager topologyManager;
  private final WorkflowCache workflowCache;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private ActorControl actor;

  public WorkflowInstanceStreamProcessor(
      WorkflowCache workflowCache,
      SubscriptionCommandSender subscriptionCommandSender,
      TopologyManager topologyManager) {
    this.workflowCache = workflowCache;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.topologyManager = topologyManager;
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {

    final BpmnStepProcessor bpmnStepProcessor =
        new BpmnStepProcessor(
            scopeInstances, workflowCache, subscriptionCommandSender, subscriptionStore);

    final ComposeableSerializableSnapshot<ElementInstanceIndex> snapshotSupport =
        new ComposeableSerializableSnapshot<>(scopeInstances);

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
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_READY, bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            bpmnStepProcessor)
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.UPDATE_PAYLOAD,
            new UpdatePayloadProcessor())
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.END_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATING,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATED,
            bpmnStepProcessor)
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor())
        .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.OPEN,
            new OpenWorkflowInstanceSubscriptionProcessor(subscriptionStore))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription())
        .withListener(this)

        // this is pretty ugly, but goes away when we switch to rocksdb
        .withStateResource(snapshotSupport)
        .withStateResource(subscriptionStore)
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onOpen(TypedStreamProcessor streamProcessor) {
                scopeInstances.shareState(snapshotSupport.getObject());
              }
            })
        .build();
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {

    this.actor = streamProcessor.getActor();
    final LogStream logStream = streamProcessor.getEnvironment().getStream();

    final StreamProcessorContext context = streamProcessor.getStreamProcessorContext();
    final MetricsManager metricsManager = context.getActorScheduler().getMetricsManager();
    final String topicName =
        logStream.getTopicName().getStringWithoutLengthUtf8(0, logStream.getTopicName().capacity());
    final String partitionId = Integer.toString(logStream.getPartitionId());

    this.streamReader = streamProcessor.getEnvironment().buildStreamReader();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final PendingWorkflowInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingWorkflowInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionStore, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);

    workflowInstanceEventCreate =
        metricsManager
            .newMetric("workflow_instance_events_count")
            .type("counter")
            .label("topic", topicName)
            .label("partition", partitionId)
            .label("type", "created")
            .create();
  }

  @Override
  public void onClose() {
    workflowCache.close();
    workflowInstanceEventCreate.close();
    streamReader.close();
  }

  private final class CreateWorkflowInstanceEventProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    private long requestId;
    private int requestStreamId;

    private long workflowInstanceKey;

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
      final WorkflowInstanceRecord value = command.getValue();
      value.setActivityId(value.getBpmnProcessId());

      final TypedBatchWriter batchWriter = writer.newBatch();
      batchWriter.addFollowUpEvent(
          workflowInstanceKey, WorkflowInstanceIntent.CREATED, value, this::addRequestMetadata);
      batchWriter.addFollowUpEvent(
          workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, value);
    }

    private void rejectCommand(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedStreamWriter writer,
        TypedResponseWriter responseWriter,
        RejectionType rejectionType,
        String rejectionReason) {
      writer.writeRejection(command, rejectionType, rejectionReason, this::addRequestMetadata);
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

      scopeInstances.newInstance(
          record.getKey(), record.getValue(), WorkflowInstanceIntent.ELEMENT_READY);
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

  private final class JobCreatedProcessor implements TypedRecordProcessor<JobRecord> {

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final JobHeaders jobHeaders = record.getValue().headers();
      final long activityInstanceKey = jobHeaders.getActivityInstanceKey();
      if (activityInstanceKey > 0) {
        final ElementInstance activityInstance = scopeInstances.getInstance(activityInstanceKey);

        if (activityInstance != null) {
          activityInstance.setJobKey(record.getKey());
        }
      }
    }
  }

  private final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord> {

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final JobRecord jobEvent = record.getValue();
      final JobHeaders jobHeaders = jobEvent.headers();
      final long activityInstanceKey = jobHeaders.getActivityInstanceKey();
      final ElementInstance activityInstance = scopeInstances.getInstance(activityInstanceKey);

      if (activityInstance != null) {

        final WorkflowInstanceRecord value = activityInstance.getValue();
        value.setPayload(jobEvent.getPayload());

        streamWriter.writeFollowUpEvent(
            activityInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
        activityInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
        activityInstance.setJobKey(-1);
        activityInstance.setValue(value);
      }
    }
  }

  private final class CorrelateWorkflowInstanceSubscription
      implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

    private TypedRecord<WorkflowInstanceSubscriptionRecord> record;
    private WorkflowInstanceSubscriptionRecord subscription;
    private TypedStreamWriter streamWriter;
    private Consumer<SideEffectProducer> sideEffect;

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
      this.sideEffect = sideEffect;

      final ElementInstance eventInstance =
          scopeInstances.getInstance(subscription.getActivityInstanceKey());

      if (eventInstance == null) {
        streamWriter.writeRejection(
            record, RejectionType.NOT_APPLICABLE, "activity is not active anymore");

      } else {
        final long workflowKey = eventInstance.getValue().getWorkflowKey();
        final DeployedWorkflow workflow = workflowCache.getWorkflowByKey(workflowKey);
        if (workflow != null) {
          onWorkflowAvailable(workflow);
        } else {
          fetchWorkflow(workflowKey, this::onWorkflowAvailable, ctx);
        }
      }
    }

    private void onWorkflowAvailable(final DeployedWorkflow workflow) {
      // remove subscription if pending
      final boolean removed = subscriptionStore.removeSubscription(subscription);
      if (!removed) {
        streamWriter.writeRejection(
            record, RejectionType.NOT_APPLICABLE, "subscription is already correlated");

        sideEffect.accept(this::sendAcknowledgeCommand);
        return;
      }

      final ElementInstance eventInstance =
          scopeInstances.getInstance(subscription.getActivityInstanceKey());

      final WorkflowInstanceRecord value = eventInstance.getValue();
      value.setPayload(subscription.getPayload());

      final TypedBatchWriter batchWriter = streamWriter.newBatch();
      batchWriter.addFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscription);
      batchWriter.addFollowUpEvent(
          subscription.getActivityInstanceKey(), WorkflowInstanceIntent.ELEMENT_COMPLETING, value);

      sideEffect.accept(this::sendAcknowledgeCommand);

      eventInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
      eventInstance.setValue(value);
    }

    private boolean sendAcknowledgeCommand() {
      return subscriptionCommandSender.correlateMessageSubscription(
          subscription.getSubscriptionPartitionId(),
          subscription.getWorkflowInstanceKey(),
          subscription.getActivityInstanceKey(),
          subscription.getMessageName());
    }
  }

  private final class CancelWorkflowInstanceProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final ElementInstance workflowInstance = scopeInstances.getInstance(command.getKey());

      final boolean canCancel = workflowInstance != null && workflowInstance.canTerminate();

      if (canCancel) {
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
        ElementInstance workflowInstance,
        TypedStreamWriter writer,
        TypedResponseWriter responseWriter) {
      final WorkflowInstanceRecord workflowInstanceEvent = workflowInstance.getValue();

      workflowInstanceEvent.setPayload(EMPTY_PAYLOAD);

      final TypedBatchWriter batchWriter = writer.newBatch();

      batchWriter.addFollowUpEvent(
          command.getKey(), WorkflowInstanceIntent.CANCELING, workflowInstanceEvent);
      batchWriter.addFollowUpEvent(
          command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, workflowInstanceEvent);

      workflowInstance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATING);

      responseWriter.writeEventOnCommand(
          command.getKey(), WorkflowInstanceIntent.CANCELING, command);
    }
  }

  private final class UpdatePayloadProcessor implements CommandProcessor<WorkflowInstanceRecord> {

    @Override
    public void onCommand(
        TypedRecord<WorkflowInstanceRecord> command, CommandControl commandControl) {
      final WorkflowInstanceRecord commandValue = command.getValue();

      final ElementInstance workflowInstance =
          scopeInstances.getInstance(commandValue.getWorkflowInstanceKey());

      if (workflowInstance != null) {
        final WorkflowInstanceRecord workflowInstanceValue = workflowInstance.getValue();
        workflowInstanceValue.setPayload(commandValue.getPayload());
        workflowInstance.setValue(workflowInstance.getValue());
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
}
