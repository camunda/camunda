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
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class RequestTopologyHandler extends AbstractControlMessageHandler {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

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
      final long requestId,
      final int requestStreamId) {

    actor.runOnCompletion(
        topologyManager.getTopologyDto(),
        ((topology, throwable) -> {
          if (throwable == null) {
            sendResponse(actor, requestStreamId, requestId, topology);
          } else {
            LOG.error("Expected to fetch topology, but failed unexpectedly", throwable);
            sendErrorResponse(actor, requestStreamId, requestId, throwable.getMessage());
          }
        }));
  }
}
