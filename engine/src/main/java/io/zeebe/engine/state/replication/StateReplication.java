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
package io.zeebe.engine.state.replication;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.Subscription;
import io.zeebe.engine.Loggers;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotReplication;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class StateReplication implements SnapshotReplication {

  public static final String REPLICATION_TOPIC_FORMAT = "replication-%d-%s";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final String replicationTopic;

  private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);
  private final ClusterEventService eventService;

  private ExecutorService executorService;
  private Subscription subscription;

  public StateReplication(ClusterEventService eventService, int partitionId, String name) {
    this.eventService = eventService;
    this.replicationTopic = String.format(REPLICATION_TOPIC_FORMAT, partitionId, name);
  }

  @Override
  public void replicate(SnapshotChunk snapshot) {
    eventService.broadcast(
        replicationTopic,
        snapshot,
        (s) -> {
          LOG.debug(
              "Replicate on topic {} snapshot chunk {} for snapshot pos {}.",
              replicationTopic,
              s.getChunkName(),
              s.getSnapshotPosition());

          final SnapshotChunkImpl chunkImpl = new SnapshotChunkImpl(s);
          return chunkImpl.toBytes();
        });
  }

  @Override
  public void consume(Consumer<SnapshotChunk> consumer) {
    executorService = Executors.newSingleThreadExecutor((r) -> new Thread(r, replicationTopic));

    subscription =
        eventService
            .subscribe(
                replicationTopic,
                (bytes -> {
                  readBuffer.wrap(bytes);
                  final SnapshotChunkImpl chunk = new SnapshotChunkImpl();
                  chunk.wrap(readBuffer, 0, bytes.length);
                  LOG.debug(
                      "Received on topic {} replicated snapshot chunk {} for snapshot pos {}.",
                      replicationTopic,
                      chunk.getChunkName(),
                      chunk.getSnapshotPosition());
                  return chunk;
                }),
                consumer,
                executorService)
            .join();
  }

  @Override
  public void close() {
    if (subscription != null) {
      subscription.close().join();
      subscription = null;
    }
    if (executorService != null) {
      executorService.shutdownNow();
      executorService = null;
    }
  }
}
