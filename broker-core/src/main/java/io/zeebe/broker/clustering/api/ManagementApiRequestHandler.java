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
package io.zeebe.broker.clustering.api;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.management.FetchSnapshotChunkRequestDecoder;
import io.zeebe.clustering.management.ListSnapshotsRequestDecoder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.ServerTransportBuilder;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class ManagementApiRequestHandler implements ServerRequestHandler, ServerMessageHandler {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final ActorControl actor;

  private final SnapshotReplicationRequestHandler snapshotReplicationRequestHandler;

  public ManagementApiRequestHandler(
      final ActorControl actor, final Map<Integer, Partition> trackedSnapshotPartitions) {
    this.actor = actor;
    this.snapshotReplicationRequestHandler =
        new SnapshotReplicationRequestHandler(
            LOG, trackedSnapshotPartitions, ServerTransportBuilder.DEFAULT_MAX_MESSAGE_LENGTH);
  }

  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (ListSnapshotsRequestDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();
      switch (templateId) {
        case ListSnapshotsRequestDecoder.TEMPLATE_ID:
          {
            sendResponseAsync(
                output,
                remoteAddress,
                requestId,
                snapshotReplicationRequestHandler.handleListSnapshotsAsync(buffer, offset, length));
            break;
          }
        case FetchSnapshotChunkRequestDecoder.TEMPLATE_ID:
          {
            // Instead provide a supplier which knows how to get the right response
            sendResponseAsync(
                output,
                remoteAddress,
                requestId,
                snapshotReplicationRequestHandler.handleFetchSnapshotChunkAsync(
                    buffer, offset, length));
            break;
          }
      }
    }

    return true;
  }

  private void sendResponseAsync(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId,
      final Supplier<BufferWriter> responseSupplier) {
    actor.runUntilDone(
        () -> {
          final BufferWriter responseWriter;

          try {
            responseWriter = responseSupplier.get();
          } catch (final Exception ex) {
            LOG.error("Error generating server response", ex);
            actor.done();
            return;
          }

          final ServerResponse response =
              new ServerResponse()
                  .reset()
                  .remoteAddress(remoteAddress)
                  .requestId(requestId)
                  .writer(responseWriter);

          if (output.sendResponse(response)) {
            actor.done();
          } else {
            actor.yield();
          }
        });
  }

  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    // no messages currently supported
    return true;
  }
}
