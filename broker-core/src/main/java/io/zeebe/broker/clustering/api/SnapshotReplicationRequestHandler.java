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

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.clustering.management.ErrorResponseCode;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.spi.SnapshotMetadata;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Handles snapshot replication requests.
 *
 * <p>This class keeps state related to the current request being handled, and is therefore not
 * thread-safe.
 */
public class SnapshotReplicationRequestHandler {
  private final Logger logger;
  private final Map<Integer, Partition> trackedPartitions;
  private final byte[] chunkReadBuffer;

  private final ListSnapshotsRequest listSnapshotsRequest = new ListSnapshotsRequest();
  private final ListSnapshotsResponse listSnapshotsResponse = new ListSnapshotsResponse();

  private final FetchSnapshotChunkRequest fetchSnapshotChunkRequest =
      new FetchSnapshotChunkRequest();
  private final FetchSnapshotChunkResponse fetchSnapshotChunkResponse =
      new FetchSnapshotChunkResponse();

  private final ErrorResponse errorResponse = new ErrorResponse();
  public static final String PARTITION_NOT_FOUND_MESSAGE = "not currently tracking given partition";
  public static final String GET_SNAPSHOT_ERROR_MESSAGE = "could not open snapshot";
  public static final String INVALID_CHUNK_OFFSET_MESSAGE = "chunkOffset must be >= 0";
  public static final String INVALID_CHUNK_LENGTH_MESSAGE =
      "chunkLength must be between 1 and 512kb";
  public static final String SEEK_ERROR_MESSAGE = "could not seek to given chunkOffset";
  public static final String INVALID_READ_ERROR_MESSAGE =
      "could not read requested amount of bytes";
  public static final String READ_ERROR_MESSAGE = "unexpected read error occurred";
  public static final String NO_SNAPSHOT_ERROR_MESSAGE =
      "no snapshot found for given name and position";

  SnapshotReplicationRequestHandler(
      final Logger logger,
      final Map<Integer, Partition> trackedPartitions,
      final int chunkReadBufferSize) {
    this.logger = logger;
    this.trackedPartitions = trackedPartitions;
    this.chunkReadBuffer = new byte[chunkReadBufferSize];
  }

  Supplier<BufferWriter> handleListSnapshotsAsync(
      final DirectBuffer buffer, final int offset, final int length) {
    listSnapshotsRequest.wrap(buffer, offset, length);
    listSnapshotsResponse.reset();

    final int partitionId = listSnapshotsRequest.getPartitionId();
    final Partition partition = trackedPartitions.get(partitionId);

    if (partition == null) {
      return () -> prepareError(ErrorResponseCode.PARTITION_NOT_FOUND, PARTITION_NOT_FOUND_MESSAGE);
    }

    final SnapshotStorage storage = partition.getSnapshotStorage();
    return () -> handleListSnapshots(storage);
  }

  Supplier<BufferWriter> handleFetchSnapshotChunkAsync(
      final DirectBuffer buffer, final int offset, final int length) {
    fetchSnapshotChunkRequest.wrap(buffer, offset, length);

    final int partitionId = fetchSnapshotChunkRequest.getPartitionId();
    final Partition partition = trackedPartitions.get(partitionId);
    if (partition == null) {
      return () -> prepareError(ErrorResponseCode.PARTITION_NOT_FOUND, PARTITION_NOT_FOUND_MESSAGE);
    }

    final String name = BufferUtil.bufferAsString(fetchSnapshotChunkRequest.getName());
    final int chunkOffset = fetchSnapshotChunkRequest.getChunkOffset();
    final int maxChunkLength = fetchSnapshotChunkRequest.getChunkLength();
    final SnapshotStorage storage = partition.getSnapshotStorage();

    return () -> handleFetchSnapshotChunk(storage, name, chunkOffset, maxChunkLength);
  }

  private BufferWriter handleListSnapshots(final SnapshotStorage storage) {
    final List<SnapshotMetadata> snapshots = storage.listSnapshots();
    for (final SnapshotMetadata snapshot : snapshots) {
      // TODO: who should decide whether a snapshot is replicable or not? the handler? the storage?
      // the snapshot?
      if (snapshot.isReplicable()) {
        listSnapshotsResponse.addSnapshot(
            snapshot.getName(), snapshot.getPosition(), snapshot.getChecksum(), snapshot.getSize());
      }
    }

    return listSnapshotsResponse;
  }

  private BufferWriter handleFetchSnapshotChunk(
      final SnapshotStorage storage,
      final String name,
      final int chunkOffset,
      final int chunkLength) {
    final ReadableSnapshot snapshot;

    try {
      snapshot = storage.getLastSnapshot(name);
    } catch (final Exception ex) {
      logger.error(GET_SNAPSHOT_ERROR_MESSAGE, ex);
      return prepareError(ErrorResponseCode.READ_ERROR, GET_SNAPSHOT_ERROR_MESSAGE);
    }

    if (snapshot == null) {
      return prepareError(ErrorResponseCode.INVALID_PARAMETERS, NO_SNAPSHOT_ERROR_MESSAGE);
    }

    return readSnapshotChunk(snapshot, chunkOffset, chunkLength);
  }

  private BufferWriter readSnapshotChunk(
      final ReadableSnapshot snapshot, final int chunkOffset, final int maxChunkLength) {
    if (chunkOffset < 0) {
      return prepareError(ErrorResponseCode.INVALID_PARAMETERS, INVALID_CHUNK_OFFSET_MESSAGE);
    }

    final long snapshotLength = snapshot.getSize();
    final int chunkLength =
        (int)
            Math.min(
                Math.min(snapshotLength - chunkOffset, maxChunkLength), chunkReadBuffer.length);

    if (chunkLength < 1) {
      return prepareError(ErrorResponseCode.INVALID_PARAMETERS, INVALID_CHUNK_LENGTH_MESSAGE);
    }

    int bytesRead = 0;
    try (InputStream snapshotData = snapshot.getData()) {
      final int bytesSkipped = (int) snapshotData.skip(chunkOffset);
      if (bytesSkipped < chunkOffset) {
        return prepareError(ErrorResponseCode.READ_ERROR, SEEK_ERROR_MESSAGE);
      }

      bytesRead = snapshotData.read(chunkReadBuffer, 0, chunkLength);
      if (bytesRead < 1) {
        return prepareError(ErrorResponseCode.READ_ERROR, INVALID_READ_ERROR_MESSAGE);
      }
    } catch (final IOException ex) {
      logger.error(READ_ERROR_MESSAGE, ex);
      return prepareError(ErrorResponseCode.READ_ERROR, READ_ERROR_MESSAGE);
    }

    fetchSnapshotChunkResponse.setData(chunkReadBuffer, 0, bytesRead);
    return fetchSnapshotChunkResponse;
  }

  private ErrorResponse prepareError(final ErrorResponseCode code, final String message) {
    errorResponse.reset();
    return errorResponse.setCode(code).setData(message);
  }
}
