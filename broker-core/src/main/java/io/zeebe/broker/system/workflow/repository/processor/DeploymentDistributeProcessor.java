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
import io.zeebe.broker.system.workflow.repository.processor.state.DeploymentsStateController;
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

public class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final TopologyManager topologyManager;
  private final LogStreamWriterImpl logStreamWriter;
  private final ClientTransport managementApi;
  private final DeploymentsStateController deploymentsStateController;

  private ActorControl actor;
  private TopologyPartitionListenerImpl partitionListener;
  private DeploymentDistributor deploymentDistributor;
  private int streamProcessorId;

  public DeploymentDistributeProcessor(
      TopologyManager topologyManager,
      DeploymentsStateController deploymentsStateController,
      ClientTransport managementClient,
      LogStreamWriterImpl logStreamWriter) {
    this.deploymentsStateController = deploymentsStateController;
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

    deploymentDistributor =
        new DeploymentDistributor(
            managementApi, partitionListener, deploymentsStateController, actor);

    actor.submit(this::reprocessPendingDeployments);
  }

  private void reprocessPendingDeployments() {
    deploymentsStateController.foreach(
        ((key, pendingDeploymentDistribution) -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          final DirectBuffer deployment = pendingDeploymentDistribution.getDeployment();
          buffer.putBytes(0, deployment, 0, deployment.capacity());

          distributeDeployment(key, pendingDeploymentDistribution.getSourcePosition(), buffer);
        }));
  }

  @Override
  public void processRecord(
      TypedRecord<DeploymentRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {

    responseWriter.writeEvent(event);

    final DeploymentRecord deploymentEvent = event.getValue();
    final long key = event.getKey();

    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    deploymentEvent.write(buffer, 0);
    distributeDeployment(key, event.getPosition(), buffer);
  }

  private void distributeDeployment(long key, long position, DirectBuffer buffer) {
    final ActorFuture<Void> pushDeployment =
        deploymentDistributor.pushDeployment(key, position, buffer);

    actor.runOnCompletion(
        pushDeployment, (aVoid, throwable) -> writeCreatingDeploymentCommand(key));
  }

  private void writeCreatingDeploymentCommand(long deploymentKey) {
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentDistributor.removePendingDeployment(deploymentKey);
    final DirectBuffer buffer = pendingDeploymentDistribution.getDeployment();
    final long sourcePosition = pendingDeploymentDistribution.getSourcePosition();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(buffer);
    final RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata
        .intent(DeploymentIntent.CREATING)
        .valueType(ValueType.DEPLOYMENT)
        .recordType(RecordType.COMMAND);

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
