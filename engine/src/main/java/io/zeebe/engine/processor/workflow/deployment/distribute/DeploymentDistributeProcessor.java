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
package io.zeebe.engine.processor.workflow.deployment.distribute;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.DeploymentsState;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final LogStreamWriterImpl logStreamWriter;
  private final DeploymentsState deploymentsState;

  private ActorControl actor;
  private final DeploymentDistributor deploymentDistributor;
  private int streamProcessorId;

  public DeploymentDistributeProcessor(
      final DeploymentsState deploymentsState,
      final LogStreamWriterImpl logStreamWriter,
      final DeploymentDistributor deploymentDistributor) {
    this.deploymentsState = deploymentsState;
    this.logStreamWriter = logStreamWriter;
    this.deploymentDistributor = deploymentDistributor;
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    streamProcessorId = streamProcessor.getStreamProcessorContext().getId();
    actor = streamProcessor.getActor();
    actor.submit(this::reprocessPendingDeployments);
  }

  private void reprocessPendingDeployments() {
    deploymentsState.foreachPending(
        ((pendingDeploymentDistribution, key) -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          final DirectBuffer deployment = pendingDeploymentDistribution.getDeployment();
          buffer.putBytes(0, deployment, 0, deployment.capacity());

          distributeDeployment(key, pendingDeploymentDistribution.getSourcePosition(), buffer);
        }));
  }

  @Override
  public void processRecord(
      final long position,
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final DeploymentRecord deploymentEvent = event.getValue();
    final long key = event.getKey();

    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    deploymentEvent.write(buffer, 0);
    distributeDeployment(key, position, buffer);
  }

  private void distributeDeployment(
      final long key, final long position, final DirectBuffer buffer) {
    final ActorFuture<Void> pushDeployment =
        deploymentDistributor.pushDeployment(key, position, buffer);

    actor.runOnCompletion(
        pushDeployment, (aVoid, throwable) -> writeCreatingDeploymentCommand(key));
  }

  private void writeCreatingDeploymentCommand(final long deploymentKey) {
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentDistributor.removePendingDeployment(deploymentKey);
    final DirectBuffer buffer = pendingDeploymentDistribution.getDeployment();
    final long sourcePosition = pendingDeploymentDistribution.getSourcePosition();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(buffer);
    final RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata
        .intent(DeploymentIntent.DISTRIBUTED)
        .valueType(ValueType.DEPLOYMENT)
        .recordType(RecordType.EVENT);

    actor.runUntilDone(
        () -> {
          final long position =
              logStreamWriter
                  .key(deploymentKey)
                  .producerId(streamProcessorId)
                  .sourceRecordPosition(sourcePosition)
                  .valueWriter(deploymentRecord)
                  .metadataWriter(recordMetadata)
                  .tryWrite();
          if (position < 0) {
            actor.yield();
          } else {
            actor.done();
          }
        });
  }
}
