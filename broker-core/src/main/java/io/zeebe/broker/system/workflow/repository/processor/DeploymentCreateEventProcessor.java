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
package io.zeebe.broker.system.workflow.repository.processor;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class DeploymentCreateEventProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final TopologyManager topologyManager;
  private final DeploymentTransformer deploymentTransformer;
  private final LogStreamWriterImpl logStreamWriter;
  private final ClientTransport managementApi;

  private ActorControl actor;
  private TopologyPartitionListenerImpl partitionListener;
  private DeploymentDistributor deploymentDistributor;
  private int streamProcessorId;

  public DeploymentCreateEventProcessor(
      TopologyManager topologyManager,
      WorkflowRepositoryIndex index,
      ClientTransport managementClient,
      LogStreamWriterImpl logStreamWriter) {
    deploymentTransformer = new DeploymentTransformer(index);
    this.topologyManager = topologyManager;
    managementApi = managementClient;
    this.logStreamWriter = logStreamWriter;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    streamProcessorId = streamProcessor.getStreamProcessorContext().getId();
    actor = streamProcessor.getActor();

    partitionListener = new TopologyPartitionListenerImpl(streamProcessor.getActor());
    topologyManager.addTopologyPartitionListener(partitionListener);

    deploymentDistributor = new DeploymentDistributor(managementApi, partitionListener, actor);
  }

  @Override
  public void processRecord(
      TypedRecord<DeploymentRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {
    final DeploymentRecord deploymentEvent = event.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      processValidDeployment(event, responseWriter, streamWriter, sideEffect, deploymentEvent);
    } else {
      streamWriter.writeRejection(
          event,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason(),
          m ->
              m.requestId(event.getMetadata().getRequestId())
                  .requestStreamId(event.getMetadata().getRequestStreamId()));
    }
  }

  private void processValidDeployment(
      TypedRecord<DeploymentRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      DeploymentRecord deploymentEvent) {
    final long key = streamWriter.getKeyGenerator().nextKey();

    sideEffect.accept(
        () -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          deploymentEvent.write(buffer, 0);
          final ActorFuture<Void> pushDeployment =
              deploymentDistributor.pushDeployment(key, event.getPosition(), buffer);

          actor.runOnCompletion(
              pushDeployment, (aVoid, throwable) -> writeDeploymentCreatedEvent(key));

          responseWriter.writeEventOnCommand(key, DeploymentIntent.CREATED, event);
          return responseWriter.flush();
        });
  }

  private void writeDeploymentCreatedEvent(long deploymentKey) {
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentDistributor.removePendingDeployment(deploymentKey);
    final DirectBuffer buffer = pendingDeploymentDistribution.getDeployment();
    final long sourcePosition = pendingDeploymentDistribution.getSourcePosition();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(buffer);
    final RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata
        .intent(DeploymentIntent.CREATED)
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
