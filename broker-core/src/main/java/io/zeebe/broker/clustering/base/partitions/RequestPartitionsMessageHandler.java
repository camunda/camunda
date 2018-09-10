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
package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;

public class RequestPartitionsMessageHandler extends AbstractControlMessageHandler {

  private final PartitionsResponse response;

  public RequestPartitionsMessageHandler(
      final ServerOutput serverOutput, final ClusterCfg clusterCfg) {
    super(serverOutput);
    this.response = createResponse(clusterCfg);
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.REQUEST_PARTITIONS;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final RecordMetadata metadata) {

    final int requestStreamId = metadata.getRequestStreamId();
    final long requestId = metadata.getRequestId();
    sendResponse(actor, requestStreamId, requestId, response);
  }

  private PartitionsResponse createResponse(ClusterCfg clusterCfg) {
    final PartitionsResponse response = new PartitionsResponse();

    for (int i = 0; i < clusterCfg.getPartitionsCount(); i++) {
      response.addPartition(i);
    }

    return response;
  }
}
