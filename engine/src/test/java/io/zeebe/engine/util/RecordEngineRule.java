/*
 * Copyright © 2019  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.zeebe.engine.util;

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.exporter.api.record.value.deployment.ResourceType;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.EnumMap;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RecordEngineRule extends ExternalResource implements StreamProcessorLifecycleAware {

  private static final int PARTITION_ID = Protocol.DEPLOYMENT_PARTITION;

  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private static final RecordingExporter RECORDING_EXPORTER = new RecordingExporter();

  private BufferedLogStreamReader logStreamReader;

  private final StreamProcessorRule environmentRule = new StreamProcessorRule(PARTITION_ID);

  private SubscriptionCommandSender mockSubscriptionCommandSender;
  private DeploymentDistributor mockDeploymentDistributor;
  private EnumMap<ValueType, UnifiedRecordValue> eventCache;

  @Override
  public Statement apply(Statement base, Description description) {
    RECORDING_EXPORTER.maxWait = Duration.ofMillis(1000).toMillis();

    Statement statement = recordingExporterTestWatcher.apply(base, description);
    statement = super.apply(statement, description);
    return environmentRule.apply(statement, description);
  }

  @Override
  protected void before() {
    mockSubscriptionCommandSender = mock(SubscriptionCommandSender.class);

    when(mockSubscriptionCommandSender.openMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any(), anyBoolean()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.closeMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.rejectCorrelateMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any()))
        .thenReturn(true);

    mockDeploymentDistributor = mock(DeploymentDistributor.class);

    when(mockDeploymentDistributor.pushDeployment(anyLong(), anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final UnsafeBuffer deploymentBuffer = new UnsafeBuffer(new byte[deploymentRecord.getLength()]);
    deploymentRecord.write(deploymentBuffer, 0);

    final PendingDeploymentDistribution deploymentDistribution =
        mock(PendingDeploymentDistribution.class);
    when(deploymentDistribution.getDeployment()).thenReturn(deploymentBuffer);
    when(mockDeploymentDistributor.removePendingDeployment(anyLong()))
        .thenReturn(deploymentDistribution);

    environmentRule.startTypedStreamProcessor(
        (processingContext) ->
            EngineProcessors.createEngineProcessors(
                    processingContext, 1, mockSubscriptionCommandSender, mockDeploymentDistributor)
                .withListener(this));
  }

  public Record<DeploymentRecordValue> deploy(final BpmnModelInstance modelInstance) {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    environmentRule.writeCommand(DeploymentIntent.CREATE, deploymentRecord);

    return RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).getFirst();
  }

  public Record<WorkflowInstanceCreationRecordValue> createWorkflowInstance(
      Function<WorkflowInstanceCreationRecord, WorkflowInstanceCreationRecord> transformer) {
    final long position =
        environmentRule.writeCommand(
            WorkflowInstanceCreationIntent.CREATE,
            transformer.apply(new WorkflowInstanceCreationRecord()));

    return RecordingExporter.workflowInstanceCreationRecords()
        .withIntent(WorkflowInstanceCreationIntent.CREATED)
        .withSourceRecordPosition(position)
        .getFirst();
  }

  public Record<JobRecordValue> completeJobOfType(String type) {
    final Record<JobRecordValue> createdEvent =
        RecordingExporter.jobRecords().withIntent(JobIntent.CREATED).withType(type).getFirst();

    final JobRecord jobRecord = new JobRecord();
    environmentRule.writeCommand(createdEvent.getKey(), JobIntent.COMPLETE, jobRecord);

    return RecordingExporter.jobRecords().withType(type).withIntent(JobIntent.COMPLETED).getFirst();
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////// PREDICATES /////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void onOpen(ReadonlyProcessingContext context) {
    final ActorControl actor = context.getActor();

    eventCache = new EnumMap<>(ValueType.class);
    EVENT_REGISTRY.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));

    final ActorCondition onCommitCondition =
        actor.onCondition("on-commit", this::onNewEventCommitted);
    final LogStream logStream = context.getLogStream();
    logStream.registerOnCommitPositionUpdatedCondition(onCommitCondition);

    logStreamReader = new BufferedLogStreamReader(logStream);
  }

  private void onNewEventCommitted() {
    while (logStreamReader.hasNext()) {
      final LoggedEvent rawEvent = logStreamReader.next();
      final RecordMetadata rawMetadata = new RecordMetadata();
      rawEvent.readMetadata(rawMetadata);

      //      final byte[] bytes = new byte[rawEvent.getLength()];
      //      final UnsafeBuffer copiedBuffer = new UnsafeBuffer(bytes);
      //      rawEvent.write(copiedBuffer, 0);
      //
      //      final RecordMetadata recordMetadata = new RecordMetadata();
      //      recordMetadata.wrap(copiedBuffer, rawEvent.getMetadataOffset(),
      // rawEvent.getMetadataLength());
      //
      //      final UnifiedRecordValue recordValue =
      //          ReflectUtil.newInstance(EVENT_REGISTRY.get(rawMetadata.getValueType()));
      //      recordValue.wrap(copiedBuffer, rawEvent.getValueOffset(), rawEvent.getValueLength());
      //
      //      final CopiedTypedEvent copiedTypedEvent =
      //          new CopiedTypedEvent(
      //              recordValue,
      //              recordMetadata,
      //              rawEvent.getKey(),
      //              rawEvent.getPosition(),
      //              rawEvent.getSourceEventPosition());

      final TypedRecord<? extends UnifiedRecordValue> typedRecord =
          CopiedTypedEvent.toTypedEvent(rawEvent, EVENT_REGISTRY.get(rawMetadata.getValueType()));

      RECORDING_EXPORTER.export(typedRecord);
    }
  }
}
