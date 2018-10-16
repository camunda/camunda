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
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public class PushDeploymentRequestHandler {

  private static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;

  private final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final Int2ObjectHashMap<Partition> leaderPartitions;
  private final ActorControl actor;

  public PushDeploymentRequestHandler(
      final Int2ObjectHashMap<Partition> leaderPartitions, final ActorControl actor) {
    this.leaderPartitions = leaderPartitions;
    this.actor = actor;
  }

  public boolean onPushDeploymentRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {

    final PushDeploymentRequest pushDeploymentRequest = new PushDeploymentRequest();
    pushDeploymentRequest.wrap(buffer, offset, length);
    final long deploymentKey = pushDeploymentRequest.deploymentKey();
    final int partitionId = pushDeploymentRequest.partitionId();
    final DirectBuffer deployment = pushDeploymentRequest.deployment();

    LOG.debug("Got deployment push request for deployment {}.", deploymentKey);

    final Partition partition = leaderPartitions.get(partitionId);
    if (partition != null) {
      LOG.trace("Leader for partition {}, handle deployment.", partitionId);
      handlePushDeploymentRequest(
          output, remoteAddress, requestId, deployment, deploymentKey, partitionId);

    } else {
      LOG.debug("Not leader for partition {}", partitionId);
      return false;
    }

    return true;
  }

  private void handlePushDeploymentRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId,
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

            sendResponse(output, remoteAddress, requestId, deploymentKey, partitionId);
          } else {
            actor.yield();
          }
        });
  }

  private void sendResponse(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId,
      final long deploymentKey,
      final int partitionId) {

    final PushDeploymentResponse pushResponse = new PushDeploymentResponse();
    pushResponse.deploymentKey(deploymentKey);
    pushResponse.partitionId(partitionId);

    final ServerResponse serverResponse =
        new ServerResponse().writer(pushResponse).requestId(requestId).remoteAddress(remoteAddress);

    actor.runUntilDone(
        () -> {
          if (output.sendResponse(serverResponse)) {
            actor.done();
            LOG.trace("Send response back to partition 1.");
          } else {
            actor.yield();
          }
        });
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
