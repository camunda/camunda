/*
 * Zeebe Workflow Engine
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

public class EngineRule extends ExternalResource implements StreamProcessorLifecycleAware {

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
  //////////////////////////////////// PROCESSOR LIFECYCLE ////////////////////////////////////////
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

      final CopiedTypedEvent typedRecord = createCopiedEvent(rawEvent);
      RECORDING_EXPORTER.export(typedRecord);
    }
  }

  private CopiedTypedEvent createCopiedEvent(LoggedEvent rawEvent) {
    // we have to access the underlying buffer and copy the metadata and value bytes
    // otherwise next event will overwrite the event before, since UnpackedObject
    // and RecordMetadata has properties (buffers, StringProperty etc.) which only wraps the given
    // buffer instead of copying it

    final DirectBuffer contentBuffer = rawEvent.getValueBuffer();

    final byte[] metadataBytes = new byte[rawEvent.getMetadataLength()];
    contentBuffer.getBytes(rawEvent.getMetadataOffset(), metadataBytes);
    final DirectBuffer metadataBuffer = new UnsafeBuffer(metadataBytes);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.wrap(metadataBuffer, 0, metadataBuffer.capacity());

    final byte[] valueBytes = new byte[rawEvent.getValueLength()];
    contentBuffer.getBytes(rawEvent.getValueOffset(), valueBytes);
    final DirectBuffer valueBuffer = new UnsafeBuffer(valueBytes);

    final UnifiedRecordValue recordValue =
        ReflectUtil.newInstance(EVENT_REGISTRY.get(metadata.getValueType()));
    recordValue.wrap(valueBuffer);

    return new CopiedTypedEvent(
        recordValue,
        metadata,
        rawEvent.getKey(),
        rawEvent.getPosition(),
        rawEvent.getSourceEventPosition());
  }
}
