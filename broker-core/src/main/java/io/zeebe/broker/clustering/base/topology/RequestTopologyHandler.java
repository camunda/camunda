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
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;

public class RequestTopologyHandler extends AbstractControlMessageHandler {
  protected final TopologyManager topologyManager;

  public RequestTopologyHandler(final ServerOutput output, final TopologyManager topologyManager) {
    super(output);
    this.topologyManager = topologyManager;
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.REQUEST_TOPOLOGY;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final RecordMetadata metadata) {
    final int requestStreamId = metadata.getRequestStreamId();
    final long requestId = metadata.getRequestId();

    actor.runOnCompletion(
        topologyManager.getTopologyDto(),
        ((topology, throwable) -> {
          if (throwable == null) {
            sendResponse(actor, requestStreamId, requestId, topology);
          } else {
            Loggers.CLUSTERING_LOGGER.debug(
                "Problem on requesting topology. Exception {}", throwable);
            sendErrorResponse(actor, requestStreamId, requestId, "Cannot request topology");
          }
        }));
  }
}
