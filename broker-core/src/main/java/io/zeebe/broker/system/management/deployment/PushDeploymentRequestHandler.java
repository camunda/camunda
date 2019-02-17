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
package io.zeebe.broker.system.management.deployment;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.PushDeploymentRequestDecoder;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.sched.ActorControl;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class PushDeploymentRequestHandler implements Function<byte[], CompletableFuture<byte[]>> {

  private static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final Int2ObjectHashMap<Partition> leaderPartitions;
  private final ActorControl actor;

  public PushDeploymentRequestHandler(
      final Int2ObjectHashMap<Partition> leaderPartitions, final ActorControl actor) {
    this.leaderPartitions = leaderPartitions;
    this.actor = actor;
  }

  @Override
  public CompletableFuture<byte[]> apply(byte[] bytes) {
    final CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();

    actor.call(
        () -> {
          final DirectBuffer buffer = new UnsafeBuffer(bytes);
          final int offset = 0;
          final int length = buffer.capacity();

          messageHeaderDecoder.wrap(buffer, offset);
          final int schemaId = messageHeaderDecoder.schemaId();

          if (PushDeploymentRequestDecoder.SCHEMA_ID == schemaId) {
            final int templateId = messageHeaderDecoder.templateId();
            if (PushDeploymentRequestDecoder.TEMPLATE_ID == templateId) {
              handleValidRequest(responseFuture, buffer, offset, length);
            } else {
              final String errorMsg =
                  String.format(
                      "Expected to have template id %d, but got %d.",
                      PushDeploymentRequestDecoder.TEMPLATE_ID, templateId);
              responseFuture.completeExceptionally(new RuntimeException(errorMsg));
            }
          } else {
            final String errorMsg =
                String.format(
                    "Expected to have schema id %d, but got %d.",
                    PushDeploymentRequestDecoder.SCHEMA_ID, schemaId);
            responseFuture.completeExceptionally(new RuntimeException(errorMsg));
          }
        });
    return responseFuture;
  }

  private void handleValidRequest(
      CompletableFuture<byte[]> responseFuture, DirectBuffer buffer, int offset, int length) {
    final PushDeploymentRequest pushDeploymentRequest = new PushDeploymentRequest();
    pushDeploymentRequest.wrap(buffer, offset, length);
    final long deploymentKey = pushDeploymentRequest.deploymentKey();
    final int partitionId = pushDeploymentRequest.partitionId();
    final DirectBuffer deployment = pushDeploymentRequest.deployment();

    final Partition partition = leaderPartitions.get(partitionId);
    if (partition != null) {
      LOG.debug("Handling deployment {} for partition {} as leader", deploymentKey, partitionId);
      handlePushDeploymentRequest(responseFuture, deployment, deploymentKey, partitionId);
    } else {
      LOG.debug(
          "Rejecting deployment {} for partition {} as not leader", deploymentKey, partitionId);
      sendNotLeaderRejection(responseFuture);
    }
  }

  private void handlePushDeploymentRequest(
      final CompletableFuture<byte[]> responseFuture,
      final DirectBuffer deployment,
      final long deploymentKey,
      final int partitionId) {

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(deployment);

    actor.runUntilDone(
        () -> {
          final Partition partition = leaderPartitions.get(partitionId);
          if (partition == null) {
            LOG.debug("Leader change on partition {}, ignore push deployment request", partitionId);
            actor.done();
            return;
          }

          final boolean success =
              writeCreatingDeployment(partition, deploymentKey, deploymentRecord);
          if (success) {
            LOG.debug("Deployment CREATE command was written on partition {}", partitionId);
            actor.done();

            sendResponse(responseFuture, deploymentKey, partitionId);
          } else {
            actor.yield();
          }
        });
  }

  private void sendResponse(
      final CompletableFuture<byte[]> responseFuture,
      final long deploymentKey,
      final int partitionId) {

    final PushDeploymentResponse pushResponse = new PushDeploymentResponse();
    pushResponse.deploymentKey(deploymentKey);
    pushResponse.partitionId(partitionId);

    responseFuture.complete(pushResponse.toBytes());
  }

  private void sendNotLeaderRejection(final CompletableFuture<byte[]> responseFuture) {
    final NotLeaderResponse notLeaderResponse = new NotLeaderResponse();
    responseFuture.complete(notLeaderResponse.toBytes());
  }

  private boolean writeCreatingDeployment(
      final Partition partition, final long key, final UnpackedObject event) {
    final RecordType recordType = RecordType.COMMAND;
    final ValueType valueType = ValueType.DEPLOYMENT;
    final Intent intent = DeploymentIntent.CREATE;

    logStreamWriter.wrap(partition.getLogStream());

    recordMetadata.reset().recordType(recordType).valueType(valueType).intent(intent);

    final long position =
        logStreamWriter.key(key).metadataWriter(recordMetadata).valueWriter(event).tryWrite();

    return position > 0;
  }
}
